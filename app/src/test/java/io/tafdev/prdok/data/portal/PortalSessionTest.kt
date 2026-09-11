package io.tafdev.prdok.data.portal

import io.tafdev.prdok.data.pairing.FakePairingStore
import io.tafdev.prdok.data.pairing.Pairing
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** An in-memory jar: [current] is what the WebViews hold, [stored] what the session wrote. */
private class FakeSessionCookieJar : SessionCookieJar {
    var current: String? = null
    val stored = mutableListOf<Pair<String, String>>()

    override suspend fun sessionId(url: String) = current

    override suspend fun store(url: String, setCookie: String) {
        stored += url to setCookie
    }
}

class PortalSessionTest {

    private lateinit var server: MockWebServer
    private lateinit var pages: PortalPages
    private lateinit var store: FakePairingStore
    private lateinit var jar: FakeSessionCookieJar
    private lateinit var session: PortalSession

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        pages = PortalPages(server.url("/").toString(), "https://forum.example")
        store = FakePairingStore(Pairing(klic = "k", id = "99", ids = "s3cret", provoz = "cp", skladnik = null))
        jar = FakeSessionCookieJar()
        session = PortalSession(OkHttpClient(), pages, store, jar)
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `without a cookie it visits the employee site and keeps only the issued session`() = runBlocking {
        server.enqueue(
            MockResponse()
                .addHeader("Set-Cookie", "theme=light; path=/")
                .addHeader("Set-Cookie", "PHPSESSID=fresh; path=/; HttpOnly")
        )

        session.authorize()

        val request = server.takeRequest()
        assertEquals("/nasi/zamestnanci.php?ids=s3cret&id=99&provoz=cp", request.path)
        assertNull(request.getHeader("Cookie"))
        assertEquals(listOf(pages.sessionScope to "PHPSESSID=fresh; path=/; HttpOnly"), jar.stored)
    }

    @Test
    fun `an existing session is sent along and authorized in place`() = runBlocking {
        jar.current = "held"
        server.enqueue(MockResponse())

        session.authorize()

        assertEquals("PHPSESSID=held", server.takeRequest().getHeader("Cookie"))
        assertTrue(jar.stored.isEmpty())
    }

    @Test
    fun `a server error fails`() {
        server.enqueue(MockResponse().setResponseCode(500))
        assertThrows(PortalSessionException::class.java) { runBlocking { session.authorize() } }
    }

    @Test
    fun `no session before or after fails`() {
        server.enqueue(MockResponse())
        assertThrows(PortalSessionException::class.java) { runBlocking { session.authorize() } }
    }

    @Test
    fun `an unpaired device fails without a request`() {
        store.state.value = null
        assertThrows(PortalSessionException::class.java) { runBlocking { session.authorize() } }
        assertEquals(0, server.requestCount)
    }
}
