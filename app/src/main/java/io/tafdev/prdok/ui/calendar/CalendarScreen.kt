package io.tafdev.prdok.ui.calendar

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import io.tafdev.prdok.data.shifts.DayDot
import io.tafdev.prdok.data.shifts.MonthStatistics
import io.tafdev.prdok.data.shifts.ShiftDays
import io.tafdev.prdok.ui.theme.PrdokForAndroidTheme
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** How far the grid can be paged in from today's month. */
private const val RANGE_MONTHS = 12L
private const val RANGE_MONTHS_BACK = 36L

/**
 * Stateful entry point: owns the snackbar, the haptics and the day-sheet visibility,
 * and turns the ViewModel's one-shot events into those.
 */
@Composable
fun CalendarScreen(
    viewModel: CalendarViewModel,
    onWhoIsOnShift: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    var showDaySheet by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            if (event is CalendarEvent.OfferSaved || event is CalendarEvent.OfferRemoved) showDaySheet = false
            haptics.performHapticFeedback(
                if (event.isError) HapticFeedbackType.Reject else HapticFeedbackType.Confirm
            )
            // showSnackbar suspends until the snackbar is gone, so it runs in its own
            // coroutine: a newer message replaces the old one instead of queueing behind it.
            snackbarHostState.currentSnackbarData?.dismiss()
            launch { snackbarHostState.showSnackbar(event.message(context), duration = SnackbarDuration.Short) }
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
}

/** Stateless apart from the grid's own scroll state; what previews render. */
@Composable
fun CalendarContent(
    uiState: CalendarUiState,
    snackbarHostState: SnackbarHostState,
    onMonthDisplayed: (YearMonth) -> Unit,
    onSelectDate: (LocalDate) -> Unit,
    onRefresh: () -> Unit,
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
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.calendar_title),
                style = MaterialTheme.typography.headlineMedium,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
            )
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
                            isToday = day.date == uiState.today,
                            isSelected = day.date == uiState.selectedDate,
                            dot = uiState.shiftDays.dotFor(day.date),
                            enabled = !uiState.isRefreshing,
                            onClick = { onSelectDate(day.date) },
                        )
                    }
                },
            )
            StatisticsBlock(statistics = uiState.statistics, isLoading = uiState.isMonthLoading)
        }
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
private fun monthLabel(month: YearMonth, locale: Locale): String =
    month.format(DateTimeFormatter.ofPattern("LLLL y", locale)).replaceFirstChar { it.titlecase(locale) }

@Composable
private fun DaysOfWeekHeader(daysOfWeek: List<DayOfWeek>) {
    val locale = Locale.getDefault()
    Row(Modifier.fillMaxWidth()) {
        daysOfWeek.forEach { day ->
            Text(
                text = day.getDisplayName(TextStyle.SHORT, locale).take(2).uppercase(locale),
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    isToday: Boolean,
    isSelected: Boolean,
    dot: DayDot,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val highlight = MaterialTheme.colorScheme.primary
    val backgroundAlpha = when {
        isToday -> 1f
        isSelected -> 0.2f
        else -> 0f
    }
    val contentColor = if (isToday) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .padding(2.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(highlight.copy(alpha = backgroundAlpha))
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

