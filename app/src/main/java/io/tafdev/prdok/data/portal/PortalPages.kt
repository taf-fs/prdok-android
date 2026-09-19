package io.tafdev.prdok.data.portal

import io.tafdev.prdok.data.pairing.Pairing
import java.time.LocalDate
import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * A page of the employee portal, opened in the in-app browser.
 *
 * [needsSession]: the URL carries no credentials, so the page is authorized only by the
 * PHPSESSID cookie that [PortalSession.authorize] sets up. Without it the server answers
 * "neoprávněný přístup".
 */
data class PortalPage(val url: String, val needsSession: Boolean)

/** Every portal URL the app opens, built in one place from the configured base URLs. */
class PortalPages(apiBaseUrl: String, employeePortalUrl: String) {
    private val base = apiBaseUrl.trimEnd('/')

    /** The URL the session cookie belongs to; the server sets it with `path=/`. */
    val sessionScope = "$base/"

    /** The credentialed employee site. Visiting it is also what authorizes the session. */
    fun employeeWeb(pairing: Pairing) = PortalPage(
        url = "$base/nasi/zamestnanci.php".toHttpUrl().newBuilder()
            .addQueryParameter("ids", pairing.ids)
            .addQueryParameter("id", pairing.id)
            .addQueryParameter("provoz", pairing.provoz)
            .build()
            .toString(),
        needsSession = false,
    )

    /** "Who is on shift" on [date]. Credentials in this URL break the page, so it relies on the session. */
    fun whoIsOnShift(date: LocalDate) = PortalPage("$base/nasi/dnes.php?den=$date", needsSession = true)

    val contacts = PortalPage("$base/nasi/kontakty.php", needsSession = true)

    val meetingMinutes = PortalPage("$base/nasi/zapisyzporad.php", needsSession = true)

    fun files(pairing: Pairing) = PortalPage(
        url = "$base/nasi/soubory.php".toHttpUrl().newBuilder()
            .addQueryParameter("provoz", pairing.provoz)
            .addQueryParameter("id", pairing.id)
            .addQueryParameter("ids", pairing.ids)
            .build()
            .toString(),
        needsSession = true,
    )

    /** The forum lives on its own host and needs no session. */
    val forum = PortalPage(employeePortalUrl.trimEnd('/') + "/", needsSession = false)

    /** The Ebony gateway, with the stored `skladnik` query string appended verbatim; blank without one. */
    fun ebony(pairing: Pairing): String =
        pairing.skladnik?.let { "$base/brana/ebony2.php?$it" } ?: BLANK

    companion object {
        const val BLANK = "about:blank"
    }
}
