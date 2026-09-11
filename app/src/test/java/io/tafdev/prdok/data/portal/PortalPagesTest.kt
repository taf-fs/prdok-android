package io.tafdev.prdok.data.portal

import io.tafdev.prdok.data.pairing.Pairing
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PortalPagesTest {

    private val pages = PortalPages("https://portal.example/", "https://forum.example")
    private val pairing = Pairing(klic = "k", id = "99", ids = "s3cret", provoz = "cp", skladnik = "id=7&ids=abc")

    @Test
    fun `who is on shift takes the ISO date and relies on the session`() {
        assertEquals(
            PortalPage("https://portal.example/nasi/dnes.php?den=2026-09-05", needsSession = true),
            pages.whoIsOnShift(LocalDate.of(2026, 9, 5)),
        )
    }

    @Test
    fun `employee web carries the credentials and needs no session`() {
        assertEquals(
            PortalPage("https://portal.example/nasi/zamestnanci.php?ids=s3cret&id=99&provoz=cp", needsSession = false),
            pages.employeeWeb(pairing),
        )
    }

    @Test
    fun `ebony appends skladnik verbatim and is blank without one`() {
        assertEquals("https://portal.example/brana/ebony2.php?id=7&ids=abc", pages.ebony(pairing))
        assertEquals(PortalPages.BLANK, pages.ebony(pairing.copy(skladnik = null)))
    }

    @Test
    fun `contacts relies on the session, the forum lives on its own host`() {
        assertTrue(pages.contacts.needsSession)
        assertEquals(PortalPage("https://forum.example/", needsSession = false), pages.forum)
        assertEquals("https://portal.example/", pages.sessionScope)
    }
}
