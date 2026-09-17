package io.tafdev.prdok.ui.today

import android.content.res.Configuration
import android.text.format.DateFormat
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.tafdev.prdok.R
import io.tafdev.prdok.data.breaktimer.ActiveBreak
import io.tafdev.prdok.data.breaktimer.BreakTimer
import io.tafdev.prdok.ui.common.WithNotificationAccess
import io.tafdev.prdok.ui.theme.PrdokForAndroidTheme
import java.time.Duration
import java.time.Instant
import java.util.Date
import kotlinx.coroutines.delay

/** The row is seven times as wide as it is tall, whatever the screen width. */
private const val ROW_ASPECT_RATIO = 7f
private val TILE_GAP = 8.dp
private val TILE_SHAPE = RoundedCornerShape(15.dp)
private const val END_TIME_DELAY_MS = 1_500L

/**
 * A spring that settles in about half a second and overshoots a little. Compose describes a
 * spring by stiffness rather than duration; for a period of 0.5 s that is (2π / 0.5)² ≈ 158.
 */
private fun <T> tileSpring() = spring<T>(dampingRatio = 0.7f, stiffness = 158f)

/**
 * Stateful entry point for the break timers: collects the state, gates starting on the
 * notification permission, and clears the break when its time is up.
 */
@Composable
fun BreakTimerRow(viewModel: BreakTimerViewModel, modifier: Modifier = Modifier) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val active = uiState.active

    // Suspends until the end moment, then clears the break. Keyed on the end moment, so a
    // cancelled or replaced break takes its waiting coroutine with it.
    LaunchedEffect(active?.endsAt) {
        val endsAt = active?.endsAt ?: return@LaunchedEffect
        delay(Duration.between(Instant.now(), endsAt).toMillis().coerceAtLeast(0))
        viewModel.onTimeUp()
    }

    if (!uiState.loaded) {
        // Holds the row's height so the shifts below don't jump when it appears.
        Spacer(modifier.fillMaxWidth().aspectRatio(ROW_ASPECT_RATIO))
        return
    }

    WithNotificationAccess(onRefused = viewModel::onNotificationsRefused) { requestAccess ->
        BreakTimerContent(
            active = active,
            justStarted = uiState.justStarted,
            onStart = { timer -> requestAccess { viewModel.start(timer) } },
            onCancel = viewModel::cancel,
            onStartAnnounced = viewModel::onStartAnnounced,
            modifier = modifier,
        )
    }
}

/**
 * Two tiles side by side, 15 and 30 minutes. Starting one widens it across the row while the
 * other slides out towards its own edge and fades; cancelling plays it backwards.
 *
 * Every tile is always composed and positioned by hand: its width, horizontal offset and
 * opacity each animate towards a target worked out from [active]. That keeps the running tile
 * and the leaving tile moving at the same time, on the same spring.
 *
 * [justStarted] is true only right after a tap started [active]; see [RunningLabel].
 */
@Composable
fun BreakTimerContent(
    active: ActiveBreak?,
    onStart: (BreakTimer) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    justStarted: Boolean = false,
    onStartAnnounced: () -> Unit = {},
) {
    BoxWithConstraints(modifier.fillMaxWidth().aspectRatio(ROW_ASPECT_RATIO)) {
        val fullWidth = maxWidth
        val halfWidth = (maxWidth - TILE_GAP) / 2

        BreakTimer.entries.forEach { timer ->
            val isRunning = active?.timer == timer
            val isHidden = active != null && !isRunning
            val isLeading = timer == BreakTimer.SHORT
            val restingX = if (isLeading) 0.dp else halfWidth + TILE_GAP

            // Kept as State objects, not unwrapped with `by`: reading `.value` here in the composable
            // body would recompose the whole tile, text and all, on every animation frame. Read
            // inside the layout and graphicsLayer lambdas instead, a new value only re-runs those
            // lambdas: one re-measure and one redraw, no recomposition.
            val width = animateDpAsState(if (isRunning) fullWidth else halfWidth, tileSpring(), label = "width")
            val x = animateDpAsState(
                targetValue = when {
                    isRunning -> 0.dp
                    // Out past its own edge by its own width.
                    isHidden -> if (isLeading) restingX - halfWidth else restingX + halfWidth
                    else -> restingX
                },
                animationSpec = tileSpring(),
                label = "offset",
            )
            val alpha = animateFloatAsState(if (isHidden) 0f else 1f, tileSpring(), label = "alpha")

            TimerTile(
                timer = timer,
                running = active?.takeIf { isRunning },
                enabled = active == null,
                onStart = { onStart(timer) },
                onCancel = onCancel,
                justStarted = justStarted,
                onStartAnnounced = onStartAnnounced,
                modifier = Modifier
                    .graphicsLayer {
                        translationX = x.value.toPx()
                        // The spring overshoots a touch past 0 and 1; opacity can't.
                        this.alpha = alpha.value.coerceIn(0f, 1f)
                    }
                    .layout { measurable, constraints ->
                        val tileWidth = width.value.roundToPx()
                        val placeable = measurable.measure(Constraints.fixed(tileWidth, constraints.maxHeight))
                        layout(tileWidth, constraints.maxHeight) { placeable.place(0, 0) }
                    },
            )
        }
    }
}

@Composable
private fun TimerTile(
    timer: BreakTimer,
    running: ActiveBreak?,
    enabled: Boolean,
    onStart: () -> Unit,
    onCancel: () -> Unit,
    justStarted: Boolean,
    onStartAnnounced: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .clip(TILE_SHAPE)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            // indication = null: no ripple. The tile starts moving the moment it is tapped, and that
            // movement is the feedback; a ripple drawn inside it would only get dragged along.
            .clickable(interactionSource = null, indication = null, enabled = enabled, role = Role.Button, onClick = onStart),
    ) {
        // contentKey: only switching between idle and running cross-fades, not every new state.
        // using(null) turns off AnimatedContent's own size animation, which would otherwise
        // fight the width spring above.
        AnimatedContent(
            targetState = running,
            contentKey = { it != null },
            transitionSpec = { (fadeIn() togetherWith fadeOut()).using(null) },
            label = "tile",
        ) { state ->
            if (state == null) IdleLabel(timer) else RunningLabel(state, justStarted, onStartAnnounced, onCancel)
        }
    }
}

@Composable
private fun IdleLabel(timer: BreakTimer) {
    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Notifications, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.break_timer_length, timer.minutes),
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/**
 * The clock time the notification will arrive. Right after the tap that started the break, it
 * first says "notification set 15 min from now" with a check, and the time slides up in its place
 * a moment later. Coming back to the tab or reopening the app goes straight to the time.
 * The small cross cancels.
 *
 * `remember` alone can't tell those cases apart: it is forgotten whenever the label leaves the
 * screen, so every return would look like a fresh start. Whether the break was just started is
 * kept in the ViewModel, which outlives the tab, and [onStartAnnounced] clears it once the time
 * has been shown.
 */
@Composable
private fun RunningLabel(
    active: ActiveBreak,
    justStarted: Boolean,
    onStartAnnounced: () -> Unit,
    onCancel: () -> Unit,
) {
    var showEndTime by remember(active.endsAt) { mutableStateOf(!justStarted) }
    LaunchedEffect(active.endsAt) {
        if (showEndTime) return@LaunchedEffect
        delay(END_TIME_DELAY_MS)
        showEndTime = true
        onStartAnnounced()
    }
    val context = LocalContext.current
    // DateFormat.getTimeFormat follows the phone's 12/24-hour setting, which java.time alone doesn't know.
    val endTime = remember(active.endsAt, context) { DateFormat.getTimeFormat(context).format(Date.from(active.endsAt)) }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnimatedContent(
            targetState = showEndTime,
            transitionSpec = {
                // The old line leaves upwards while the new one comes up from below.
                (slideInVertically { height -> height } + fadeIn()) togetherWith
                    (slideOutVertically { height -> -height } + fadeOut())
            },
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            label = "message",
        ) { endTimeShown ->
            if (endTimeShown) {
                TileMessage(Icons.Outlined.Notifications, stringResource(R.string.break_timer_ends_at, endTime))
            } else {
                TileMessage(Icons.Default.Check, stringResource(R.string.break_timer_set, active.timer.minutes))
            }
        }
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(50))
                .clickable(interactionSource = null, indication = null, role = Role.Button, onClick = onCancel),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = stringResource(R.string.break_timer_cancel),
                modifier = Modifier
                    .size(14.dp)
                    .alpha(0.5f),
            )
        }
    }
}

@Composable
private fun TileMessage(icon: ImageVector, text: String) {
    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// --- Previews ---------------------------------------------------------------

@Composable
private fun PreviewBreakTimer(active: ActiveBreak?) {
    PrdokForAndroidTheme {
        Surface {
            BreakTimerContent(active = active, onStart = {}, onCancel = {}, modifier = Modifier.padding(16.dp))
        }
    }
}

@Preview(widthDp = 360, name = "Idle")
@Composable
private fun BreakTimerIdlePreview() = PreviewBreakTimer(active = null)

@Preview(widthDp = 360, name = "Running")
@Composable
private fun BreakTimerRunningPreview() =
    PreviewBreakTimer(ActiveBreak(BreakTimer.LONG, Instant.now().plusSeconds(30 * 60)))

@Preview(widthDp = 360, name = "Idle (dark)", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun BreakTimerIdleDarkPreview() = PreviewBreakTimer(active = null)
