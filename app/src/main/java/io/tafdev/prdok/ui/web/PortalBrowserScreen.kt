package io.tafdev.prdok.ui.web

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.tafdev.prdok.R
import io.tafdev.prdok.data.portal.PortalPage
import io.tafdev.prdok.data.portal.PortalSessionException
import io.tafdev.prdok.ui.common.DripLoadingAnimation
import okhttp3.OkHttpClient

private sealed class BrowserLoad {
    data object Authorizing : BrowserLoad()
    data class Ready(val url: String) : BrowserLoad()
    data class Failed(val detail: String) : BrowserLoad()
}

/**
 * The in-app browser for portal pages: full screen over the tabs, with close, the page
 * title, a progress bar and reload. Chrome that Safari's sheet gives iOS for free.
 *
 * A page that needs the session waits for [authorize] first. That state is made right here
 * with produceState instead of in a ViewModel: it belongs to this one opening of the page,
 * starts when the browser appears and is cancelled when it closes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortalBrowserScreen(
    page: PortalPage,
    authorize: suspend () -> Unit,
    httpClient: OkHttpClient,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var attempt by remember { mutableIntStateOf(0) }
    val initial = if (page.needsSession) BrowserLoad.Authorizing else BrowserLoad.Ready(page.url)
    // Re-runs whenever page or attempt changes; "Retry" simply bumps attempt.
    val load by produceState(initialValue = initial, page, attempt) {
        if (!page.needsSession) return@produceState
        value = BrowserLoad.Authorizing
        value = try {
            authorize()
            BrowserLoad.Ready(page.url)
        } catch (e: PortalSessionException) {
            BrowserLoad.Failed(e.message.orEmpty())
        }
    }

    val web = rememberWebPageState(showsPdfs = true)
    LaunchedEffect(load) {
        (load as? BrowserLoad.Ready)?.let { web.load(it.url) }
    }

    // Composed before the WebPage, so the page's own "go back in history" handler wins while it is enabled.
    BackHandler(onBack = onClose)

    Scaffold(
        modifier = modifier,
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text(
                            text = web.title ?: Uri.parse(page.url).host.orEmpty(),
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onClose) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.browser_close))
                        }
                    },
                    actions = {
                        IconButton(onClick = web::reload, enabled = load is BrowserLoad.Ready) {
                            Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.browser_reload))
                        }
                    },
                )
                // Always takes up its height, so the page doesn't jump when loading ends.
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                ) {
                    // Authorizing has its own cover over the body, so no bar for it here.
                    if (web.isLoading) {
                        LinearProgressIndicator(progress = { web.progress }, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        },
    ) { innerPadding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            WebPage(web, Modifier.fillMaxSize())
            val current = load
            when {
                current is BrowserLoad.Authorizing -> AuthorizingCover()
                current is BrowserLoad.Failed -> ErrorCover(
                    message = stringResource(R.string.browser_session_failed, current.detail),
                    onRetry = { attempt++ },
                )
                web.loadFailed -> ErrorCover(
                    message = stringResource(R.string.browser_load_failed),
                    onRetry = web::reload,
                )
            }
        }
    }

    // Over the whole browser, chrome included, so it reads as the document and not a panel in it.
    web.openPdf?.let { pdf ->
        PdfScreen(
            source = pdf,
            httpClient = httpClient,
            onDownload = web::downloadOpenPdf,
            onClose = web::closePdf,
            modifier = modifier,
        )
    }
}

/** Covers the blank WebView while the portal session is being authorized. */
@Composable
private fun AuthorizingCover() {
    Surface(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            DripLoadingAnimation()
            Text(
                text = stringResource(R.string.browser_authorizing),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Covers the WebView's own error page, which is technical and in English. */
@Composable
private fun ErrorCover(message: String, onRetry: () -> Unit) {
    Surface(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(message, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            Button(onClick = onRetry) { Text(stringResource(R.string.retry)) }
        }
    }
}
