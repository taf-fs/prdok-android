package io.tafdev.prdok.data.shifts

import io.tafdev.prdok.data.api.OfferOutcome
import io.tafdev.prdok.data.api.PrdokApi
import io.tafdev.prdok.data.api.PrdokApiException
import io.tafdev.prdok.data.api.RemoveOfferOutcome
import io.tafdev.prdok.data.pairing.FakePairingStore
import io.tafdev.prdok.data.pairing.Pairing
import java.io.File
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** A clock the test can move by hand. */
private class MutableClock(var now: Instant) : Clock() {
    override fun instant(): Instant = now
    override fun getZone() = ZoneOffset.UTC
    override fun withZone(zone: java.time.ZoneId) = this
    fun advance(duration: Duration) { now += duration }
}

class ShiftRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var server: MockWebServer
    private lateinit var store: FakePairingStore
    private lateinit var clock: MutableClock
    private lateinit var repository: ShiftRepository

    // "now" is 2026-09-08, so Aug/Sep/Oct 2026 are the refreshable window
    private val september = YearMonth.of(2026, 9)
    private val march = YearMonth.of(2026, 3)

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        store = FakePairingStore(Pairing(klic = "7abc", id = "99", ids = "s", provoz = "cp", skladnik = null))
        clock = MutableClock(Instant.parse("2026-09-08T10:00:00Z"))
        repository = newRepository()
    }

    private fun newRepository() = ShiftRepository(
        PrdokApi(server.url("/").toString().trimEnd('/')),
        store,
        File(tmp.root, "shifts"),
        clock,
    )

    @After
    fun tearDown() = server.shutdown()

    private fun month(vararg ids: Int) = MockResponse().setBody(
        """{"ulozsi":[],"err":[],"smeny":{"dochazka":[],"moznosti":[],"plan":[""" +
            ids.joinToString(",") { """{"id":"$it","kdy":"2026-09-0$it","od":"16:00:00","do":"23:00:00"}""" } +
            "]}}"
    )

    @Test
    fun `uses the stored pairing for credentials`() = runBlocking {
        server.enqueue(month(1))
        repository.shiftsForMonth(september)
        val body = server.takeRequest().body.readUtf8()
        assertTrue(body.contains("klic=7abc"))
        assertTrue(body.contains("provoz=cp"))
        assertTrue(body.contains("kdy=2026-09"))
    }

    @Test
    fun `second read within 24h is served from cache`() = runBlocking {
        server.enqueue(month(1))
        val first = repository.shiftsForMonth(september)
        clock.advance(Duration.ofHours(23))
        val second = repository.shiftsForMonth(september)

        assertEquals(first, second)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `cache survives a new repository instance`() = runBlocking {
        server.enqueue(month(1))
        repository.shiftsForMonth(september)

        val again = newRepository().shiftsForMonth(september)
        assertEquals(listOf(1), again.map { it.id })
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `refreshable month is refetched after 24h`() = runBlocking {
        server.enqueue(month(1))
        server.enqueue(month(1, 2))
        repository.shiftsForMonth(september)
        clock.advance(Duration.ofHours(25))

        val refreshed = repository.shiftsForMonth(september)
        assertEquals(listOf(1, 2), refreshed.map { it.id })
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `month outside the window is served forever`() = runBlocking {
        server.enqueue(month(1))
        repository.shiftsForMonth(march)
        clock.advance(Duration.ofDays(400))

        repository.shiftsForMonth(march)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `forceRefresh bypasses a fresh cache`() = runBlocking {
        server.enqueue(month(1))
        server.enqueue(month(1, 2))
        repository.shiftsForMonth(september)

        val refreshed = repository.shiftsForMonth(september, forceRefresh = true)
        assertEquals(listOf(1, 2), refreshed.map { it.id })
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `year preload only fetches months that are missing or stale`() = runBlocking {
        // Seed three months; the other nine will need the network.
        repeat(3) { server.enqueue(month()) }
        repository.shiftsForMonths(listOf(YearMonth.of(2026, 1), YearMonth.of(2026, 2), march))
        assertEquals(3, server.requestCount)

        repeat(9) { server.enqueue(month()) }
        repository.shiftsForYear(2026)
        assertEquals(12, server.requestCount)

        // A second preload straight away is all cache hits.
        repository.shiftsForYear(2026)
        assertEquals(12, server.requestCount)
    }

    @Test
    fun `clearCache forces a refetch`() = runBlocking {
        server.enqueue(month(1))
        server.enqueue(month(1))
        repository.shiftsForMonth(march)
        repository.clearCache()
        repository.shiftsForMonth(march)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `unpaired device fails without a network call`() {
        store.state.value = null
        assertThrows(PrdokApiException::class.java) {
            runBlocking { repository.shiftsForMonth(september) }
        }
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `offer and remove use the stored pairing and leave the cache alone`() = runBlocking {
        server.enqueue(month(1))
        repository.shiftsForMonth(september)
        server.takeRequest()

        server.enqueue(MockResponse().setBody("""{"ulozsi":[],"err":"ukládám možnost."}"""))
        assertEquals(OfferOutcome.Saved, repository.offerShift(LocalDate.of(2026, 9, 20), 16, 23))
        val offerBody = server.takeRequest().body.readUtf8()
        assertTrue(offerBody.contains("klic=7abc"))
        assertTrue(offerBody.contains("akce=pridatmoznost"))

        server.enqueue(MockResponse().setBody("""{"ulozsi":[],"err":"mažu možnost."}"""))
        assertEquals(RemoveOfferOutcome.Removed, repository.removeOffer(4242))
        assertTrue(server.takeRequest().body.readUtf8().contains("smenaid=4242"))

        // The cached month is untouched: the caller decides when to force-refresh.
        assertEquals(listOf(1), repository.shiftsForMonth(september).map { it.id })
        assertEquals(3, server.requestCount)
    }

    @Test
    fun `network failure is not cached`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500))
        assertThrows(PrdokApiException::class.java) {
            runBlocking { repository.shiftsForMonth(september) }
        }
        server.enqueue(month(1))
        assertEquals(listOf(1), repository.shiftsForMonth(september).map { it.id })
    }
}
