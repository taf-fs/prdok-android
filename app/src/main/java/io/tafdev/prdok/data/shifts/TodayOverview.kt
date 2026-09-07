package io.tafdev.prdok.data.shifts

import io.tafdev.prdok.data.model.PragueTime
import io.tafdev.prdok.data.model.Shift
import io.tafdev.prdok.data.model.ShiftKind
import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime

enum class CountdownUnit { DAYS, HOURS, MINUTES }

/** A duration reduced to its single largest unit: "2 days", "5 hours", "13 minutes". */
data class RemainingTime(val value: Long, val unit: CountdownUnit) {
    companion object {
        fun of(duration: Duration): RemainingTime {
            val d = if (duration.isNegative) Duration.ZERO else duration
            return when {
                d.toDays() >= 1 -> RemainingTime(d.toDays(), CountdownUnit.DAYS)
                d.toHours() >= 1 -> RemainingTime(d.toHours(), CountdownUnit.HOURS)
                else -> RemainingTime(d.toMinutes(), CountdownUnit.MINUTES)
            }
        }
    }
}

/** When the next planned shift starts, in the words the Today card uses. */
sealed class StartsIn {
    data object Today : StartsIn()
    data object Tomorrow : StartsIn()

    /** Counted from the start of today, not from now. */
    data class Later(val remaining: RemainingTime) : StartsIn()
}

sealed class Countdown {
    /** A planned shift is running right now (`start <= now < end`). */
    data class Ongoing(val shift: Shift, val remaining: RemainingTime) : Countdown()

    data class Upcoming(val shift: Shift, val startsIn: StartsIn) : Countdown()

    data object None : Countdown()
}

/**
 * Everything the Today screen derives from the loaded shifts and the current time.
 */
data class TodayOverview(
    val countdown: Countdown,
    /** Planned shifts starting after now, ascending. */
    val upcoming: List<Shift>,
    /** Which day the "who is on shift" page should open on. */
    val whoIsOnShiftDate: LocalDate,
) {
    companion object {
        fun compute(shifts: List<Shift>, now: ZonedDateTime): TodayOverview {
            val today = now.withZoneSameInstant(PragueTime.ZONE).toLocalDate()
            val planned = shifts.filter { it.kind == ShiftKind.PLANNED }

            val ongoing = planned
                .filter { !now.isBefore(it.start) && now.isBefore(it.end) }
                .minByOrNull { it.end }
            val upcoming = planned
                .filter { it.start.isAfter(now) }
                .sortedBy { it.start }
            val next = upcoming.firstOrNull()

            val countdown = when {
                ongoing != null -> Countdown.Ongoing(
                    shift = ongoing,
                    remaining = RemainingTime.of(Duration.between(now, ongoing.end)),
                )
                next != null -> Countdown.Upcoming(shift = next, startsIn = startsIn(next, today))
                else -> Countdown.None
            }

            val whoIsOnShiftDate = when {
                ongoing != null -> today
                next != null -> next.start.pragueDate()
                else -> today
            }

            return TodayOverview(countdown, upcoming, whoIsOnShiftDate)
        }

        private fun startsIn(next: Shift, today: LocalDate): StartsIn = when (next.start.pragueDate()) {
            today -> StartsIn.Today
            today.plusDays(1) -> StartsIn.Tomorrow
            else -> StartsIn.Later(
                RemainingTime.of(Duration.between(today.atStartOfDay(PragueTime.ZONE), next.start))
            )
        }

        private fun ZonedDateTime.pragueDate(): LocalDate = withZoneSameInstant(PragueTime.ZONE).toLocalDate()
    }
}
