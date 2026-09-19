package io.tafdev.prdok.data.api

import io.tafdev.prdok.data.model.ShiftKind
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

/**
 * Exercises the client against a fake HTTP server, verifying both what we SEND
 * (form-encoded body with the mandatory params) and how responses are interpreted.
 */
class PrdokApiTest {

    private lateinit var server: MockWebServer
    private lateinit var api: PrdokApi

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        api = PrdokApi(baseUrl = server.url("/").toString().trimEnd('/'))
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun enqueue(json: String) {
        server.enqueue(MockResponse().setBody(json))
    }

    private val skeleton = """{"ulozsi":[],"pak":[],"err":[],"msgbox":[],"informuj":[]}"""

    // -- request format ------------------------------------------------------

    @Test
    fun `every request carries the mandatory params and hits hello php`() = runBlocking {
        enqueue(skeleton)
        api.call(klic = "test-key", provoz = "testprovoz", akce = "nic")

        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("/zapp/hello.php", recorded.path)
        assertTrue(recorded.getHeader("Content-Type")!!.startsWith("application/x-www-form-urlencoded"))
        val body = recorded.body.readUtf8()
        assertEquals("klic=test-key&akce=nic&parametr=&provoz=testprovoz", body)
    }

    @Test
    fun `mojesmeny sends the month as kdy`() = runBlocking {
        enqueue("""{"ulozsi":[],"err":[],"smeny":{"dochazka":[],"plan":[],"moznosti":[]}}""")
        api.fetchShifts("k", "testprovoz", YearMonth.of(2026, 8))
        val body = server.takeRequest().body.readUtf8()
        assertTrue(body.contains("akce=mojesmeny"))
        assertTrue(body.contains("kdy=2026-08"))
    }

    @Test
    fun `mojedata reads the profile object`() = runBlocking {
        enqueue(
            """{"ulozsi":[],"err":[],"mojedata":{"jmeno":"Jana N.","datum_nastup":"2024-08-30",
              "kompetence1":"202409","kompetence2":"0","kompetence3":"0","kompetence4":"0",
              "kompetence5":"0","kompetence6":"0","kompetence7":"0"}}"""
        )
        val profile = api.fetchProfile("k", "testprovoz")
        assertTrue(server.takeRequest().body.readUtf8().contains("akce=mojedata"))
        assertEquals("Jana N.", profile.name)
    }

    // -- response interpretation ---------------------------------------------

    @Test
    fun `fetchShifts parses a full response`() = runBlocking {
        enqueue(
            """{
              "ulozsi": [], "pak": [], "err": [], "msgbox": [], "informuj": [],
              "smeny": {
                "dochazka": [{"id":"73084","kdy":"2025-10-04","od":"16:01:00","do":"24:58:00"}],
                "plan":     [{"id":"80001","kdy":"2025-10-06","od":"16:00:00","do":"25:00:00"}],
                "moznosti": []
              }
            }"""
        )
        val shifts = api.fetchShifts("k", "testprovoz", YearMonth.of(2025, 10))
        assertEquals(2, shifts.size)
        assertEquals(ShiftKind.ACTUAL, shifts.first { it.id == 73084 }.kind)
        assertEquals(ShiftKind.PLANNED, shifts.first { it.id == 80001 }.kind)
    }

    @Test
    fun `offer saved is matched by exact Czech string`() = runBlocking {
        enqueue("""{"ulozsi":[],"err":"ukládám možnost."}""")
        val outcome = api.offerShift("k", "testprovoz", java.time.LocalDate.of(2026, 9, 1), 16, 25)
        assertEquals(OfferOutcome.Saved, outcome)

        val body = java.net.URLDecoder.decode(server.takeRequest().body.readUtf8(), "UTF-8")
        assertTrue(body.contains("kdy=2026-09-01"))
        assertTrue(body.contains("od=16:00:00"))
        assertTrue(body.contains("do=25:00:00"))
    }

    @Test
    fun `offer freeze rejection is detected by fragment`() = runBlocking {
        enqueue("""{"ulozsi":[],"err":"Odmítám zapsat. změny do data 2026-08-20 jsou již zablokované!"}""")
        val outcome = api.offerShift("k", "testprovoz", java.time.LocalDate.of(2026, 8, 10), 16, 24)
        assertTrue(outcome is OfferOutcome.Rejected)
    }

    @Test
    fun `offer hour validation happens before any network call`() {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { api.offerShift("k", "testprovoz", java.time.LocalDate.of(2026, 9, 1), 6, 24) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { api.offerShift("k", "testprovoz", java.time.LocalDate.of(2026, 9, 1), 16, 12) }
        }
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `remove offer outcomes`() = runBlocking {
        enqueue("""{"ulozsi":[],"err":"mažu možnost."}""")
        assertEquals(RemoveOfferOutcome.Removed, api.removeOffer("k", "testprovoz", 73084))

        enqueue("""{"ulozsi":[],"err":"nevidím možnost ke smazání."}""")
        assertEquals(RemoveOfferOutcome.NotFound, api.removeOffer("k", "testprovoz", 73084))
    }

    @Test
    fun `open days accepts number string and rejects false`(): Unit = runBlocking {
        enqueue("""{"err":[],"otevrenodnu":"26","rokmesic":"2026-08"}""")
        assertEquals(26, api.fetchOpenDays("k", "testprovoz", YearMonth.of(2026, 8)))

        enqueue("""{"err":["Chybí nebo neplatný parametr rokmesic (očekávám YYYY-MM)."],"otevrenodnu":false}""")
        val e = assertThrows(PrdokApiException::class.java) {
            runBlocking { api.fetchOpenDays("k", "testprovoz", YearMonth.of(2026, 8)) }
        }
        assertTrue(e.message!!.contains("rokmesic"))
    }

    // -- transport failures --------------------------------------------------

    @Test
    fun `unlinked device surfaces as an exception on data fetches`() {
        enqueue("""{"ulozsi":[],"err":"nerozpoznán zaměstnanec."}""")
        assertThrows(PrdokApiException::class.java) {
            runBlocking { api.fetchShifts("k", "testprovoz", YearMonth.of(2026, 8)) }
        }
    }

    @Test
    fun `http 500 throws`() {
        server.enqueue(MockResponse().setResponseCode(500))
        assertThrows(PrdokApiException::class.java) {
            runBlocking { api.call("k", "testprovoz", "nic") }
        }
    }

    @Test
    fun `die-zero empty body throws`() {
        enqueue("0")
        assertThrows(PrdokApiException::class.java) {
            runBlocking { api.call("k", "testprovoz", "nic") }
        }
    }
}
