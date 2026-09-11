package io.tafdev.prdok.ui.ebony

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EbonyScriptsTest {

    @Test
    fun `by default a regular facility gets no script at all`() {
        assertEquals("", EbonyScripts.afterPageLoad("cp"))
    }

    @Test
    fun `mopos links are hidden only when asked`() {
        val script = EbonyScripts.afterPageLoad("cp", hideMoposLinks = true)
        assertTrue(script.contains("""a.linka[href="mopos.php"]"""))
        assertFalse(script.contains("#ebonydiv"))
    }

    @Test
    fun `integrace gets the read-only stylesheet either way`() {
        val script = EbonyScripts.afterPageLoad(EbonyScripts.INTEGRATION_PROVOZ)
        assertFalse(script.contains("mopos.php"))
        assertTrue(script.contains("#ebonydiv > div:first-of-type { pointer-events: none !important; }"))
    }
}
