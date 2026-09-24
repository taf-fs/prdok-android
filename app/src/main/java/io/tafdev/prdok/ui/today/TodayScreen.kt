package io.tafdev.prdok.ui.today

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.tafdev.prdok.R
import io.tafdev.prdok.data.model.PragueTime
import io.tafdev.prdok.data.model.Shift
import io.tafdev.prdok.data.model.ShiftKind
import io.tafdev.prdok.data.model.ShiftRole
import io.tafdev.prdok.data.shifts.Countdown
import io.tafdev.prdok.data.shifts.CountdownUnit
import io.tafdev.prdok.data.shifts.RemainingTime
import io.tafdev.prdok.data.shifts.StartsIn
import io.tafdev.prdok.data.shifts.TodayOverview
import io.tafdev.prdok.ui.calendar.ShiftDayTimelineRows
import io.tafdev.prdok.ui.calendar.ShiftTimelineAxis
import io.tafdev.prdok.ui.calendar.span
import io.tafdev.prdok.ui.calendar.timeRange
import io.tafdev.prdok.ui.calendar.timelineEntry
import io.tafdev.prdok.ui.theme.PrdokForAndroidTheme
import io.tafdev.prdok.ui.theme.backgroundSecondary
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val SHORT_DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.")

/** How much of the height below the status bar the header takes. */
private const val HEADER_SHARE = 0.55f
private val SECTION_GAP = 20.dp
private val TOP_BAR_ICON_SIZE = 28.dp

/**
 * Stateful entry point: the only part that knows a ViewModel exists. It collects the
 * state and hands it to [TodayContent], which is what previews and UI tests can drive.
 */
@Composable
fun TodayScreen(
    viewModel: TodayViewModel,
    breakTimerViewModel: BreakTimerViewModel,
    onOpenProfile: () -> Unit,
    onOpenSettings: () -> Unit,
    onWhoIsOnShift: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    TodayContent(
        uiState = uiState,
        onOpenProfile = onOpenProfile,
        onOpenSettings = onOpenSettings,
        onWhoIsOnShift = onWhoIsOnShift,
        onRetry = viewModel::refresh,
        modifier = modifier,
        breakTimer = { BreakTimerRow(breakTimerViewModel) },
    )
}

/**
 * Stateless rendering of [TodayUiState]: no ViewModel, no clock, no network.
 *
 * A header on the primary background taking the upper part of the screen (date bar, countdown,
 * who is on shift), the break timers under it, then the upcoming shifts filling what is left.
 *
 * [breakTimer] is a slot: the timers have their own ViewModel, so the screen only says where
 * they go, and previews can leave the slot empty or fill it with the stateless version.
 */
@Composable
fun TodayContent(
    uiState: TodayUiState,
    onOpenProfile: () -> Unit,
    onOpenSettings: () -> Unit,
    onWhoIsOnShift: (LocalDate) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    breakTimer: @Composable () -> Unit = {},
) {
    val overview = uiState.overview
    // The header's background runs up behind the status bar, but its share is taken of the height below it.
    val topInset = WindowInsets.safeDrawing.only(WindowInsetsSides.Top).asPaddingValues().calculateTopPadding()

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.backgroundSecondary),
    ) {
        val headerHeight = topInset + (maxHeight - topInset) * HEADER_SHARE
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(SECTION_GAP)) {
            Header(
                uiState = uiState,
                onOpenProfile = onOpenProfile,
                onOpenSettings = onOpenSettings,
                // With nothing loaded there is no shift to pick a day from, so it opens today.
                onWhoIsOnShift = { onWhoIsOnShift(overview?.whoIsOnShiftDate ?: uiState.now.toLocalDate()) },
                onRetry = onRetry,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(headerHeight),
            )

            Box(Modifier.padding(horizontal = 16.dp)) { breakTimer() }

            UpcomingShifts(
                upcoming = overview?.upcoming,
                onOpenDay = onWhoIsOnShift,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp),
            )
        }
    }
}

@Composable
private fun Header(
    uiState: TodayUiState,
    onOpenProfile: () -> Unit,
    onOpenSettings: () -> Unit,
    onWhoIsOnShift: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
            .padding(top = 8.dp),
    ) {
        // Less side padding than the rest: the icon buttons' own touch padding makes up the difference.
        TopDateBar(uiState.now, onOpenProfile, onOpenSettings, Modifier.padding(horizontal = 4.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when {
                uiState.isLoading -> Loading()
                uiState.errorMessage != null -> LoadError(uiState.errorMessage, onRetry)
                uiState.overview != null -> CountdownBlock(uiState.overview.countdown)
            }
            WhoIsOnShiftButton(onClick = onWhoIsOnShift)
        }
    }
}

@Composable
private fun TopDateBar(now: ZonedDateTime, onOpenProfile: () -> Unit, onOpenSettings: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onOpenProfile) {
            Icon(
                Icons.Default.AccountCircle,
                contentDescription = stringResource(R.string.today_profile),
                modifier = Modifier.size(TOP_BAR_ICON_SIZE),
            )
        }
        Text(
            text = now.format(DateTimeFormatter.ofPattern("d. MMMM", Locale.getDefault())),
            style = MaterialTheme.typography.labelMedium,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onOpenSettings) {
            Icon(
                Icons.Default.Settings,
                contentDescription = stringResource(R.string.today_settings),
                modifier = Modifier.size(TOP_BAR_ICON_SIZE),
            )
        }
    }
}

@Composable
private fun Loading() {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        CircularProgressIndicator()
        Text(stringResource(R.string.today_loading), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun LoadError(message: String, onRetry: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.error_load_failed, message),
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onRetry) { Text(stringResource(R.string.retry)) }
    }
}

@Composable
private fun CountdownBlock(countdown: Countdown) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        when (countdown) {
            is Countdown.Ongoing -> {
                CountdownTitle(stringResource(R.string.countdown_current_ends))
                CountdownValue(remainingText(countdown.remaining))
                ShiftDetails(countdown.shift)
            }
            is Countdown.Upcoming -> {
                // "Next shift is / today", but "Next shift is in / 3 days": the title carries the "in".
                when (val s = countdown.startsIn) {
                    StartsIn.Today, StartsIn.Tomorrow -> {
                        CountdownTitle(stringResource(R.string.countdown_next_soon))
                        CountdownValue(
                            stringResource(if (s == StartsIn.Today) R.string.countdown_today else R.string.countdown_tomorrow)
                        )
                    }
                    is StartsIn.Later -> {
                        CountdownTitle(stringResource(R.string.countdown_next_in))
                        CountdownValue(remainingText(s.remaining))
                    }
                }
                ShiftDetails(countdown.shift)
            }
            Countdown.None -> Text(
                text = stringResource(R.string.today_no_shifts),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CountdownTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold)
}

@Composable
private fun CountdownValue(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.displaySmall,
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun ShiftDetails(shift: Shift) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = shift.span().timeRange(),
            style = MaterialTheme.typography.labelMedium,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = shift.start.format(SHORT_DATE_FORMAT),
            style = MaterialTheme.typography.labelMedium,
            fontFamily = FontFamily.Monospace,
        )
    }
}

/** Ink on paper: the page's own background colour on a block of the primary foreground. */
@Composable
private fun WhoIsOnShiftButton(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.onBackground,
            contentColor = MaterialTheme.colorScheme.background,
        ),
        contentPadding = PaddingValues(horizontal = 40.dp, vertical = 8.dp),
    ) {
        Text(stringResource(R.string.today_who_is_on_shift), fontWeight = FontWeight.SemiBold)
    }
}

/**
 * The planned shifts ahead, in the same day timeline as the free shifts on the Calendar tab.
 * The axis stays put while the days scroll under it. [upcoming] is null until the shifts load;
 * the header shows the spinner or the error meanwhile.
 */
@Composable
private fun UpcomingShifts(upcoming: List<Shift>?, onOpenDay: (LocalDate) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.today_upcoming),
            style = MaterialTheme.typography.titleMedium,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        when {
            upcoming == null -> Unit
            upcoming.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.today_no_shifts),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            else -> {
                // map is an inline function, so its lambda may call the composable timelineEntry().
                val entries = upcoming.map { timelineEntry(it.start, it.end, it.role) }
                ShiftTimelineAxis()
                ShiftDayTimelineRows(
                    entries = entries,
                    onOpenDay = onOpenDay,
                    background = MaterialTheme.backgroundSecondary,
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun remainingText(remaining: RemainingTime): String {
    val count = remaining.value.toInt()
    val res = when (remaining.unit) {
        CountdownUnit.DAYS -> R.plurals.countdown_days
        CountdownUnit.HOURS -> R.plurals.countdown_hours
        CountdownUnit.MINUTES -> R.plurals.countdown_minutes
    }
    return pluralStringResource(res, count, count)
}

// --- Previews ---------------------------------------------------------------
// Previews render composables in the IDE without running the app. They can only be
// used on composables that take plain data, which is why TodayContent exists.

private object TodayPreviewData {
    /** A fixed "now" so previews are deterministic: Friday 2026-09-04, 18:30 Prague. */
    val now: ZonedDateTime = ZonedDateTime.of(2026, 9, 4, 18, 30, 0, 0, PragueTime.ZONE)

    private fun shift(id: Int, date: String, from: String, to: String, role: ShiftRole = ShiftRole.REGULAR): Shift {
        val start = ZonedDateTime.of(LocalDate.parse(date), LocalTime.parse(from), PragueTime.ZONE)
        var end = ZonedDateTime.of(LocalDate.parse(date), LocalTime.parse(to), PragueTime.ZONE)
        if (end.isBefore(start)) end = end.plusDays(1)
        return Shift(id, ShiftKind.PLANNED, start, end, role)
    }

    private val laterShifts = listOf(
        shift(2, "2026-09-06", "08:00", "16:00"),
        shift(3, "2026-09-11", "17:00", "01:00", ShiftRole.MANAGER),
        shift(4, "2026-09-12", "07:00", "11:00", ShiftRole.BARISTA),
        shift(5, "2026-09-12", "17:00", "23:00"),
        shift(6, "2026-09-15", "10:00", "18:00"),
    )

    /** A shift running right now, plus the future ones. */
    val ongoing = laterShifts + shift(1, "2026-09-04", "16:00", "23:00")

    /** Nothing running; the next shift is two days out. */
    val upcomingOnly = laterShifts

    /** Builds the state the same way the ViewModel does, so previews exercise the real logic. */
    fun state(shifts: List<Shift>) = TodayUiState(now = now, overview = TodayOverview.compute(shifts, now))
}

@Composable
private fun PreviewTodayContent(uiState: TodayUiState) {
    PrdokForAndroidTheme {
        Surface {
            TodayContent(
                uiState = uiState,
                onOpenProfile = {},
                onOpenSettings = {},
                onWhoIsOnShift = {},
                onRetry = {},
                breakTimer = { BreakTimerContent(active = null, onStart = {}, onCancel = {}) },
            )
        }
    }
}

@Preview(widthDp = 360, heightDp = 760, name = "Ongoing shift")
@Composable
private fun TodayOngoingPreview() = PreviewTodayContent(TodayPreviewData.state(TodayPreviewData.ongoing))

@Preview(widthDp = 360, heightDp = 760, name = "Upcoming shift")
@Composable
private fun TodayUpcomingPreview() = PreviewTodayContent(TodayPreviewData.state(TodayPreviewData.upcomingOnly))

@Preview(widthDp = 360, heightDp = 760, name = "No shifts")
@Composable
private fun TodayEmptyPreview() = PreviewTodayContent(TodayPreviewData.state(emptyList()))

@Preview(widthDp = 360, heightDp = 760, name = "Loading")
@Composable
private fun TodayLoadingPreview() =
    PreviewTodayContent(TodayUiState(now = TodayPreviewData.now, isLoading = true))

@Preview(widthDp = 360, heightDp = 760, name = "Error")
@Composable
private fun TodayErrorPreview() = PreviewTodayContent(
    TodayUiState(now = TodayPreviewData.now, errorMessage = "Network error: timeout")
)

@Preview(widthDp = 360, heightDp = 760, name = "Ongoing shift (dark)", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun TodayOngoingDarkPreview() = PreviewTodayContent(TodayPreviewData.state(TodayPreviewData.ongoing))
