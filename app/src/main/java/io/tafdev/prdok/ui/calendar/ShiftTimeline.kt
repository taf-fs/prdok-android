package io.tafdev.prdok.ui.calendar

import io.tafdev.prdok.data.model.FreeShift
import io.tafdev.prdok.data.model.Shift
import java.time.Duration
import java.time.ZonedDateTime

/**
 * The one thing the time track needs from any kind of shift: when it starts and ends.
 * Both [Shift] and [FreeShift] convert to it, so one pill composable serves both.
 */
data class TimeSpan(val start: ZonedDateTime, val end: ZonedDateTime)

fun Shift.span() = TimeSpan(start, end)
fun FreeShift.span() = TimeSpan(start, end)

/**
 * Places shifts on a time track as fractions of its width, and spreads overlapping
 * ones over lanes.
 *
 * Plain Kotlin with no Compose in sight, so the geometry can be unit-tested;
 * the composables only turn the fractions into dp.
 */
object ShiftTimeline {

    /** The track spans 07:00 to 01:00 next day - the whole range the portal allows. */
    const val START_HOUR = 7.0
    const val END_HOUR = 25.0

    private const val TOTAL_HOURS = END_HOUR - START_HOUR

    /** Both ends as 0..1 positions on the track. */
    data class Range(val start: Float, val end: Float)

    fun normalizedRange(start: ZonedDateTime, end: ZonedDateTime): Range {
        // Both ends are measured from the SAME midnight, so a shift keeps its real
        // length instead of each end being placed by its wall-clock hour alone.
        var startHours = hoursSinceMidnightOf(start, start)
        var endHours = maxOf(hoursSinceMidnightOf(end, start), startHours)

        // A shift that also ends before 07:00 is a post-midnight one (the portal's
        // 24:00-25:00, which we store as 00:00-01:00), so it belongs at the far end.
        if (endHours < START_HOUR) {
            startHours += 24
            endHours += 24
        }
        return Range(normalized(startHours), normalized(endHours))
    }

    /**
     * Spreads [ranges] over as few lanes as possible, with no two ranges in a lane overlapping.
     *
     * Taken in order of start, each range joins the first lane whose last range has already
     * ended; only when every lane is still busy does a new one open. Because the earliest start
     * always goes first, a new lane opens only when that many ranges really run at the same
     * moment, so the lane count is the smallest possible. Ranges that merely touch share a lane.
     */
    fun lanes(ranges: List<Range>): List<List<Range>> {
        val lanes = mutableListOf<MutableList<Range>>()
        for (range in ranges.sortedWith(compareBy(Range::start, Range::end))) {
            val free = lanes.firstOrNull { it.last().end <= range.start }
            if (free != null) free += range else lanes += mutableListOf(range)
        }
        return lanes
    }

    private fun hoursSinceMidnightOf(time: ZonedDateTime, anchor: ZonedDateTime): Double {
        val midnight = anchor.toLocalDate().atStartOfDay(anchor.zone)
        return Duration.between(midnight, time).toMinutes() / 60.0
    }

    /** Clamped, so early starts sit flush left and anything past 01:00 sits flush right. */
    private fun normalized(hour: Double): Float =
        ((hour.coerceIn(START_HOUR, END_HOUR) - START_HOUR) / TOTAL_HOURS).toFloat()
}
