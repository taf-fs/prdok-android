package io.tafdev.prdok.data.portal

import io.tafdev.prdok.data.pairing.PairingStore
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class PortalSessionException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Where the in-app browser keeps the portal's session cookie. On a device that is WebView's
 * CookieManager; unit tests use a plain field. The same trick as `CalendarStore`.
 */
interface SessionCookieJar {
    /** The PHPSESSID value WebViews would send to [url], or null when there is none. */
    suspend fun sessionId(url: String): String?

    /** Stores one raw `Set-Cookie` header for [url], exactly as the server sent it. */
    suspend fun store(url: String, setCookie: String)
}

/**
 * Authorizes the portal session that pages without credentials (dnes.php, kontakty.php) depend on.
 *
 * iOS loads the credentialed employee site in an invisible browser on every launch, because
 * Safari's cookies can't be written any other way. Here one plain GET does the same job: the
 * server authorizes whichever session the request carries, and the cookie lands in the jar
 * that every portal WebView reads.
 *
 * It runs each time such a page opens, not once per launch. A cookie being there proves
 * nothing: every portal page hands out a fresh, unauthorized PHPSESSID to whoever lacks one,
 * and the server can expire an authorized one at any time.
 */
class PortalSession(
    private val client: OkHttpClient,
    private val pages: PortalPages,
    private val pairingStore: PairingStore,
    private val cookies: SessionCookieJar,
) {
    suspend fun authorize() {
        val pairing = pairingStore.pairing.first() ?: throw PortalSessionException("Device is not paired")
        // Authorize the session the WebViews already hold rather than starting a second one.
        val current = cookies.sessionId(pages.sessionScope)
        val request = Request.Builder()
            .url(pages.employeeWeb(pairing).url)
            .apply { if (current != null) header("Cookie", "$SESSION_COOKIE=$current") }
            .build()

        val issued = withContext(Dispatchers.IO) {
            try {
                // Only the headers matter; closing without reading skips the page body.
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw PortalSessionException("Server error (HTTP ${response.code})")
                    }
                    response.headers("Set-Cookie").filter { it.startsWith("$SESSION_COOKIE=") }
                }
            } catch (e: IOException) {
                throw PortalSessionException("Network error: ${e.message}", e)
            }
        }
        issued.forEach { cookies.store(pages.sessionScope, it) }
        if (issued.isEmpty() && current == null) {
            throw PortalSessionException("The portal did not start a session")
        }
    }

    companion object {
        const val SESSION_COOKIE = "PHPSESSID"
    }
}
