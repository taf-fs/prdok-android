package io.tafdev.prdok.ui.web

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature

/**
 * One WebView plus the parts of its state Compose should react to.
 *
 * A WebView is a classic Android View with an identity of its own: history, scroll position,
 * running scripts. Compose can't rebuild it from state the way it redraws a Text, so the
 * instance lives here, held by whichever composable decides how long the page should survive.
 */
@Stable
class WebPageState internal constructor(
    val webView: WebView,
    private val showsPdfs: Boolean,
    private val afterPageLoad: (WebView) -> Unit,
) {
    /** The page's `<title>`, once it has one. */
    var title by mutableStateOf<String?>(null)
        private set
    var progress by mutableFloatStateOf(0f)
        private set
    var isLoading by mutableStateOf(false)
        private set
    var canGoBack by mutableStateOf(false)
        private set

    /** The page itself failed to load (offline, DNS...). A broken image or script doesn't count. */
    var loadFailed by mutableStateOf(false)
        private set

    /** Turns true when the first page finishes, and stays true. */
    var hasLoaded by mutableStateOf(false)
        private set

    /** The last URL passed to [load], so a caller can tell whether it is already showing. */
    var requestedUrl by mutableStateOf<String?>(null)
        private set

    /** The PDF the page opened, waiting to be shown. Always null unless [showsPdfs]. */
    var openPdf by mutableStateOf<PdfSource?>(null)
        private set

    init {
        // The portal fills its pages over AJAX, so nothing works without JavaScript. Lint warns
        // about it because of XSS in untrusted content; these are the employer's own pages.
        @SuppressLint("SetJavaScriptEnabled")
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        // Lay pages out the way mobile Safari does: honour a <meta name="viewport"> when the page
        // has one, otherwise use a desktop-width layout zoomed out to fit, and let the user pinch.
        // WebView's default is device width for everything, which cuts desktop-only pages off.
        webView.settings.useWideViewPort = true
        webView.settings.loadWithOverviewMode = true
        webView.settings.builtInZoomControls = true
        webView.settings.displayZoomControls = false
        webView.webViewClient = Client()
        webView.webChromeClient = ChromeClient()
        // Without a listener here a WebView silently drops every response it can't display,
        // which is what a download and an opened PDF both look like to it.
        webView.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            val pdf = if (showsPdfs) webView.pdfSource(url, contentDisposition, mimeType) else null
            if (pdf != null) openPdf = pdf
            else webView.startDownload(url, userAgent, contentDisposition, mimeType)
        }
    }

    fun load(url: String) {
        requestedUrl = url
        webView.loadUrl(url)
    }

    fun reload() {
        loadFailed = false
        webView.reload()
    }

    fun closePdf() {
        openPdf = null
    }

    /** Saves the open PDF, which until now has only been in the cache. */
    fun downloadOpenPdf() {
        openPdf?.let { webView.startDownload(it) }
    }

    /** Page lifecycle: start, finish, errors, and which links stay inside the WebView. */
    private inner class Client : WebViewClient() {
        override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
            isLoading = true
            loadFailed = false
        }

        override fun onPageFinished(view: WebView, url: String?) {
            isLoading = false
            hasLoaded = true
            afterPageLoad(view)
        }

        override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
            canGoBack = view.canGoBack()
        }

        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
            if (request.isForMainFrame) loadFailed = true
        }

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val uri = request.url
            if (uri.scheme == "http" || uri.scheme == "https") return false
            // tel:, mailto: and the like belong to other apps; a WebView only shows an error for them.
            try {
                view.context.startActivity(Intent(Intent.ACTION_VIEW, uri))
            } catch (_: ActivityNotFoundException) {
                // Nothing on the device handles it; staying on the page is the best we can do.
            }
            return true
        }
    }

    /** Browser-chrome events: progress and title. */
    private inner class ChromeClient : WebChromeClient() {
        override fun onProgressChanged(view: WebView, newProgress: Int) {
            progress = newProgress / 100f
        }

        override fun onReceivedTitle(view: WebView, title: String?) {
            this@WebPageState.title = title
        }
    }
}

/**
 * Creates a WebView that lives exactly as long as the calling composable and is destroyed with it.
 *
 * [profile] gives the page its own cookie jar and storage; on WebViews too old for profiles
 * (before ~2023) it quietly falls back to the shared default one.
 * [showsPdfs] offers a PDF up as [WebPageState.openPdf] instead of downloading it, for a
 * caller that shows a [PdfScreen]. [configure] runs once on the fresh WebView;
 * [onPageFinished] after every page load.
 */
@Composable
fun rememberWebPageState(
    profile: String? = null,
    showsPdfs: Boolean = false,
    configure: WebView.() -> Unit = {},
    onPageFinished: (WebView) -> Unit = {},
): WebPageState {
    val context = LocalContext.current
    val currentOnPageFinished by rememberUpdatedState(onPageFinished)
    val state = remember {
        val webView = WebView(context)
        // A profile has to be assigned before the WebView does anything else.
        if (profile != null && WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
            WebViewCompat.setProfile(webView, profile)
        }
        webView.configure()
        WebPageState(webView, showsPdfs) { view -> currentOnPageFinished(view) }
    }
    DisposableEffect(state) {
        onDispose { state.webView.destroy() }
    }
    return state
}

/**
 * Shows [state]'s WebView. Back steps through the page's own history while there is any,
 * then falls through to the next handler (closing the browser, leaving the app).
 */
@Composable
fun WebPage(state: WebPageState, modifier: Modifier = Modifier) {
    BackHandler(enabled = state.canGoBack) { state.webView.goBack() }
    AndroidView(
        // The WebView can outlive this composable (Ebony across tab switches), so it may still
        // hang in the container of its previous appearance. A View can only have one parent.
        factory = { state.webView.apply { (parent as? ViewGroup)?.removeView(this) } },
        modifier = modifier,
    )
}
