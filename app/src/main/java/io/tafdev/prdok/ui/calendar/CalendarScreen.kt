package io.tafdev.prdok.ui.calendar

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kizitonwose.calendar.compose.CalendarState
import com.kizitonwose.calendar.compose.HorizontalCalendar
import com.kizitonwose.calendar.compose.rememberCalendarState
import com.kizitonwose.calendar.core.DayPosition
import com.kizitonwose.calendar.core.OutDateStyle
import com.kizitonwose.calendar.core.daysOfWeek
import com.kizitonwose.calendar.core.firstDayOfWeekFromLocale
import io.tafdev.prdok.R
import io.tafdev.prdok.data.export.ExportMode
import io.tafdev.prdok.data.shifts.DayDot
import io.tafdev.prdok.data.shifts.MonthStatistics
import io.tafdev.prdok.data.shifts.ShiftDays
import io.tafdev.prdok.ui.common.TabTitle
import io.tafdev.prdok.ui.theme.PrdokForAndroidTheme
import io.tafdev.prdok.ui.theme.backgroundSecondary
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** How far the grid can be paged in from today's month. */
private const val RANGE_MONTHS = 12L
private const val RANGE_MONTHS_BACK = 36L

/**
 * Stateful entry point: owns the snackbar, the haptics and which sheet is open,
 * and turns both ViewModels' one-shot events into those.
 */
@Composable
fun CalendarScreen(
    viewModel: CalendarViewModel,
    exportViewModel: ExportViewModel,
    onWhoIsOnShift: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val exportState by exportViewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    var showDaySheet by rememberSaveable { mutableStateOf(false) }
    var showMultiOffer by rememberSaveable { mutableStateOf(false) }
    var showExport by rememberSaveable { mutableStateOf(false) }

    // Both event streams end the same way: a haptic and a snackbar. Only which sheet
    // to close differs, so each collector decides that and hands the rest over.
    // A local *extension* on CoroutineScope: called from inside a LaunchedEffect, it
    // picks up that effect's scope as its receiver.
    fun CoroutineScope.notify(isError: Boolean, message: String) {
        haptics.performHapticFeedback(if (isError) HapticFeedbackType.Reject else HapticFeedbackType.Confirm)
        // showSnackbar suspends until the snackbar is gone, so it runs in its own
        // coroutine: a newer message replaces the old one instead of queueing behind it.
        snackbarHostState.currentSnackbarData?.dismiss()
        launch { snackbarHostState.showSnackbar(message, duration = SnackbarDuration.Short) }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                CalendarEvent.OfferSaved, CalendarEvent.OfferRemoved -> showDaySheet = false
                is CalendarEvent.MultiOfferFinished -> showMultiOffer = false
                else -> Unit
            }
            notify(event.isError, event.message(context))
        }
    }
    LaunchedEffect(Unit) {
        exportViewModel.events.collect { event ->
            showExport = false
            notify(event.isError, event.message(context))
        }
    }

    CalendarContent(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onMonthDisplayed = viewModel::showMonth,
        onSelectDate = { date ->
            viewModel.selectDate(date)
            showDaySheet = true
        },
        onRefresh = viewModel::refresh,
        onOfferShifts = { showMultiOffer = true },
        onExport = { showExport = true },
        onWhoIsOnShift = onWhoIsOnShift,
        modifier = modifier,
    )

    if (showDaySheet) {
        DaySheet(
            date = uiState.selectedDate,
            shifts = uiState.shiftsOn(uiState.selectedDate),
            isSubmitting = uiState.isSubmitting,
            onDismiss = { showDaySheet = false },
            onOffer = viewModel::offer,
            onRemoveOffer = viewModel::removeOffer,
            onShowWhoIsOnShift = { date ->
                showDaySheet = false
                onWhoIsOnShift(date)
            },
        )
    }
    if (showMultiOffer) {
        MultiOfferSheet(
            month = uiState.displayedMonth,
            offeredDays = uiState.offeredDaysInMonth(),
            progress = uiState.multiOffer,
            onSubmit = viewModel::offerMany,
            onDismiss = { showMultiOffer = false },
        )
    }
    if (showExport) {
        val month = uiState.displayedMonth
        ExportSheet(
            month = month,
            state = exportState,
            onPermissionResult = { granted -> exportViewModel.onPermissionResult(granted, month) },
            onSelectCalendar = { id -> exportViewModel.selectCalendar(id, month) },
            onExport = { title, alarm -> exportViewModel.export(month, title, alarm) },
            onDismiss = { showExport = false },
        )
    }
}

private fun CalendarEvent.message(context: Context): String = when (this) {
    CalendarEvent.Refreshed -> context.getString(R.string.calendar_refreshed)
    CalendarEvent.OfferSaved -> context.getString(R.string.offer_saved)
    CalendarEvent.OfferRemoved -> context.getString(R.string.offer_removed)
    CalendarEvent.OfferNotFound -> context.getString(R.string.offer_not_found)
    is CalendarEvent.LoadFailed -> context.getString(R.string.error_load_failed, detail)
    is CalendarEvent.RefreshFailed -> context.getString(R.string.calendar_refresh_failed, detail)
    is CalendarEvent.RequestFailed -> context.getString(R.string.error_request_failed, detail)
    is CalendarEvent.OfferRejected -> context.getString(R.string.offer_rejected, serverMessage)
    is CalendarEvent.UnexpectedResponse -> context.getString(R.string.error_unexpected_response, serverMessage)
    is CalendarEvent.MultiOfferFinished -> if (failed == 0) {
        context.resources.getQuantityString(R.plurals.multi_offer_success, saved, saved)
    } else {
        // Two lines: the count, then the last server message, which is the most useful one.
        listOfNotNull(
            context.resources.getQuantityString(R.plurals.multi_offer_failure, failed, failed),
            lastError,
        ).joinToString("\n")
    }
}

private fun ExportEvent.message(context: Context): String = when (this) {
    is ExportEvent.Finished -> when (mode) {
        ExportMode.ADD_ONLY -> context.resources.getQuantityString(R.plurals.export_added, summary.created, summary.created)
        ExportMode.SYNC -> context.getString(R.string.export_synced, summary.created, summary.updated, summary.deleted)
    }
    ExportEvent.AccessDenied -> context.getString(R.string.export_permission_denied)
    is ExportEvent.Failed -> context.getString(R.string.export_failed, detail)
}

/** Stateless apart from the grid's own scroll state; what previews render. */
@Composable
fun CalendarContent(
    uiState: CalendarUiState,
    snackbarHostState: SnackbarHostState,
    onMonthDisplayed: (YearMonth) -> Unit,
    onSelectDate: (LocalDate) -> Unit,
    onRefresh: () -> Unit,
    onOfferShifts: () -> Unit,
    onExport: () -> Unit,
    onWhoIsOnShift: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val todayMonth = YearMonth.from(uiState.today)
    val calendarState = rememberCalendarState(
        startMonth = todayMonth.minusMonths(RANGE_MONTHS_BACK),
        endMonth = todayMonth.plusMonths(RANGE_MONTHS),
        firstVisibleMonth = uiState.displayedMonth,
        firstDayOfWeek = firstDayOfWeekFromLocale(),
        // EndOfGrid pads every month to six rows, so the statistics below never jump.
        outDateStyle = OutDateStyle.EndOfGrid,
    )
    val scope = rememberCoroutineScope()

    // snapshotFlow turns Compose state reads into a Flow. We wait for the scroll to
    // settle before telling the ViewModel. equivalent to the iOS .onDeceleratingEnd
    LaunchedEffect(calendarState) {
        snapshotFlow { calendarState.firstVisibleMonth.yearMonth to calendarState.isScrollInProgress }
            .filter { (_, scrolling) -> !scrolling }
            .map { (month, _) -> month }
            .distinctUntilChanged()
            .collect { onMonthDisplayed(it) }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        // Shows past the end of short content, so the free shifts' background runs down to the bottom edge.
        containerColor = MaterialTheme.backgroundSecondary,
    ) { innerPadding ->
        val layoutDirection = LocalLayoutDirection.current
        val horizontalPadding = Modifier.padding(
            start = innerPadding.calculateStartPadding(layoutDirection) + 16.dp,
            end = innerPadding.calculateEndPadding(layoutDirection) + 16.dp,
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(top = innerPadding.calculateTopPadding())
                    .then(horizontalPadding)
                    .padding(top = 8.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TabTitle(stringResource(R.string.calendar_title))
                MonthHeader(
                    calendarState = calendarState,
                    isRefreshing = uiState.isRefreshing,
                    onScrollToMonth = { month -> scope.launch { calendarState.animateScrollToMonth(month) } },
                    onRefresh = onRefresh,
                )
                DaysOfWeekHeader(daysOfWeek(calendarState.firstDayOfWeek))
                HorizontalCalendar(
                    state = calendarState,
                    calendarScrollPaged = true,
                    userScrollEnabled = !uiState.isRefreshing,
                    dayContent = { day ->
                        if (day.position != DayPosition.MonthDate) {
                            Box(Modifier.aspectRatio(1f)) // out-of-month: blank and not tappable
                        } else {
                            DayCell(
                                date = day.date,
                                dot = uiState.shiftDays.dotFor(day.date),
                                highlight = when (day.date) {
                                    uiState.today -> 1f
                                    uiState.selectedDate -> 0.2f
                                    else -> 0f
                                },
                                enabled = !uiState.isRefreshing,
                                onClick = { onSelectDate(day.date) },
                            )
                        }
                    },
                )
                ActionRow(enabled = !uiState.isRefreshing, onOfferShifts = onOfferShifts, onExport = onExport)
                StatisticsBlock(
                    statistics = uiState.statistics,
                    bonus = uiState.bonus,
                    isLoading = uiState.isMonthLoading,
                )
            }
            // Free shifts sit on the secondary background, like the upcoming shifts on Today.
            FreeShiftsSection(
                load = uiState.freeShifts,
                onOpenDay = onWhoIsOnShift,
                modifier = horizontalPadding
                    .padding(top = 16.dp, bottom = innerPadding.calculateBottomPadding() + 16.dp),
            )
        }
    }
}

/** The two month-level actions between the grid and the statistics. */
@Composable
private fun ActionRow(enabled: Boolean, onOfferShifts: () -> Unit, onExport: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ActionButton(stringResource(R.string.calendar_offer_shifts), enabled, onOfferShifts, Modifier.weight(1f))
        ActionButton(stringResource(R.string.calendar_export), enabled, onExport, Modifier.weight(1f))
    }
}

/**
 * A slimmer Button than Material's default. `heightIn(min = ...)` rather than a fixed height:
 * a minimum from outside switches off the Button's own 40 dp minimum, yet still lets the button
 * grow when the user enlarges the system font.
 *
 * The shape is spelled out rather than left to Material, whose default pill sits too close to the
 * statistics card below it. 12 dp against the card's 16 dp: controls one step tighter than surfaces.
 */
@Composable
private fun ActionButton(text: String, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val style = MaterialTheme.typography.bodyLarge
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        modifier = modifier.heightIn(min = 36.dp),
    ) {
        Text(
            text = text,
            style = style,
            maxLines = 1,
            // Shrinks the label step by step until it fits on one line, down to half its size.
            autoSize = TextAutoSize.StepBased(minFontSize = style.fontSize / 2, maxFontSize = style.fontSize),
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun MonthHeader(
    calendarState: CalendarState,
    isRefreshing: Boolean,
    onScrollToMonth: (YearMonth) -> Unit,
    onRefresh: () -> Unit,
) {
    val month = calendarState.firstVisibleMonth.yearMonth
    val locale = Locale.getDefault()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = monthLabel(month, locale),
            style = MaterialTheme.typography.titleLarge,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        IconButton(
            onClick = { onScrollToMonth(month.minusMonths(1)) },
            enabled = !isRefreshing && month > calendarState.startMonth,
            modifier = Modifier.size(36.dp),
        ) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.calendar_previous_month))
        }
        IconButton(
            onClick = { onScrollToMonth(month.plusMonths(1)) },
            enabled = !isRefreshing && month < calendarState.endMonth,
            modifier = Modifier.size(36.dp),
        ) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.calendar_next_month))
        }
        IconButton(onClick = onRefresh, enabled = !isRefreshing, modifier = Modifier.size(36.dp)) {
            if (isRefreshing) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Default.Refresh, stringResource(R.string.calendar_refresh))
            }
        }
    }
}

/** "September 2026" / "Září 2026": `LLLL` is the stand-alone month name (nominative in Czech). */
internal fun monthLabel(month: YearMonth, locale: Locale): String =
    month.format(DateTimeFormatter.ofPattern("LLLL y", locale)).replaceFirstChar { it.titlecase(locale) }

/** "PO", "ÚT" / "MO", "TU": the first two letters of the short weekday name. */
internal fun DayOfWeek.shortLabel(locale: Locale): String =
    getDisplayName(TextStyle.SHORT, locale).take(2).uppercase(locale)

@Composable
internal fun DaysOfWeekHeader(daysOfWeek: List<DayOfWeek>) {
    val locale = Locale.getDefault()
    Row(Modifier.fillMaxWidth()) {
        daysOfWeek.forEach { day ->
            Text(
                text = day.shortLabel(locale),
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * One day of a month grid. Deliberately knows nothing about *why* it is highlighted:
 * the month calendar passes 1.0 for today and 0.2 for the selected day, the multi-offer
 * sheet passes 1.0 for every picked day. That is what lets both screens share it.
 *
 * @param highlight background opacity, 0..1; past 0.5 the number inverts to stay readable.
 */
@Composable
internal fun DayCell(
    date: LocalDate,
    dot: DayDot,
    highlight: Float,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val inverted = highlight >= 0.5f
    val contentColor = if (inverted) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .padding(2.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = highlight))
            .clickable(interactionSource = null, indication = null, enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(
                text = date.dayOfMonth.toString(),
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
                color = contentColor,
            )
            Box(
                Modifier
                    .size(5.dp)
                    .alpha(
                        when (dot) {
                            DayDot.SOLID -> 1f
                            DayDot.FAINT -> 0.3f
                            DayDot.NONE -> 0f
                        }
                    )
                    .background(contentColor, CircleShape),
            )
        }
    }
}

// --- Previews ---------------------------------------------------------------

private val previewState = CalendarUiState(
    today = LocalDate.of(2026, 9, 8),
    displayedMonth = YearMonth.of(2026, 9),
    selectedDate = LocalDate.of(2026, 9, 12),
    shiftDays = ShiftDays(
        planned = setOf(LocalDate.of(2026, 9, 12), LocalDate.of(2026, 9, 13)),
        offered = setOf(LocalDate.of(2026, 9, 19), LocalDate.of(2026, 9, 20)),
        actual = setOf(LocalDate.of(2026, 9, 5)),
    ),
    statistics = MonthStatistics.compute(emptyList(), YearMonth.of(2026, 9), 26),
    freeShifts = FreeShiftsLoad.Loaded(emptyList()),
)

@Preview(showBackground = true)
@Composable
private fun CalendarContentPreview() {
    PrdokForAndroidTheme {
        CalendarContent(
            uiState = previewState,
            snackbarHostState = remember { SnackbarHostState() },
            onMonthDisplayed = {},
            onSelectDate = {},
            onRefresh = {},
            onOfferShifts = {},
            onExport = {},
            onWhoIsOnShift = {},
        )
    }
}
