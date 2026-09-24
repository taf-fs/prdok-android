package io.tafdev.prdok.ui.ebony

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import io.tafdev.prdok.data.portal.EBONY_PROFILE
import io.tafdev.prdok.ui.common.DripLoadingAnimation
import io.tafdev.prdok.ui.web.WebPage
import io.tafdev.prdok.ui.web.WebPageState
import io.tafdev.prdok.ui.web.rememberWebPageState

/** The Ebony page is light-only; iOS paints the same yellow behind and around it. */
private val EbonyBackground = Color(0xFFFFFFB3)

/**
 * Creates Ebony's WebView in its own profile (see [EBONY_PROFILE]). Call it from a composable
 * that outlives the tab, so switching tabs keeps the page instead of loading it again.
 */
@Composable
fun rememberEbonyPageState(provoz: String): WebPageState = rememberWebPageState(
    profile = EBONY_PROFILE,
    // Transparent, so the yellow shows through while a page is still blank.
    configure = { setBackgroundColor(android.graphics.Color.TRANSPARENT) },
    onPageFinished = { view -> view.evaluateJavascript(EbonyScripts.afterPageLoad(provoz), null) },
)

/**
 * The Ebony tab: the gateway page edge to edge on its yellow, behind an opaque cover until
 * the first load finishes. [contentPadding] keeps the page clear of the system bars while
 * the yellow still runs under them.
 */
@Composable
fun EbonyScreen(
    state: WebPageState,
    url: String,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    // Loads on the first visit only; coming back to the tab finds the page as it was left.
    LaunchedEffect(url) {
        if (state.requestedUrl != url) state.load(url)
    }

    Box(
        modifier
            .fillMaxSize()
            .background(EbonyBackground)
            .padding(contentPadding)
    ) {
        WebPage(state, Modifier.fillMaxSize())
        AnimatedVisibility(
            visible = !state.hasLoaded,
            enter = EnterTransition.None,
            exit = fadeOut(tween(durationMillis = 300)),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(EbonyBackground),
                contentAlignment = Alignment.Center,
            ) {
                DripLoadingAnimation()
            }
        }
    }
}
