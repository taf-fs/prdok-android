package io.tafdev.prdok.data.pairing

import io.tafdev.prdok.data.model.Credentials
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PairingInputTest {

    private val expected = Credentials(id = "42", ids = "s3cr3t", provoz = "testprovoz")

    // -- QR ------------------------------------------------------------------

    @Test
    fun `qr payload with six parts parses`() {
        assertEquals(expected, PairingInput.fromQr("zapp|klic|testprovoz_zamestnanci|42|s3cr3t|testprovoz"))
    }

    @Test
    fun `qr payload works for any facility, the table name is not hardcoded`() {
        assertEquals(
            Credentials(id = "7", ids = "tok", provoz = "jinyprovoz"),
            PairingInput.fromQr("zapp|klic|jinyprovoz_zamestnanci|7|tok|jinyprovoz"),
        )
    }

    @Test
    fun `qr payload with wrong part count is rejected`() {
        assertNull(PairingInput.fromQr("zapp|klic|testprovoz_zamestnanci|42|s3cr3t"))
        assertNull(PairingInput.fromQr("zapp|klic|testprovoz_zamestnanci|42|s3cr3t|testprovoz|extra"))
        assertNull(PairingInput.fromQr(""))
    }

    @Test
    fun `qr payload with an empty part is rejected`() {
        assertNull(PairingInput.fromQr("zapp|klic|testprovoz_zamestnanci||s3cr3t|testprovoz"))
    }

    // -- link ----------------------------------------------------------------

    @Test
    fun `link with all three params parses regardless of order or extra params`() {
        assertEquals(
            expected,
            PairingInput.fromLink("https://example.com/nasi/zamestnanci.php?provoz=testprovoz&foo=bar&ids=s3cr3t&id=42"),
        )
    }

    @Test
    fun `link values are url-decoded`() {
        assertEquals(
            Credentials("42", "a b+c", "testprovoz"),
            PairingInput.fromLink("https://x.y/p?id=42&ids=a%20b%2Bc&provoz=testprovoz"),
        )
    }

    @Test
    fun `link missing a param is rejected`() {
        assertNull(PairingInput.fromLink("https://example.com/p?id=42&ids=s3cr3t"))
        assertNull(PairingInput.fromLink("https://example.com/p?id=42&ids=&provoz=testprovoz"))
        assertNull(PairingInput.fromLink("https://example.com/p"))
        assertNull(PairingInput.fromLink("not a link at all"))
    }

    // -- typed ---------------------------------------------------------------

    @Test
    fun `typed values are trimmed`() {
        assertEquals(expected, PairingInput.fromTyped("  42 ", "s3cr3t\n", " testprovoz"))
    }

    @Test
    fun `typed values must all be non-empty`() {
        assertNull(PairingInput.fromTyped("42", "   ", "testprovoz"))
        assertNull(PairingInput.fromTyped("", "s3cr3t", "testprovoz"))
    }

    // -- auto-detect ---------------------------------------------------------

    @Test
    fun `parse detects qr then link`() {
        assertEquals(expected, PairingInput.parse("zapp|klic|testprovoz_zamestnanci|42|s3cr3t|testprovoz"))
        assertEquals(expected, PairingInput.parse("https://x.y/p?id=42&ids=s3cr3t&provoz=testprovoz"))
        assertNull(PairingInput.parse("garbage"))
    }
}
