package io.tafdev.prdok.ui.calendar

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.coerceIn
import androidx.compose.ui.unit.dp
import io.tafdev.prdok.R
import io.tafdev.prdok.data.model.FreeShift
import io.tafdev.prdok.data.model.FreeShiftRole
import io.tafdev.prdok.data.model.PragueTime
import io.tafdev.prdok.ui.theme.PrdokForAndroidTheme
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/** The axis is [ShiftTimeline]'s 07:00 to 01:00, where every shift falls; a tick interval dividing its 18 hours labels both ends. */
private val AXIS_START_HOUR = ShiftTimeline.START_HOUR.toInt()
private val AXIS_END_HOUR = ShiftTimeline.END_HOUR.toInt()
private const val AXIS_TICK_HOURS = 9

private val DATE_COLUMN_WIDTH = 60.dp
private val COLUMN_GAP = 8.dp

private val BAR_HEIGHT = 8.dp
private val LANE_GAP = 3.dp
private val TRACK_LINE_HEIGHT = 2.dp
private val MIN_BAR_WIDTH = 6.dp

/** Each bar gives up this much of its length, so two shifts touching in one lane stay two bars. */
private val BAR_END_GAP = 2.dp

private val DATE_FORMAT = DateTimeFormatter.ofPattern("d.M.")

/**
 * "Handlování směn": the open shifts anyone can pick up, under the statistics.
 * One row per day on a shared time axis; tapping a day opens who is on shift then.
 * Read-only; refreshed by the calendar's refresh button, never on its own.
 *
 * Plain Columns rather than a LazyColumn: the screen already scrolls, and a lazy
 * list inside a scrolling column has no height to lay out against.
 */
@Composable
fun FreeShiftsSection(load: FreeShiftsLoad, onOpenDay: (LocalDate) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.free_shifts_title),
            style = MaterialTheme.typography.titleLarge,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        when (load) {
            FreeShiftsLoad.Loading -> Centered { CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) }
            FreeShiftsLoad.Failed -> Centered { Note(stringResource(R.string.free_shifts_error)) }
            is FreeShiftsLoad.Loaded -> if (load.shifts.isEmpty()) {
                Centered { Note(stringResource(R.string.free_shifts_empty)) }
            } else {
                Timeline(load.shifts, onOpenDay)
            }
        }
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun Note(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** One day of the timeline: its shifts, and the same shifts as positions spread over lanes. */
private class FreeShiftDay(val date: LocalDate, val shifts: List<FreeShift>, val lanes: List<List<ShiftTimeline.Range>>)

private fun List<FreeShift>.byDay(): List<FreeShiftDay> =
    groupBy { it.start.toLocalDate() }
        .toSortedMap()
        .map { (date, shifts) ->
            val ranges = shifts.map { ShiftTimeline.normalizedRange(it.start, it.end) }
            FreeShiftDay(date, shifts, ShiftTimeline.lanes(ranges))
        }

@Composable
private fun Timeline(shifts: List<FreeShift>, onOpenDay: (LocalDate) -> Unit) {
    // Grouping and lane packing rerun only when a different list arrives, not on every recomposition.
    val days = remember(shifts) { shifts.byDay() }
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(COLUMN_GAP)) {
            Spacer(Modifier.width(DATE_COLUMN_WIDTH))
            AxisLabels(Modifier.weight(1f))
        }
        days.forEach { day ->
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            DayRow(day, onClick = { onOpenDay(day.date) })
        }
    }
}

/**
 * Hour labels centred on their ticks. A Row can only put children one after another;
 * a custom [Layout] measures them and then places each wherever it likes - here at its
 * hour's fraction of the width. The first may hang half outside into the empty date column;
 * the last is pulled back so its right edge ends flush with the rows below.
 */
@Composable
private fun AxisLabels(modifier: Modifier = Modifier) {
    val hours = (AXIS_START_HOUR..AXIS_END_HOUR step AXIS_TICK_HOURS).toList()
    Layout(
        content = {
            hours.forEach { hour ->
                Text(
                    text = (hour % 24).toString().padStart(2, '0'),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        modifier = modifier,
    ) { measurables, constraints ->
        // The width is fixed by the Row's weight; each label is free to be as narrow as its text.
        val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0, minHeight = 0)) }
        val width = constraints.maxWidth
        layout(width, placeables.maxOfOrNull { it.height } ?: 0) {
            placeables.forEachIndexed { index, label ->
                val fraction = (hours[index] - AXIS_START_HOUR).toFloat() / (AXIS_END_HOUR - AXIS_START_HOUR)
                val centredX = (width * fraction).roundToInt() - label.width / 2
                label.placeRelative(centredX.coerceAtMost(width - label.width), 0)
            }
        }
    }
}

@Composable
private fun DayRow(day: FreeShiftDay, onClick: () -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    // The bars carry no text, so a screen reader gets the times and roles spelled out instead.
    // map is an inline function, so its lambda may call the composable label().
    val description = day.shifts
        .map { shift -> listOfNotNull(shift.span().timeRange(), shift.role.label()).joinToString(" ") }
        .joinToString()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(COLUMN_GAP),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.width(DATE_COLUMN_WIDTH)) {
            Text(
                text = day.date.format(DATE_FORMAT),
                style = MaterialTheme.typography.bodyLarge,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            Text(
                text = day.date.dayOfWeek.shortLabel(Locale.getDefault()),
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = muted,
            )
        }
        DayTrack(
            lanes = day.lanes,
            modifier = Modifier
                .weight(1f)
                .semantics { contentDescription = description },
        )
    }
}

/** A thin track line with the day's lanes stacked over it; the row grows by a lane when it needs one. */
@Composable
private fun DayTrack(lanes: List<List<ShiftTimeline.Range>>, modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.CenterStart) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(TRACK_LINE_HEIGHT)
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f), CircleShape),
        )
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(LANE_GAP)) {
            lanes.forEach { Lane(it) }
        }
    }
}

@Composable
private fun Lane(ranges: List<ShiftTimeline.Range>) {
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(BAR_HEIGHT),
    ) {
        val trackWidth = maxWidth
        // The bar colour is see-through, which would let the track line show through the bars lying on it.
        // compositeOver blends it with the background once, giving the same colour but opaque.
        val barColor = PlannedColor.compositeOver(MaterialTheme.colorScheme.background)
        ranges.forEach { range ->
            val barWidth = (trackWidth * (range.end - range.start) - BAR_END_GAP).coerceIn(MIN_BAR_WIDTH, trackWidth)
            // Keep the bar inside the track when the minimum width pushes it past an edge.
            val barX = (trackWidth * range.start + BAR_END_GAP / 2)
                .coerceIn(0.dp, (trackWidth - barWidth).coerceAtLeast(0.dp))
            Box(
                Modifier
                    .offset(x = barX)
                    .width(barWidth)
                    .fillMaxHeight()
                    .background(barColor, CircleShape),
            )
        }
    }
}

@Composable
private fun FreeShiftRole.label(): String? = when (this) {
    FreeShiftRole.MANAGER -> stringResource(R.string.free_shift_role_manager)
    FreeShiftRole.BARISTA -> stringResource(R.string.free_shift_role_barista)
    FreeShiftRole.REGULAR -> null
}

// --- Previews ---------------------------------------------------------------

private fun previewShift(id: Int, day: Int, from: Int, to: Int, role: FreeShiftRole = FreeShiftRole.REGULAR): FreeShift {
    val start = ZonedDateTime.of(2026, 9, day, from, 0, 0, 0, PragueTime.ZONE)
    val end = if (to > from) start.withHour(to) else start.plusDays(1).withHour(to)
    return FreeShift(id, start, end, role)
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun FreeShiftsSectionPreview() {
    PrdokForAndroidTheme {
        Surface {
            FreeShiftsSection(
                load = FreeShiftsLoad.Loaded(
                    listOf(
                        previewShift(1, 11, 9, 13),
                        // Two pairs overlapping: two lanes.
                        previewShift(2, 17, 7, 8),
                        previewShift(3, 17, 7, 11, FreeShiftRole.BARISTA),
                        previewShift(4, 17, 16, 0),
                        previewShift(5, 17, 17, 0),
                        previewShift(6, 20, 8, 11, FreeShiftRole.MANAGER),
                        // Three at once between 13 and 14: the row grows a third lane.
                        previewShift(7, 21, 10, 17),
                        previewShift(8, 21, 11, 14),
                        previewShift(9, 21, 13, 14),
                        // Touching in one lane, then the portal's 24:00-25:00.
                        previewShift(10, 25, 10, 16),
                        previewShift(11, 25, 16, 18),
                        previewShift(12, 25, 0, 1),
                    )
                ),
                onOpenDay = {},
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 360, name = "Empty")
@Composable
private fun FreeShiftsSectionEmptyPreview() {
    PrdokForAndroidTheme {
        Surface {
            FreeShiftsSection(load = FreeShiftsLoad.Loaded(emptyList()), onOpenDay = {}, modifier = Modifier.padding(16.dp))
        }
    }
}
