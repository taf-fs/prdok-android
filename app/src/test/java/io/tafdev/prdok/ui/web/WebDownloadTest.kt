package io.tafdev.prdok.ui.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WebDownloadTest {

    @Test
    fun `reads a quoted attachment name`() {
        assertEquals("vyplatnice.pdf", fileNameFromHeader("""attachment; filename="vyplatnice.pdf""""))
    }

    @Test
    fun `reads an unquoted name`() {
        assertEquals("vyplatnice.pdf", fileNameFromHeader("attachment; filename=vyplatnice.pdf"))
    }

    /** The case URLUtil's regex misses: the portal shows a PDF rather than offering it. */
    @Test
    fun `reads a name from an inline disposition`() {
        assertEquals("vyplatnice.pdf", fileNameFromHeader("""inline; filename="vyplatnice.pdf""""))
    }

    /** The other case it misses: anything following the name defeats its end anchor. */
    @Test
    fun `reads a name followed by further parameters`() {
        assertEquals(
            "vyplatnice.pdf",
            fileNameFromHeader("""attachment; filename="vyplatnice.pdf"; size=12345"""),
        )
    }

    @Test
    fun `prefers the encoded name and decodes it`() {
        assertEquals(
            "výplatnice.pdf",
            fileNameFromHeader("""attachment; filename="vyplatnice.pdf"; filename*=UTF-8''v%C3%BDplatnice.pdf"""),
        )
    }

    @Test
    fun `keeps a plus sign in an encoded name`() {
        assertEquals("a+b.pdf", fileNameFromHeader("attachment; filename*=UTF-8''a+b.pdf"))
    }

    /** A UTF-8 name sent without the encoded form arrives as Latin-1 mojibake. */
    @Test
    fun `repairs a mis-decoded plain name`() {
        assertEquals("výplatnice.pdf", fileNameFromHeader("""attachment; filename="vÃ½platnice.pdf""""))
    }

    @Test
    fun `leaves a correctly decoded plain name alone`() {
        assertEquals("výplatnice.pdf", fileNameFromHeader("""attachment; filename="výplatnice.pdf""""))
    }

    @Test
    fun `keeps only the last segment of a name that is a path`() {
        assertEquals("passwd", fileNameFromHeader("""attachment; filename="../../etc/passwd""""))
    }

    @Test
    fun `has no name to offer`() {
        assertNull(fileNameFromHeader("attachment"))
        assertNull(fileNameFromHeader("""attachment; size=12345"""))
        assertNull(fileNameFromHeader("""attachment; filename="""""))
    }
}
