package io.tafdev.prdok.ui.calendar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kizitonwose.calendar.compose.HorizontalCalendar
import com.kizitonwose.calendar.compose.rememberCalendarState
import com.kizitonwose.calendar.core.DayPosition
import com.kizitonwose.calendar.core.OutDateStyle
import com.kizitonwose.calendar.core.daysOfWeek
import com.kizitonwose.calendar.core.firstDayOfWeekFromLocale
import io.tafdev.prdok.R
import io.tafdev.prdok.data.shifts.DayDot
import io.tafdev.prdok.ui.theme.PrdokForAndroidTheme
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

private val SUBMIT_HEIGHT = 52.dp

/**
 * `rememberSaveable` can only store what fits in a Bundle, and a `Set<LocalDate>`
 * doesn't. A Saver says how to flatten it (epoch days) and build it back.
 */
private val DateSetSaver = listSaver<Set<LocalDate>, Long>(
    save = { dates -> dates.map { it.toEpochDay() } },
    restore = { days -> days.map(LocalDate::ofEpochDay).toSet() },
)

/**
 * "Offer shifts": pick any number of days in the displayed month and one start/end
 * pair, and the ViewModel offers them one after another. Days that already carry an
 * offer are pre-marked and can't be picked.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MultiOfferSheet(
    month: YearMonth,
    offeredDays: Set<LocalDate>,
    progress: MultiOfferProgress?,
    onSubmit: (dates: Set<LocalDate>, startHour: Int, endHour: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    // The sheet state is created once, but its confirm lambda must see the *current*
    // progress; rememberUpdatedState is the idiom for a long-lived lambda reading fresh values.
    val isSubmitting by rememberUpdatedState(progress != null)
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        // A swipe or back press mid-batch is refused: the requests would carry on
        // regardless, and the counter would vanish from under the user.
        confirmValueChange = { !isSubmitting },
    )

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        MultiOfferContent(month = month, offeredDays = offeredDays, progress = progress, onSubmit = onSubmit)
    }
}

@Composable
fun MultiOfferContent(
    month: YearMonth,
    offeredDays: Set<LocalDate>,
    progress: MultiOfferProgress?,
    onSubmit: (dates: Set<LocalDate>, startHour: Int, endHour: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = Locale.getDefault()
    var selected by rememberSaveable(stateSaver = DateSetSaver) { mutableStateOf(emptySet<LocalDate>()) }
    var startHour by rememberSaveable { mutableIntStateOf(7) }
    var endHour by rememberSaveable { mutableIntStateOf(25) }
    val isSubmitting = progress != null
    val canSubmit = !isSubmitting && selected.isNotEmpty() && startHour < endHour

    // One month, scrolling off: a horizontal pager with a single page. The vertical
    // variant is a LazyColumn, which can't sit inside this sheet's own vertical scroll.
    val calendarState = rememberCalendarState(
        startMonth = month,
        endMonth = month,
        firstVisibleMonth = month,
        firstDayOfWeek = firstDayOfWeekFromLocale(),
        outDateStyle = OutDateStyle.EndOfGrid,
    )

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.multi_offer_title),
                style = MaterialTheme.typography.titleLarge,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = monthLabel(month, locale),
                style = MaterialTheme.typography.labelMedium,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Column {
            DaysOfWeekHeader(daysOfWeek(calendarState.firstDayOfWeek))
            HorizontalCalendar(
                state = calendarState,
                userScrollEnabled = false,
                dayContent = { day ->
                    if (day.position != DayPosition.MonthDate) {
                        Box(Modifier.aspectRatio(1f))
                    } else {
                        val alreadyOffered = day.date in offeredDays
                        val isSelected = day.date in selected
                        DayCell(
                            date = day.date,
                            dot = if (alreadyOffered) DayDot.FAINT else DayDot.NONE,
                            highlight = if (isSelected) 1f else 0f,
                            enabled = !isSubmitting && !alreadyOffered,
                            onClick = { selected = if (isSelected) selected - day.date else selected + day.date },
                        )
                    }
                },
            )
        }

        DualHourWheel(
            startHour = startHour,
            endHour = endHour,
            onStartSettle = { startHour = it },
            onEndSettle = { endHour = it },
        )

        Button(
            onClick = { onSubmit(selected, startHour, endHour) },
            enabled = canSubmit,
            modifier = Modifier
                .fillMaxWidth()
                .height(SUBMIT_HEIGHT),
        ) {
            when {
                progress != null -> SubmitProgress(progress)
                selected.isEmpty() -> Text(stringResource(R.string.multi_offer_submit_none))
                else -> Text(pluralStringResource(R.plurals.multi_offer_submit, selected.size, selected.size))
            }
        }
    }
}

@Composable
private fun SubmitProgress(progress: MultiOfferProgress) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        LinearProgressIndicator(
            progress = { progress.submitted.toFloat() / progress.total },
            modifier = Modifier.fillMaxWidth(0.6f),
        )
        Text(
            text = stringResource(R.string.multi_offer_progress, progress.submitted, progress.total),
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
        )
    }
}

// --- Previews ---------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PreviewMultiOffer(progress: MultiOfferProgress? = null) {
    PrdokForAndroidTheme {
        Surface(color = BottomSheetDefaults.ContainerColor) {
            Column {
                BottomSheetDefaults.DragHandle(modifier = Modifier.align(Alignment.CenterHorizontally))
                MultiOfferContent(
                    month = YearMonth.of(2026, 9),
                    offeredDays = setOf(LocalDate.of(2026, 9, 12), LocalDate.of(2026, 9, 19)),
                    progress = progress,
                    onSubmit = { _, _, _ -> },
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun MultiOfferPreview() = PreviewMultiOffer()

@Preview(showBackground = true, name = "Submitting")
@Composable
private fun MultiOfferSubmittingPreview() = PreviewMultiOffer(MultiOfferProgress(submitted = 2, total = 5))
