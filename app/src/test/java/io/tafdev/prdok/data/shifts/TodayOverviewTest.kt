package io.tafdev.prdok.data.shifts

import io.tafdev.prdok.data.model.PragueTime
import io.tafdev.prdok.data.model.Shift
import io.tafdev.prdok.data.model.ShiftKind
import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TodayOverviewTest {

    private fun at(date: String, time: String): ZonedDateTime =
        ZonedDateTime.of(LocalDate.parse(date), java.time.LocalTime.parse(time), PragueTime.ZONE)

    private fun shift(id: Int, date: String, from: String, to: String, kind: ShiftKind = ShiftKind.PLANNED): Shift {
        val start = at(date, from)
        var end = at(date, to)
        if (end.isBefore(start)) end = end.plusDays(1)
        return Shift(id, kind, start, end)
    }

    // -- RemainingTime -------------------------------------------------------

    @Test
    fun `remaining time picks the single largest unit`() {
        assertEquals(RemainingTime(2, CountdownUnit.DAYS), RemainingTime.of(Duration.ofHours(50)))
        assertEquals(RemainingTime(5, CountdownUnit.HOURS), RemainingTime.of(Duration.ofMinutes(5 * 60 + 59)))
        assertEquals(RemainingTime(13, CountdownUnit.MINUTES), RemainingTime.of(Duration.ofMinutes(13).plusSeconds(30)))
        assertEquals(RemainingTime(0, CountdownUnit.MINUTES), RemainingTime.of(Duration.ofSeconds(-5)))
    }

    // -- countdown -----------------------------------------------------------

    @Test
    fun `ongoing planned shift shows time until it ends`() {
        val now = at("2026-09-04", "18:30")
        val overview = TodayOverview.compute(listOf(shift(1, "2026-09-04", "16:00", "23:00")), now)

        val countdown = overview.countdown as Countdown.Ongoing
        assertEquals(1, countdown.shift.id)
        assertEquals(RemainingTime(4, CountdownUnit.HOURS), countdown.remaining)
        assertEquals(LocalDate.parse("2026-09-04"), overview.whoIsOnShiftDate)
    }

    @Test
    fun `shift ending exactly now is no longer ongoing`() {
        val now = at("2026-09-04", "23:00")
        val overview = TodayOverview.compute(listOf(shift(1, "2026-09-04", "16:00", "23:00")), now)
        assertEquals(Countdown.None, overview.countdown)
    }

    @Test
    fun `next shift later today says today`() {
        val now = at("2026-09-04", "09:00")
        val overview = TodayOverview.compute(listOf(shift(1, "2026-09-04", "16:00", "23:00")), now)
        val countdown = overview.countdown as Countdown.Upcoming
        assertEquals(StartsIn.Today, countdown.startsIn)
        assertEquals(LocalDate.parse("2026-09-04"), overview.whoIsOnShiftDate)
    }

    @Test
    fun `next shift tomorrow says tomorrow even if under 24h away`() {
        val now = at("2026-09-04", "23:30")
        val overview = TodayOverview.compute(listOf(shift(1, "2026-09-05", "08:00", "16:00")), now)
        assertEquals(StartsIn.Tomorrow, (overview.countdown as Countdown.Upcoming).startsIn)
        assertEquals(LocalDate.parse("2026-09-05"), overview.whoIsOnShiftDate)
    }

    @Test
    fun `next shift further out counts days from the start of today`() {
        // Friday 23:30 -> Sunday 08:00 is only 32.5 h away, but counted from Friday 00:00
        // it's 2 days 8 h, so the card says "2 days" rather than "1 day".
        val now = at("2026-09-04", "23:30")
        val overview = TodayOverview.compute(listOf(shift(1, "2026-09-06", "08:00", "16:00")), now)
        val startsIn = (overview.countdown as Countdown.Upcoming).startsIn as StartsIn.Later
        assertEquals(RemainingTime(2, CountdownUnit.DAYS), startsIn.remaining)
    }

    @Test
    fun `only planned shifts drive the countdown`() {
        val now = at("2026-09-04", "18:00")
        val overview = TodayOverview.compute(
            listOf(
                shift(1, "2026-09-04", "16:00", "23:00", ShiftKind.ACTUAL),
                shift(2, "2026-09-05", "16:00", "23:00", ShiftKind.OFFERED),
            ),
            now,
        )
        assertEquals(Countdown.None, overview.countdown)
        assertTrue(overview.upcoming.isEmpty())
        assertEquals(LocalDate.parse("2026-09-04"), overview.whoIsOnShiftDate)
    }

    @Test
    fun `ongoing wins over an upcoming shift`() {
        val now = at("2026-09-04", "18:00")
        val overview = TodayOverview.compute(
            listOf(
                shift(2, "2026-09-05", "16:00", "23:00"),
                shift(1, "2026-09-04", "16:00", "23:00"),
            ),
            now,
        )
        assertEquals(1, (overview.countdown as Countdown.Ongoing).shift.id)
        // ...but the upcoming list still only holds future shifts
        assertEquals(listOf(2), overview.upcoming.map { it.id })
    }

    // -- upcoming list -------------------------------------------------------

    @Test
    fun `upcoming list is future planned shifts ascending`() {
        val now = at("2026-09-04", "12:00")
        val overview = TodayOverview.compute(
            listOf(
                shift(3, "2026-09-10", "16:00", "23:00"),
                shift(1, "2026-09-01", "16:00", "23:00"),   // past
                shift(2, "2026-09-06", "16:00", "23:00"),
                shift(4, "2026-09-08", "16:00", "23:00", ShiftKind.OFFERED),
            ),
            now,
        )
        assertEquals(listOf(2, 3), overview.upcoming.map { it.id })
    }

    @Test
    fun `cross-midnight shift is ongoing after midnight`() {
        val now = at("2026-09-05", "00:30")
        val overview = TodayOverview.compute(listOf(shift(1, "2026-09-04", "17:00", "01:00")), now)
        val countdown = overview.countdown as Countdown.Ongoing
        assertEquals(RemainingTime(30, CountdownUnit.MINUTES), countdown.remaining)
    }
}
