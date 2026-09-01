package io.tafdev.prdok.data.api

import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The envelope must survive the server's type-shifting fields:
 * `err` string-or-array, `ulozsi` object-or-empty-array.
 */
class ApiEnvelopeTest {

    @Test
    fun `err as array of strings`() {
        val env = ApiEnvelope.parse(
            """{"ulozsi":[],"pak":[],"err":["Nove nainstalovana aplikace.","druhá zpráva"],"msgbox":[],"informuj":[]}"""
        )
        assertEquals(listOf("Nove nainstalovana aplikace.", "druhá zpráva"), env.errMessages)
        assertEquals("Nove nainstalovana aplikace.\ndruhá zpráva", env.errText)
    }

    @Test
    fun `err as plain string`() {
        // pridatmoznost / smazatmoznost serialize err as a bare string
        val env = ApiEnvelope.parse(
            """{"ulozsi":[],"pak":[],"err":"ukládám možnost.","msgbox":[],"informuj":[]}"""
        )
        assertEquals(listOf("ukládám možnost."), env.errMessages)
    }

    @Test
    fun `err strings are trimmed before matching`() {
        val env = ApiEnvelope.parse("""{"err":"  mažu možnost. \n"}""")
        assertEquals(listOf("mažu možnost."), env.errMessages)
    }

    @Test
    fun `ulozsi as empty array means empty map`() {
        val env = ApiEnvelope.parse("""{"ulozsi":[],"err":[]}""")
        assertTrue(env.ulozsi.isEmpty())
    }

    @Test
    fun `ulozsi as object is exposed as string map`() {
        val env = ApiEnvelope.parse(
            """{"ulozsi":{"zamid":"42","zamids":"tajny","provoz":"testprovoz"},"err":[]}"""
        )
        assertEquals("42", env.ulozsi["zamid"])
        assertEquals("tajny", env.ulozsi["zamids"])
        assertEquals("testprovoz", env.ulozsi["provoz"])
    }

    @Test
    fun `unrecognized employee gate is detected`() {
        val env = ApiEnvelope.parse("""{"ulozsi":[],"err":"nerozpoznán zaměstnanec."}""")
        assertTrue(env.isUnrecognizedEmployee)
    }

    @Test
    fun `action-specific top-level fields are reachable`() {
        val env = ApiEnvelope.parse("""{"err":[],"otevrenodnu":26,"rokmesic":"2026-08"}""")
        assertEquals("26", (env["otevrenodnu"] as JsonPrimitive).content)
    }

    @Test
    fun `malformed json throws PrdokApiException`() {
        assertThrows(PrdokApiException::class.java) { ApiEnvelope.parse("<html>error</html>") }
    }
}
