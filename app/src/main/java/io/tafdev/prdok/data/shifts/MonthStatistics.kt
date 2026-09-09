package io.tafdev.prdok.data.shifts

import io.tafdev.prdok.data.model.PragueTime
import io.tafdev.prdok.data.model.Shift
import io.tafdev.prdok.data.model.ShiftKind
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalTime
import java.time.YearMonth
import kotlin.math.roundToInt

/** One "x of y" requirement; met once [value] reaches [required]. */
data class Requirement(val value: Int, val required: Int) {
    val met: Boolean get() = value >= required
}

/** A requirement satisfied by either its offered side or its actual (worked) side. */
data class EitherRequirement(val offered: Requirement, val actual: Requirement) {
    val met: Boolean get() = offered.met || actual.met
}

/**
 * The monthly requirements block under the calendar. Everything scales with
 * `coef = openDays / 30`, so a month the facility is open 26 days asks for less.
 */
data class MonthStatistics(
    val openDays: Int,
    /** Offered hours on Sat/Sun within 09:00-23:00, vs. coef x 18. */
    val weekendHours: Requirement,
    /** Offered closing shifts vs. coef x 12, or actual ones vs. coef x 4. */
    val closingShifts: EitherRequirement,
    /** Shown for information only; not a requirement. */
    val plannedClosingShifts: Int,
    /** Offered hours vs. coef x 100, or actual hours vs. coef x 72. */
    val totalHours: EitherRequirement,
) {
    companion object {
        private val WEEKEND_WINDOW_START: LocalTime = LocalTime.of(9, 0)
        private val WEEKEND_WINDOW_END: LocalTime = LocalTime.of(23, 0)

        /**
         * @param shifts the month's shift list as fetched (all three kinds).
         * @param openDays from `akce=otevrene_dny`; null falls back to the calendar day count.
         */
        fun compute(shifts: List<Shift>, month: YearMonth, openDays: Int?): MonthStatistics {
            val days = openDays ?: month.lengthOfMonth()
            val coef = days / 30.0
            // A local function: only meaningful inside compute, so it lives here.
            fun required(base: Int): Int = (coef * base).roundToInt()

            val offered = shifts.filter { it.kind == ShiftKind.OFFERED }
            val planned = shifts.filter { it.kind == ShiftKind.PLANNED }
            val actual = shifts.filter { it.kind == ShiftKind.ACTUAL }

            return MonthStatistics(
                openDays = days,
                weekendHours = Requirement(
                    value = roundedHours(offered.map(::weekendWindow)),
                    required = required(18),
                ),
                closingShifts = EitherRequirement(
                    offered = Requirement(offered.count(::isClosing), required(12)),
                    actual = Requirement(actual.count(::isClosing), required(4)),
                ),
                plannedClosingShifts = planned.count(::isClosing),
                totalHours = EitherRequirement(
                    offered = Requirement(roundedHours(offered.map(::duration)), required(100)),
                    actual = Requirement(roundedHours(actual.map(::duration)), required(72)),
                ),
            )
        }

        /**
         * Sum first, round once. Rounding each shift on its own would turn a
         * 16:01-24:58 punch-clock entry into 8 h instead of the 8.95 h it contributes.
         */
        private fun roundedHours(durations: List<Duration>): Int =
            (durations.sumOf { it.toMinutes() } / 60.0).roundToInt()

        private fun duration(shift: Shift): Duration = Duration.between(shift.start, shift.end)

        /** The part of [shift] that counts as weekend work: Sat/Sun, clamped to 09:00-23:00 of its start day. */
        internal fun weekendWindow(shift: Shift): Duration {
            if (!shift.start.dayOfWeek.isWeekend()) return Duration.ZERO
            val day = shift.start.toLocalDate()
            val windowStart = day.atTime(WEEKEND_WINDOW_START).atZone(PragueTime.ZONE)
            val windowEnd = day.atTime(WEEKEND_WINDOW_END).atZone(PragueTime.ZONE)
            val from = maxOf(shift.start, windowStart)
            val to = minOf(shift.end, windowEnd)
            return if (to.isAfter(from)) Duration.between(from, to) else Duration.ZERO
        }

        /**
         * Weekend: ends at 23:00 or later on the same day, or before 06:00 the next day.
         * Weekday: ends between 01:00 and 05:59 the next day.
         */
        internal fun isClosing(shift: Shift): Boolean {
            val endsNextDay = shift.end.toLocalDate().isAfter(shift.start.toLocalDate())
            val endHour = shift.end.hour
            return if (shift.start.dayOfWeek.isWeekend()) {
                (!endsNextDay && endHour >= 23) || (endsNextDay && endHour < 6)
            } else {
                endsNextDay && endHour in 1..5
            }
        }

        private fun DayOfWeek.isWeekend() = this == DayOfWeek.SATURDAY || this == DayOfWeek.SUNDAY
    }
}
