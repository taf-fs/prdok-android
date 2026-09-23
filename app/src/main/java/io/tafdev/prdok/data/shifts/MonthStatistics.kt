package io.tafdev.prdok.data.shifts

import io.tafdev.prdok.data.bonus.BonusCondition
import io.tafdev.prdok.data.bonus.BonusEither
import io.tafdev.prdok.data.bonus.BonusStructure
import io.tafdev.prdok.data.model.PragueTime
import io.tafdev.prdok.data.model.Shift
import io.tafdev.prdok.data.model.ShiftKind
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalTime
import java.time.YearMonth
import kotlin.math.roundToInt

/**
 * One "x of y" requirement. [met] defaults to the obvious reading of the two numbers for
 * the local computation; a requirement taken from the server passes that server's own
 * verdict instead.
 */
data class Requirement(val value: Int, val required: Int, val met: Boolean = value >= required)

/**
 * A requirement satisfied by either its offered side or its actual (worked) side.
 *
 * [actual] is null when nobody can state it: only payroll counts worked hours and worked
 * closing shifts, so without the bonus payload that side shows a dash and [met] rests on
 * the offered side alone.
 */
data class EitherRequirement(
    val offered: Requirement,
    val actual: Requirement?,
    val met: Boolean = offered.met || actual?.met == true,
)

/**
 * The monthly requirements block under the calendar: the first three of the six bonus
 * conditions, which are the three the app can also work out for itself.
 *
 * With a bonus payload the numbers, the limits and the verdicts are the server's. Without
 * one they are computed from the month's shifts, and everything scales with
 * `coef = openDays / 30`, so a month the facility is open 26 days asks for less.
 */
data class MonthStatistics(
    val openDays: Int,
    /** Offered hours on Sat/Sun within 09:00-23:00, vs. coef x 18. */
    val weekendHours: Requirement,
    /** Offered closing shifts vs. coef x 12; the worked side is the server's alone. */
    val closingShifts: EitherRequirement,
    /** Shown for information only; not a requirement, and never sent by the server. */
    val plannedClosingShifts: Int,
    /** Offered hours vs. coef x 100; the worked side is the server's alone. */
    val totalHours: EitherRequirement,
) {
    companion object {
        private val WEEKEND_WINDOW_START: LocalTime = LocalTime.of(9, 0)
        private val WEEKEND_WINDOW_END: LocalTime = LocalTime.of(23, 0)

        /**
         * @param shifts the month's shift list as fetched (all three kinds).
         * @param openDays from `akce=otevrene_dny`; null falls back to the calendar day count.
         * @param bonus from `akce=mzdastruktura`; when present it owns all three rows, and
         *   the local computation below is only the offline fallback.
         */
        fun compute(
            shifts: List<Shift>,
            month: YearMonth,
            openDays: Int?,
            bonus: BonusStructure? = null,
        ): MonthStatistics {
            val days = openDays ?: month.lengthOfMonth()
            val coef = days / 30.0
            fun required(base: Int): Int = (coef * base).roundToInt()

            val offered = shifts.filter { it.kind == ShiftKind.OFFERED }
            val planned = shifts.filter { it.kind == ShiftKind.PLANNED }

            return MonthStatistics(
                openDays = days,
                weekendHours = bonus?.weekendHours?.toRequirement()
                    ?: Requirement(
                        value = roundedHours(offered.map(::weekendWindow)),
                        required = required(18),
                    ),
                closingShifts = bonus?.closingShifts?.toEitherRequirement()
                    ?: EitherRequirement(
                        offered = Requirement(offered.count(::isClosing), required(12)),
                        actual = null,
                    ),
                // Local either way: the server doesn't send it.
                plannedClosingShifts = planned.count(::isClosing),
                totalHours = bonus?.hours?.toEitherRequirement()
                    ?: EitherRequirement(
                        offered = Requirement(roundedHours(offered.map(::duration)), required(100)),
                        actual = null,
                    ),
            )
        }

        /** The server states both numbers on every side it sends, so a missing one is a 0. */
        private fun BonusCondition.toRequirement() = Requirement(value ?: 0, required ?: 0, met)

        private fun BonusEither.toEitherRequirement() =
            EitherRequirement(offered.toRequirement(), worked.toRequirement(), met)

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
