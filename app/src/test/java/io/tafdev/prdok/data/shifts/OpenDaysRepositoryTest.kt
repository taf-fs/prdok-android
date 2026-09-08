package io.tafdev.prdok.data.shifts

import io.tafdev.prdok.data.api.PrdokApi
import io.tafdev.prdok.data.api.PrdokApiException
import io.tafdev.prdok.data.pairing.FakePairingStore
import io.tafdev.prdok.data.pairing.Pairing
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class OpenDaysRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var server: MockWebServer
    private var now: Instant = Instant.parse("2026-09-08T10:00:00Z")
    private lateinit var repository: OpenDaysRepository

    private val clock = object : Clock() {
        override fun instant() = now
        override fun getZone() = ZoneOffset.UTC
        override fun withZone(zone: java.time.ZoneId) = this
    }

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        repository = OpenDaysRepository(
            PrdokApi(server.url("/").toString().trimEnd('/')),
            FakePairingStore(Pairing("7abc", "99", "s", "cp", null)),
            File(tmp.root, "opendays"),
            clock,
        )
    }

    @After
    fun tearDown() = server.shutdown()

    private fun count(value: String) =
        MockResponse().setBody("""{"ulozsi":[],"err":[],"otevrenodnu":$value,"rokmesic":"x"}""")

    @Test
    fun `count is fetched then cached`() = runBlocking {
        server.enqueue(count("26"))
        assertEquals(26, repository.openDays(YearMonth.of(2026, 9)))
        assertEquals(26, repository.openDays(YearMonth.of(2026, 9)))
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `a past month is final even long after`() = runBlocking {
        server.enqueue(count("\"25\""))
        assertEquals(25, repository.openDays(YearMonth.of(2026, 8)))
        now = Instant.parse("2027-09-08T10:00:00Z")
        assertEquals(25, repository.openDays(YearMonth.of(2026, 8)))
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `server false is an error and not cached`() {
        server.enqueue(count("false"))
        assertThrows(PrdokApiException::class.java) {
            runBlocking { repository.openDays(YearMonth.of(2026, 9)) }
        }
        server.enqueue(count("26"))
        assertEquals(26, runBlocking { repository.openDays(YearMonth.of(2026, 9)) })
    }
}
