package io.tafdev.prdok.data.pairing

import io.tafdev.prdok.data.api.PrdokApi
import io.tafdev.prdok.data.model.Credentials
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PairingManagerTest {

    private lateinit var server: MockWebServer
    private lateinit var store: FakePairingStore
    private lateinit var manager: PairingManager

    private val typed = Credentials(id = "42", ids = "typed-secret", provoz = "testprovoz")

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        store = FakePairingStore()
        manager = PairingManager(
            api = PrdokApi(server.url("/").toString().trimEnd('/')),
            store = store,
            pairingInitKey = "INIT-KEY",
        )
    }

    @After
    fun tearDown() = server.shutdown()

    private fun enqueue(json: String) = server.enqueue(MockResponse().setBody(json))

    private val initResponse =
        """{"ulozsi":{"klic":"7abc","lidauths":"id=1&ids=x"},"pak":["init"],"err":["Nove nainstalovana aplikace.","nerozpoznán zaměstnanec."]}"""

    @Test
    fun `happy path persists server-resolved credentials`() = runBlocking {
        enqueue(initResponse)
        enqueue("""{"ulozsi":{"zamid":"99","zamids":"server-secret","provoz":"testprovoz","lidauths":"id=7&ids=q"},"err":["propojeno"]}""")

        assertEquals(PairingResult.Success, manager.pair(typed))

        // note: id/ids come from the server, NOT from what was typed
        assertEquals(
            Pairing(klic = "7abc", id = "99", ids = "server-secret", provoz = "testprovoz", skladnik = "id=7&ids=q"),
            store.state.value,
        )
    }

    @Test
    fun `handshake sends the init key first and the issued key second`() = runBlocking {
        enqueue(initResponse)
        enqueue("""{"ulozsi":{"zamid":"99","zamids":"s"},"err":[]}""")
        manager.pair(typed)

        val step1 = server.takeRequest().body.readUtf8()
        assertTrue(step1.contains("klic=INIT-KEY"))
        assertTrue(step1.contains("akce=init"))
        assertTrue(step1.contains("provoz=testprovoz"))

        val step2 = java.net.URLDecoder.decode(server.takeRequest().body.readUtf8(), "UTF-8")
        assertTrue(step2.contains("klic=7abc"))
        assertTrue(step2.contains("akce=propojit_klicem"))
        assertTrue(step2.contains("parametr=zapp|7abc|testprovoz_zamestnanci|42|typed-secret|testprovoz"))
    }

    @Test
    fun `the employee table in parametr is derived from the facility`() = runBlocking {
        enqueue(initResponse)
        enqueue("""{"ulozsi":{"zamid":"99","zamids":"s"},"err":[]}""")
        manager.pair(Credentials(id = "7", ids = "tok", provoz = "jinyprovoz"))

        server.takeRequest() // step 1
        val step2 = java.net.URLDecoder.decode(server.takeRequest().body.readUtf8(), "UTF-8")
        assertTrue(step2.contains("parametr=zapp|7abc|jinyprovoz_zamestnanci|7|tok|jinyprovoz"))
    }

    @Test
    fun `provoz falls back to typed value and skladnik is null when server omits them`() = runBlocking {
        enqueue(initResponse)
        enqueue("""{"ulozsi":{"zamid":"99","zamids":"s","provoz":"","lidauths":""},"err":[]}""")
        manager.pair(typed)

        assertEquals("testprovoz", store.state.value?.provoz)
        assertNull(store.state.value?.skladnik)
    }

    @Test
    fun `wrong credentials come back as failure with the server message and no state`() = runBlocking {
        enqueue(initResponse)
        enqueue("""{"ulozsi":[],"err":["nenalezen zaměstnanec s tímto id a ids."]}""")

        val result = manager.pair(typed)
        assertEquals(PairingResult.Failure("nenalezen zaměstnanec s tímto id a ids."), result)
        assertNull(store.state.value)
    }

    @Test
    fun `init without a key is a failure and no state`() = runBlocking {
        enqueue("""{"ulozsi":[],"err":[]}""")
        val result = manager.pair(typed)
        assertTrue(result is PairingResult.Failure)
        assertNull(store.state.value)
        assertEquals(1, server.requestCount) // step 2 never attempted
    }

    @Test
    fun `network failure is a failure not a crash`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(503))
        assertTrue(manager.pair(typed) is PairingResult.Failure)
        assertNull(store.state.value)
    }

    @Test
    fun `unpair calls the server then clears the store`() = runBlocking {
        store.state.value = Pairing("7abc", "99", "s", "testprovoz", null)
        enqueue("""{"ulozsi":[],"err":["Aplikace odpárována."]}""")

        manager.unpair()

        val body = server.takeRequest().body.readUtf8()
        assertTrue(body.contains("akce=odparovat"))
        assertTrue(body.contains("klic=7abc"))
        assertNull(store.state.value)
    }

    @Test
    fun `unpair keeps local state when the server call fails`() {
        val existing = Pairing("7abc", "99", "s", "testprovoz", null)
        store.state.value = existing
        server.enqueue(MockResponse().setResponseCode(500))

        val thrown = runCatching { runBlocking { manager.unpair() } }.exceptionOrNull()
        assertTrue(thrown is io.tafdev.prdok.data.api.PrdokApiException)
        assertEquals(existing, store.state.value)
    }
}
