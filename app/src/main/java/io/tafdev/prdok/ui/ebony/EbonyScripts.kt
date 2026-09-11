package io.tafdev.prdok.ui.ebony

/** JavaScript run after every Ebony page load. Plain strings, so a unit test can check them. */
object EbonyScripts {
    /** The one facility that gets a stripped-down, read-only Ebony. */
    const val INTEGRATION_PROVOZ = "integrace"

    /** Off for now, so the mopos links stay visible. iOS hides them; flip to true to match again. */
    const val HIDE_MOPOS_LINKS_ENABLED = false

    fun afterPageLoad(provoz: String, hideMoposLinks: Boolean = HIDE_MOPOS_LINKS_ENABLED): String = buildString {
        if (hideMoposLinks) append(HIDE_MOPOS_LINKS)
        if (provoz == INTEGRATION_PROVOZ) append(INTEGRATION_STYLESHEET)
    }

    private const val HIDE_MOPOS_LINKS = """
        document.querySelectorAll('a.linka[href="mopos.php"]').forEach(function (el) {
            el.style.display = 'none';
        });
    """

    // #ebonydiv is filled over AJAX after the load and refilled on every reloadebony(), so
    // inline styles set now would run before the panels exist and be wiped afterwards.
    // A stylesheet keeps matching whatever lands in the div.
    private const val INTEGRATION_STYLESHEET = """
        (function () {
            if (document.getElementById('nativeIntegraceStyle')) return;
            var style = document.createElement('style');
            style.id = 'nativeIntegraceStyle';
            style.textContent =
                '#ebonydiv > div:first-of-type { pointer-events: none !important; }' +
                '#ebonydiv > div[style*="solid green"] { display: none !important; }';
            document.head.appendChild(style);
        })();
    """
}
