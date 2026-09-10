package io.tafdev.prdok.data.shifts

import io.tafdev.prdok.data.model.Shift
import io.tafdev.prdok.data.model.ShiftKind
import java.time.LocalDate

/** What the calendar draws under a day number. */
enum class DayDot { NONE, FAINT, SOLID }

/**
 * The three date sets behind the calendar's day dots, keyed by each shift's start date.
 * Built once per loaded year; every cell then just does set-membership checks.
 */
data class ShiftDays(
    val planned: Set<LocalDate> = emptySet(),
    val offered: Set<LocalDate> = emptySet(),
    val actual: Set<LocalDate> = emptySet(),
) {
    /** One dot per day at most: solid for planned/actual, faint for offered-only. */
    fun dotFor(date: LocalDate): DayDot = when {
        date in planned || date in actual -> DayDot.SOLID
        date in offered -> DayDot.FAINT
        else -> DayDot.NONE
    }

    companion object {
        fun of(shifts: List<Shift>): ShiftDays {
            // groupBy with two lambdas: the first picks the key, the second what to collect.
            val datesByKind = shifts.groupBy({ it.kind }, { it.start.toLocalDate() })
            return ShiftDays(
                planned = datesByKind[ShiftKind.PLANNED].orEmpty().toSet(),
                offered = datesByKind[ShiftKind.OFFERED].orEmpty().toSet(),
                actual = datesByKind[ShiftKind.ACTUAL].orEmpty().toSet(),
            )
        }
    }
}
