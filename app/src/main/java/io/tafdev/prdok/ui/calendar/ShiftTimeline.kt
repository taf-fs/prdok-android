package io.tafdev.prdok.ui.calendar

import java.time.Duration
import java.time.ZonedDateTime

/**
 * Places a shift on the day sheet's time track as two fractions of its width.
 *
 * Plain Kotlin with no Compose in sight, so the geometry can be unit-tested;
 * the composable only turns the fractions into dp.
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

    private fun hoursSinceMidnightOf(time: ZonedDateTime, anchor: ZonedDateTime): Double {
        val midnight = anchor.toLocalDate().atStartOfDay(anchor.zone)
        return Duration.between(midnight, time).toMinutes() / 60.0
    }

    /** Clamped, so early starts sit flush left and anything past 01:00 sits flush right. */
    private fun normalized(hour: Double): Float =
        ((hour.coerceIn(START_HOUR, END_HOUR) - START_HOUR) / TOTAL_HOURS).toFloat()
}
