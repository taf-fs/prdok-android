package io.tafdev.prdok.data.portal

import android.webkit.CookieManager
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewFeature
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * The WebView profile Ebony runs in. A profile is a separate cookie jar (plus storage and
 * cache). Ebony needs one because its gateway page takes over whatever PHP session it is
 * handed, which would sign the portal pages out every time Ebony loads.
 */
const val EBONY_PROFILE = "ebony"

/**
 * [SessionCookieJar] over the default profile's CookieManager, shared by every portal WebView.
 * CookieManager calls back on the thread that asked, which has to have a Looper: the main one.
 */
class WebViewSessionCookieJar : SessionCookieJar {

    override suspend fun sessionId(url: String): String? = withContext(Dispatchers.Main) {
        CookieManager.getInstance().getCookie(url)
            ?.split(';')
            ?.map { it.trim() }
            ?.firstOrNull { it.startsWith("${PortalSession.SESSION_COOKIE}=") }
            ?.substringAfter('=')
    }

    override suspend fun store(url: String, setCookie: String) {
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { continuation ->
                CookieManager.getInstance().setCookie(url, setCookie) { continuation.resume(Unit) }
            }
        }
    }

    /** Forgets every web session, portal and Ebony alike. Called when the device is unpaired. */
    suspend fun clearAll() {
        withContext(Dispatchers.Main) {
            val managers = buildList {
                add(CookieManager.getInstance())
                if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
                    ProfileStore.getInstance().getProfile(EBONY_PROFILE)?.let { add(it.cookieManager) }
                }
            }
            managers.forEach { manager ->
                suspendCancellableCoroutine { continuation ->
                    manager.removeAllCookies { continuation.resume(Unit) }
                }
            }
        }
    }
}
