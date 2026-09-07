package io.tafdev.prdok.data.shifts

import io.tafdev.prdok.data.api.PrdokApi
import io.tafdev.prdok.data.api.PrdokApiException
import io.tafdev.prdok.data.pairing.FakePairingStore
import io.tafdev.prdok.data.pairing.Pairing
import java.time.YearMonth
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ShiftRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var store: FakePairingStore
    private lateinit var repository: ShiftRepository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        store = FakePairingStore(Pairing(klic = "7abc", id = "99", ids = "s", provoz = "cp", skladnik = null))
        repository = ShiftRepository(PrdokApi(server.url("/").toString().trimEnd('/')), store)
    }

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
        repository.shiftsForMonth(YearMonth.of(2026, 9))
        val body = server.takeRequest().body.readUtf8()
        assertTrue(body.contains("klic=7abc"))
        assertTrue(body.contains("provoz=cp"))
        assertTrue(body.contains("kdy=2026-09"))
    }

    @Test
    fun `multiple months are fetched and flattened`() = runBlocking {
        server.enqueue(month(1, 2))
        server.enqueue(month(3))
        val shifts = repository.shiftsForMonths(listOf(YearMonth.of(2026, 9), YearMonth.of(2026, 10)))
        assertEquals(setOf(1, 2, 3), shifts.map { it.id }.toSet())
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `unpaired device fails without a network call`() {
        store.state.value = null
        assertThrows(PrdokApiException::class.java) {
            runBlocking { repository.shiftsForMonth(YearMonth.of(2026, 9)) }
        }
        assertEquals(0, server.requestCount)
    }
}
