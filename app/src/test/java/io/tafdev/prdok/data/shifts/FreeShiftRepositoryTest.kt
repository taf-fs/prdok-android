package io.tafdev.prdok.data.shifts

import io.tafdev.prdok.data.api.PrdokApi
import io.tafdev.prdok.data.api.PrdokApiException
import io.tafdev.prdok.data.model.ShiftRole
import io.tafdev.prdok.data.pairing.FakePairingStore
import io.tafdev.prdok.data.pairing.Pairing
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FreeShiftRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var store: FakePairingStore
    private lateinit var repository: FreeShiftRepository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        store = FakePairingStore(Pairing(klic = "7abc", id = "99", ids = "s", provoz = "cp", skladnik = null))
        repository = FreeShiftRepository(PrdokApi(server.url("/").toString().trimEnd('/')), store)
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `fetches with the stored pairing and sorts by start`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"ulozsi":[],"err":[],"smeny_handl":[
                   {"id":"2","kdy":"2026-09-20","od":"16:00:00","do":"23:00:00","typ":"v"},
                   {"id":"1","kdy":"2026-09-12","od":"08:00:00","do":"16:00:00","typ":"-"}]}"""
            )
        )

        val shifts = repository.freeShifts()

        val body = server.takeRequest().body.readUtf8()
        assertTrue(body.contains("akce=smeny_handl"))
        assertTrue(body.contains("klic=7abc"))
        assertEquals(listOf(1, 2), shifts.map { it.id })
        assertEquals(listOf(ShiftRole.REGULAR, ShiftRole.MANAGER), shifts.map { it.role })
    }

    @Test
    fun `unpaired device fails without a network call`() {
        store.state.value = null
        assertThrows(PrdokApiException::class.java) { runBlocking { repository.freeShifts() } }
        assertEquals(0, server.requestCount)
    }
}
