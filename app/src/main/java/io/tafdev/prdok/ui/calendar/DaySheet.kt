package io.tafdev.prdok.ui.calendar

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import dev.darkokoa.datetimewheelpicker.core.WheelPickerDefaults
import dev.darkokoa.datetimewheelpicker.core.WheelTextPicker
import io.tafdev.prdok.R
import io.tafdev.prdok.data.model.PragueTime
import io.tafdev.prdok.data.model.Shift
import io.tafdev.prdok.data.model.ShiftKind
import io.tafdev.prdok.ui.theme.PrdokForAndroidTheme
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

private val OfferedColor = Color(0xFF66FF33).copy(alpha = 0.5f)
private val PlannedColor = Color(0xFF4CC417).copy(alpha = 0.5f)
private val ActualColor = Color(0xFFBDB76B).copy(alpha = 0.5f)

private val TRACK_HEIGHT = 40.dp
private val HEADER_HEIGHT = 28.dp
private val SECTION_SPACING = 24.dp
private val TRACK_CORNER = 16.dp
private val MIN_PILL_WIDTH = 40.dp
private val SUBMIT_HEIGHT = 52.dp

/** Odd row count so one row sits centred under the selector, with two dimmed rows either side. */
private const val WHEEL_ROWS = 5
private val WHEEL_ROW_HEIGHT = 44.dp

/** §6.2: start 7..24, end 8..25, start < end. The wheels carry the full ranges. */
private val START_HOURS = (7..24).toList()
private val END_HOURS = (8..25).toList()

private val TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm")

/**
 * Day detail: one time track per shift kind, each shift drawn as a pill
 * carrying its own time range. The sheet is always exactly as tall as its content.
 * `skipPartiallyExpanded` drops the half-height anchor, which would otherwise
 * appear the moment the offer form makes the sheet tall enough.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DaySheet(
    date: LocalDate,
    shifts: List<Shift>,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onOffer: (date: LocalDate, startHour: Int, endHour: Int) -> Unit,
    onRemoveOffer: (shiftId: Int) -> Unit,
    onShowWhoIsOnShift: (LocalDate) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var offering by rememberSaveable { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        DaySheetContent(
            date = date,
            shifts = shifts,
            isSubmitting = isSubmitting,
            offering = offering,
            onStartOffering = { offering = true },
            onOffer = onOffer,
            onRemoveOffer = onRemoveOffer,
            onShowWhoIsOnShift = onShowWhoIsOnShift,
        )
    }
}

/**
 * Everything inside the sheet. Split out from [DaySheet] because a `ModalBottomSheet`
 * lives in its own window and won't render in the design tab - this part will.
 */
@Composable
fun DaySheetContent(
    date: LocalDate,
    shifts: List<Shift>,
    isSubmitting: Boolean,
    offering: Boolean,
    onStartOffering: () -> Unit,
    onOffer: (date: LocalDate, startHour: Int, endHour: Int) -> Unit,
    onRemoveOffer: (shiftId: Int) -> Unit,
    onShowWhoIsOnShift: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = Locale.getDefault()

    val offered = shifts.filter { it.kind == ShiftKind.OFFERED }
    val planned = shifts.filter { it.kind == ShiftKind.PLANNED }
    val actual = shifts.filter { it.kind == ShiftKind.ACTUAL }

    // Nothing to offer once the day is already offered or rostered.
    val canOffer = offered.isEmpty() && planned.isEmpty()

    Column(
        modifier = modifier
            // The scroll only ever engages on a screen too short to fit the form.
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(SECTION_SPACING),
    ) {
        Text(
            text = date.format(rememberDayTitleFormatter(locale)),
            style = MaterialTheme.typography.titleLarge,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
        )

        ShiftSection(
            title = stringResource(R.string.day_offered),
            color = OfferedColor,
            shifts = offered,
            action = {
                val toRemove = offered.firstOrNull()
                when {
                    toRemove != null -> SectionAction(
                        text = stringResource(R.string.day_remove_offer),
                        color = MaterialTheme.colorScheme.error,
                        enabled = !isSubmitting,
                        isBusy = isSubmitting,
                        onClick = { onRemoveOffer(toRemove.id) },
                    )
                    canOffer && !offering -> SectionAction(
                        text = stringResource(R.string.day_offer),
                        onClick = onStartOffering,
                    )
                }
            },
        )
        ShiftSection(
            title = stringResource(R.string.day_planned),
            color = PlannedColor,
            shifts = planned,
            action = {
                SectionAction(
                    text = stringResource(R.string.day_show_who),
                    onClick = { onShowWhoIsOnShift(date) },
                )
            },
        )
        ShiftSection(
            title = stringResource(R.string.day_actual),
            color = ActualColor,
            shifts = actual,
        )
        // The form has to grow into place rather than appear: the sheet's height is its
        // content's height, and a one-frame jump there makes the sheet snap
        AnimatedVisibility(
            visible = offering,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            OfferForm(
                isSubmitting = isSubmitting,
                onSubmit = { start, end -> onOffer(date, start, end) },
                modifier = Modifier.padding(top = SECTION_SPACING),
            )
        }
    }
}

/**
 * "13 September 2026" / "13. září 2026" — the locale decides the order and punctuation.
 * Pure `java.time` rather than `DateFormat.getBestDateTimePattern`: same text, but no
 * framework call, so it also works in the design tab and in unit tests.
 */
@Composable
private fun rememberDayTitleFormatter(locale: Locale): DateTimeFormatter = remember(locale) {
    DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(locale)
}

@Composable
private fun ShiftSection(
    title: String,
    color: Color,
    shifts: List<Shift>,
    action: (@Composable () -> Unit)? = null,
) {

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(HEADER_HEIGHT),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = title, style = sectionTextStyle())
            Spacer(Modifier.weight(1f))
            action?.invoke()
        }
        ShiftIndicator(shifts = shifts, color = color)
    }
}

/**
 * The underlined text actions at the right of a section title. Deliberately not a
 * Material3 `TextButton`, its 40 dp minimum height makes unwanted gaps.
 */
@Composable
private fun SectionAction(
    text: String,
    onClick: () -> Unit,
    color: Color = MaterialTheme.colorScheme.onSurface,
    enabled: Boolean = true,
    isBusy: Boolean = false,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .fillMaxHeight()
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (isBusy) {
            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = color)
        }
        Text(text = text, style = sectionTextStyle(), color = color, textDecoration = TextDecoration.Underline)
    }
}

@Composable
private fun sectionTextStyle(): TextStyle = MaterialTheme.typography.bodySmall.copy(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.SemiBold,
)

/**
 * One track per section: every shift of that kind becomes a pill placed and sized by
 * [ShiftTimeline], with its time range printed inside.
 */
@Composable
private fun ShiftIndicator(shifts: List<Shift>, color: Color, modifier: Modifier = Modifier) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(TRACK_HEIGHT)
            .background(
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
                RoundedCornerShape(TRACK_CORNER),
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (shifts.isEmpty()) {
            Text(text = "–", style = sectionTextStyle())
            return@BoxWithConstraints
        }
        // BoxWithConstraints hands us the settled track width, which is what turns
        // ShiftTimeline's 0..1 fractions into real dp.
        val trackWidth: Dp = maxWidth
        shifts.forEach { shift ->
            val range = ShiftTimeline.normalizedRange(shift.start, shift.end)
            val pillWidth = (trackWidth * (range.end - range.start)).coerceIn(MIN_PILL_WIDTH, trackWidth)
            // Keep the pill inside the track when the minimum width pushes it past an edge.
            val pillX = (trackWidth * range.start).coerceIn(0.dp, (trackWidth - pillWidth).coerceAtLeast(0.dp))
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = pillX)
                    .width(pillWidth)
                    .fillMaxHeight()
                    // Never round more than the pill's own half-extent, or the corners overlap.
                    .background(color, RoundedCornerShape(minOf(TRACK_CORNER, pillWidth / 2, TRACK_HEIGHT / 2))),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = shift.timeRange(),
                    style = sectionTextStyle(),
                    color = MaterialTheme.colorScheme.onSurface,
                    // Narrow pills let the label spill out rather than wrapping it.
                    softWrap = false,
                )
            }
        }
    }
}

private fun Shift.timeRange(): String = "${start.format(TIME_FORMAT)} - ${end.format(TIME_FORMAT)}"

@Composable
private fun OfferForm(
    isSubmitting: Boolean,
    onSubmit: (startHour: Int, endHour: Int) -> Unit,
    // no cancel, dismiss to back out
    modifier: Modifier = Modifier,
) {
    var startHour by rememberSaveable { mutableIntStateOf(7) }
    var endHour by rememberSaveable { mutableIntStateOf(25) }
    
    val isRangeValid = startHour < endHour

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            HourWheel(
                label = stringResource(R.string.offer_from),
                hours = START_HOURS,
                selected = startHour,
                onSettle = { startHour = it },
                modifier = Modifier.weight(1f),
            )
            HourWheel(
                label = stringResource(R.string.offer_to),
                hours = END_HOURS,
                selected = endHour,
                onSettle = { endHour = it },
                modifier = Modifier.weight(1f),
            )
        }
        Button(
            onClick = { onSubmit(startHour, endHour) },
            enabled = !isSubmitting && isRangeValid,
            modifier = Modifier
                .fillMaxWidth()
                .height(SUBMIT_HEIGHT),
        ) {
            if (isSubmitting) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Text(stringResource(R.string.offer_submit), style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}

/**
 * An iOS-style spinning wheel of whole hours; "24:00" and "25:00" mean past midnight.
 *
 * [onSettle] receives the hour the wheel came to rest on. The wheel never moves on its
 * own: wherever it is flicked is where it stays, and validity is the caller's business.
 */
@Composable
private fun HourWheel(
    label: String,
    hours: List<Int>,
    selected: Int,
    onSettle: (hour: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(text = label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = FontFamily.Serif)
        // The wheel wants a concrete DpSize rather than a fill modifier, so the width
        // this column was given has to be measured before it can be handed over.
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            WheelTextPicker(
                size = DpSize(maxWidth, WHEEL_ROW_HEIGHT * WHEEL_ROWS),
                rowCount = WHEEL_ROWS,
                texts = hours.map(::hourLabel),
                startIndex = hours.indexOf(selected).coerceAtLeast(0),
                style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurface,
                selectorProperties = WheelPickerDefaults.selectorProperties(
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
                    border = null,
                    shape = RoundedCornerShape(TRACK_CORNER),
                ),
                onScrollFinished = { index ->
                    onSettle(hours[index])
                    // null accepts the snap the wheel already made; an index would
                    // scroll it somewhere else, which is exactly what we don't want.
                    null
                },
            )
        }
    }
}

private fun hourLabel(hour: Int) = "%02d:00".format(Locale.ROOT, hour)
