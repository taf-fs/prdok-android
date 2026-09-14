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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.coerceIn
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/*
 * One row per day on a shared 07:00 to 01:00 axis, with each shift as a bar and overlapping
 * shifts spread over lanes. Tapping a day hands its date back to the caller. Used by the free
 * shifts on the Calendar tab and the upcoming planned shifts on the Today tab.
 */

/** The axis is [ShiftTimeline]'s 07:00 to 01:00, where every shift falls; a tick interval dividing its 18 hours labels both ends. */
private val AXIS_START_HOUR = ShiftTimeline.START_HOUR.toInt()
private val AXIS_END_HOUR = ShiftTimeline.END_HOUR.toInt()
private const val AXIS_TICK_HOURS = 9

private val DATE_COLUMN_WIDTH = 60.dp
private val COLUMN_GAP = 8.dp

/** Tall enough to fit the role letter inside. */
private val BAR_HEIGHT = 14.dp
private val LANE_GAP = 3.dp
private val TRACK_LINE_HEIGHT = 2.dp
private val MIN_BAR_WIDTH = BAR_HEIGHT
private val LETTER_SIZE = 10.sp

/** Each bar gives up this much of its length, so two shifts touching in one lane stay two bars. */
private val BAR_END_GAP = 2.dp

private val DATE_FORMAT = DateTimeFormatter.ofPattern("d.M.")

/**
 * One shift as the timeline needs it: when, an optional letter inside the bar, and what a
 * screen reader gets for it, since the bars show no times.
 */
data class ShiftTimelineEntry(
    val start: ZonedDateTime,
    val end: ZonedDateTime,
    val letter: String? = null,
    val description: String,
)

/**
 * Axis and day rows together, for a caller that scrolls the whole thing.
 *
 * [background] is what the timeline sits on: the bars' see-through green is laid over it to come out opaque.
 */
@Composable
fun ShiftDayTimeline(
    entries: List<ShiftTimelineEntry>,
    onOpenDay: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    background: Color = MaterialTheme.colorScheme.background,
) {
    Column(modifier) {
        ShiftTimelineAxis()
        ShiftDayTimelineRows(entries, onOpenDay, background = background)
    }
}

/** Hour labels over the tracks, indented past the date column. Separate from the rows so a caller can keep it in place while the rows scroll. */
@Composable
fun ShiftTimelineAxis(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clearAndSetSemantics {},
        horizontalArrangement = Arrangement.spacedBy(COLUMN_GAP),
    ) {
        Spacer(Modifier.width(DATE_COLUMN_WIDTH))
        AxisLabels(Modifier.weight(1f))
    }
}

/**
 * The day rows alone, each under a divider. Plain Columns rather than a LazyColumn: the caller
 * does the scrolling, and a lazy list inside a scrolling column has no height to lay out against.
 */
@Composable
fun ShiftDayTimelineRows(
    entries: List<ShiftTimelineEntry>,
    onOpenDay: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    background: Color = MaterialTheme.colorScheme.background,
) {
    // Grouping and lane packing rerun only when a different list arrives, not on every recomposition.
    val days = remember(entries) { entries.byDay() }
    Column(modifier) {
        days.forEach { day ->
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            DayRow(day, background, onClick = { onOpenDay(day.date) })
        }
    }
}

/** One shift's bar: where it sits on the track, and the letter drawn inside it. */
private class Bar(val range: ShiftTimeline.Range, val letter: String?)

/** One day of the timeline: its entries, and the same entries as bars spread over lanes. */
private class TimelineDay(val date: LocalDate, val entries: List<ShiftTimelineEntry>, val lanes: List<List<Bar>>)

private fun List<ShiftTimelineEntry>.byDay(): List<TimelineDay> =
    groupBy { it.start.toLocalDate() }
        .toSortedMap()
        .map { (date, entries) ->
            val bars = entries.map { Bar(ShiftTimeline.normalizedRange(it.start, it.end), it.letter) }
            TimelineDay(date, entries, ShiftTimeline.lanes(bars) { it.range })
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
private fun DayRow(day: TimelineDay, background: Color, onClick: () -> Unit) {
    val description = day.entries.sortedBy { it.start }.joinToString { it.description }

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
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DayTrack(
            lanes = day.lanes,
            background = background,
            modifier = Modifier
                .weight(1f)
                .semantics { contentDescription = description },
        )
    }
}

/** A thin track line with the day's lanes stacked over it; the row grows by a lane when it needs one. */
@Composable
private fun DayTrack(lanes: List<List<Bar>>, background: Color, modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.CenterStart) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(TRACK_LINE_HEIGHT)
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f), CircleShape),
        )
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(LANE_GAP)) {
            lanes.forEach { Lane(it, background) }
        }
    }
}

@Composable
private fun Lane(bars: List<Bar>, background: Color) {
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(BAR_HEIGHT),
    ) {
        val trackWidth = maxWidth
        // The bar colour is see-through, which would let the track line show through the bars lying on it.
        // compositeOver blends it with the background once, giving the same colour but opaque.
        val barColor = PlannedColor.compositeOver(background)
        bars.forEach { bar ->
            val range = bar.range
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
                contentAlignment = Alignment.Center,
            ) {
                if (bar.letter != null) {
                    Text(
                        text = bar.letter,
                        fontSize = LETTER_SIZE,
                        lineHeight = LETTER_SIZE,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        }
    }
}
