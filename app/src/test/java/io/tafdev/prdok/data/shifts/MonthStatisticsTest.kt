package io.tafdev.prdok.data.shifts

import io.tafdev.prdok.data.model.PragueTime
import io.tafdev.prdok.data.model.Shift
import io.tafdev.prdok.data.model.ShiftKind
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MonthStatisticsTest {

    // September 2026: the 5th/6th are Sat/Sun, the 7th is a Monday.
    private val september = YearMonth.of(2026, 9)

    private var nextId = 1

    private fun shift(kind: ShiftKind, date: String, from: String, to: String): Shift {
        val start = ZonedDateTime.of(LocalDate.parse(date), LocalTime.parse(from), PragueTime.ZONE)
        var end = ZonedDateTime.of(LocalDate.parse(date), LocalTime.parse(to), PragueTime.ZONE)
        if (end.isBefore(start)) end = end.plusDays(1)
        return Shift(nextId++, kind, start, end)
    }

    private fun offered(date: String, from: String, to: String) = shift(ShiftKind.OFFERED, date, from, to)
    private fun actual(date: String, from: String, to: String) = shift(ShiftKind.ACTUAL, date, from, to)
    private fun planned(date: String, from: String, to: String) = shift(ShiftKind.PLANNED, date, from, to)

    // -- coefficient ---------------------------------------------------------

    @Test
    fun `requirements scale with the open-day coefficient and round to nearest`() {
        val stats = MonthStatistics.compute(emptyList(), september, openDays = 26)
        // coef = 26/30 = 0.8667
        assertEquals(16, stats.weekendHours.required)        // 15.6
        assertEquals(10, stats.closingShifts.offered.required) // 10.4
        assertEquals(3, stats.closingShifts.actual.required)   // 3.47
        assertEquals(87, stats.totalHours.offered.required)    // 86.7
        assertEquals(62, stats.totalHours.actual.required)     // 62.4
    }

    @Test
    fun `missing open-day count falls back to the calendar length`() {
        val stats = MonthStatistics.compute(emptyList(), september, openDays = null)
        assertEquals(30, stats.openDays)
        assertEquals(18, stats.weekendHours.required)
        assertEquals(100, stats.totalHours.offered.required)
    }

    // -- total hours ---------------------------------------------------------

    @Test
    fun `hours are summed before rounding`() {
        // Three punch-clock shifts of 8 h 57 min: 26.85 h total -> 27, not 3 x 8 = 24.
        val shifts = listOf(
            actual("2026-09-01", "16:01", "00:58"),
            actual("2026-09-02", "16:01", "00:58"),
            actual("2026-09-03", "16:01", "00:58"),
        )
        val stats = MonthStatistics.compute(shifts, september, 30)
        assertEquals(27, stats.totalHours.actual.value)
        assertEquals(0, stats.totalHours.offered.value)
    }

    @Test
    fun `either side satisfies the total hours requirement`() {
        val offeredOnly = (1..13).map { offered("2026-09-%02d".format(it), "15:00", "23:00") } // 104 h
        assertTrue(MonthStatistics.compute(offeredOnly, september, 30).totalHours.met)

        val actualOnly = (1..9).map { actual("2026-09-%02d".format(it), "15:00", "23:00") } // 72 h
        val stats = MonthStatistics.compute(actualOnly, september, 30)
        assertFalse(stats.totalHours.offered.met)
        assertTrue(stats.totalHours.actual.met)
        assertTrue(stats.totalHours.met)
    }

    // -- weekend hours -------------------------------------------------------

    @Test
    fun `weekend hours are clamped to the 9-23 window`() {
        val shifts = listOf(
            offered("2026-09-05", "07:00", "01:00"), // Saturday: only 09:00-23:00 counts -> 14 h
            offered("2026-09-06", "20:00", "22:00"), // Sunday, fully inside -> 2 h
            offered("2026-09-07", "09:00", "23:00"), // Monday -> 0
        )
        assertEquals(16, MonthStatistics.compute(shifts, september, 30).weekendHours.value)
    }

    @Test
    fun `weekend shift entirely outside the window counts nothing`() {
        val shifts = listOf(offered("2026-09-05", "23:00", "03:00"))
        assertEquals(0, MonthStatistics.compute(shifts, september, 30).weekendHours.value)
    }

    @Test
    fun `only offered shifts count towards weekend hours`() {
        val shifts = listOf(actual("2026-09-05", "10:00", "20:00"), planned("2026-09-06", "10:00", "20:00"))
        assertEquals(0, MonthStatistics.compute(shifts, september, 30).weekendHours.value)
    }

    // -- closing shifts ------------------------------------------------------

    @Test
    fun `weekend closing is 23 or later, or past midnight before 6`() {
        assertTrue(MonthStatistics.isClosing(offered("2026-09-05", "16:00", "23:00")))
        assertTrue(MonthStatistics.isClosing(offered("2026-09-05", "16:00", "00:00")))
        assertTrue(MonthStatistics.isClosing(offered("2026-09-05", "16:00", "05:59")))
        assertFalse(MonthStatistics.isClosing(offered("2026-09-05", "16:00", "22:59")))
        assertFalse(MonthStatistics.isClosing(offered("2026-09-05", "16:00", "06:00")))
    }

    @Test
    fun `weekday closing is between 1 and 6 next morning`() {
        assertTrue(MonthStatistics.isClosing(offered("2026-09-07", "16:00", "01:00")))
        assertTrue(MonthStatistics.isClosing(offered("2026-09-07", "16:00", "05:59")))
        assertFalse(MonthStatistics.isClosing(offered("2026-09-07", "16:00", "23:00")))
        assertFalse(MonthStatistics.isClosing(offered("2026-09-07", "16:00", "00:30")))
        assertFalse(MonthStatistics.isClosing(offered("2026-09-07", "16:00", "06:00")))
    }

    @Test
    fun `closing counts are split by kind and planned is informational`() {
        val shifts = listOf(
            offered("2026-09-07", "16:00", "02:00"),
            offered("2026-09-08", "16:00", "02:00"),
            actual("2026-09-09", "16:00", "02:00"),
            planned("2026-09-10", "16:00", "02:00"),
            planned("2026-09-11", "16:00", "22:00"),
        )
        val stats = MonthStatistics.compute(shifts, september, 10) // tiny coef so actual (1 >= 1) is met
        assertEquals(2, stats.closingShifts.offered.value)
        assertEquals(1, stats.closingShifts.actual.value)
        assertEquals(1, stats.plannedClosingShifts)
        assertFalse(stats.closingShifts.offered.met) // 2 < 4
        assertTrue(stats.closingShifts.actual.met)   // 1 >= 1
        assertTrue(stats.closingShifts.met)
    }
}
