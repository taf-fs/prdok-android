package io.tafdev.prdok.data.shifts

import io.tafdev.prdok.data.bonus.BonusCondition
import io.tafdev.prdok.data.bonus.BonusEither
import io.tafdev.prdok.data.bonus.BonusStructure
import io.tafdev.prdok.data.model.PragueTime
import io.tafdev.prdok.data.model.Shift
import io.tafdev.prdok.data.model.ShiftKind
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
        // coef = 26/30 = 0.8667, applied to the offered sides only — the worked ones are
        // payroll's to count, so there is no local limit to scale.
        assertEquals(16, stats.weekendHours.required)          // 15.6
        assertEquals(10, stats.closingShifts.offered.required) // 10.4
        assertEquals(87, stats.totalHours.offered.required)    // 86.7
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
        // Three offers of 8 h 57 min: 26.85 h total -> 27, not 3 x 8 = 24.
        val shifts = listOf(
            offered("2026-09-01", "16:01", "00:58"),
            offered("2026-09-02", "16:01", "00:58"),
            offered("2026-09-03", "16:01", "00:58"),
        )
        assertEquals(27, MonthStatistics.compute(shifts, september, 30).totalHours.offered.value)
    }

    @Test
    fun `without a payload the offered side carries the row on its own`() {
        val offeredOnly = (1..13).map { offered("2026-09-%02d".format(it), "15:00", "23:00") } // 104 h
        assertTrue(MonthStatistics.compute(offeredOnly, september, 30).totalHours.met)

        // Nine worked shifts would clear the old 72 h line, but locally the worked side is
        // unknown, so the row rests on the offered one.
        val actualOnly = (1..9).map { actual("2026-09-%02d".format(it), "15:00", "23:00") } // 72 h
        val stats = MonthStatistics.compute(actualOnly, september, 30)
        assertFalse(stats.totalHours.offered.met)
        assertNull(stats.totalHours.actual)
        assertFalse(stats.totalHours.met)
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
        val stats = MonthStatistics.compute(shifts, september, 10) // tiny coef: the limit is 4
        assertEquals(2, stats.closingShifts.offered.value)
        assertEquals(1, stats.plannedClosingShifts)
        assertFalse(stats.closingShifts.offered.met) // 2 < 4
        assertNull(stats.closingShifts.actual)
        assertFalse(stats.closingShifts.met)
    }

    // -- the payload owns rows 1-3 -------------------------------------------

    /**
     * March 2026 as payroll scored it. Its worked numbers are the ones the local rules
     * get wrong: 42 hours against a gross 44, and 2 closings where the shift rows read 0.
     */
    private val march = BonusStructure(
        score = 5,
        maxScore = 6,
        jokers = 1,
        jokersUsed = 1,
        earned = true,
        bonusCzkPerHour = 20,
        monthState = 1,
        weekendHours = BonusCondition(met = true, value = 28, required = 19),
        closingShifts = BonusEither(
            met = true,
            offered = BonusCondition(met = true, value = 12, required = 12),
            worked = BonusCondition(met = false, value = 2, required = 4),
        ),
        hours = BonusEither(
            met = true,
            offered = BonusCondition(met = true, value = 140, required = 103),
            worked = BonusCondition(met = false, value = 42, required = 74),
        ),
        meeting = BonusCondition(met = false),
        meetingNote = "",
        earlyOffers = BonusCondition(met = true, value = 104, required = 20),
        earlyOffersDeadline = null,
        competencies = BonusCondition(met = true, value = 6),
        competencyGained = null,
    )

    /** One offer, so the local computation would produce visibly different numbers. */
    private val oneOffer = listOf(offered("2026-09-07", "16:00", "02:00"))

    @Test
    fun `values, limits and verdicts come from the payload when there is one`() {
        val stats = MonthStatistics.compute(oneOffer, september, openDays = 30, bonus = march)

        assertEquals(Requirement(28, 19, met = true), stats.weekendHours)
        assertEquals(Requirement(12, 12, met = true), stats.closingShifts.offered)
        assertEquals(Requirement(2, 4, met = false), stats.closingShifts.actual)
        assertEquals(Requirement(140, 103, met = true), stats.totalHours.offered)
        assertEquals(Requirement(42, 74, met = false), stats.totalHours.actual)
    }

    /** A row can be met while the side shown next to it is not — the flag decides. */
    @Test
    fun `the row's verdict is the server's, not the two sides re-read`() {
        val missedRow = march.copy(
            closingShifts = march.closingShifts.copy(
                met = false,
                offered = BonusCondition(met = true, value = 12, required = 12),
            )
        )
        val stats = MonthStatistics.compute(oneOffer, september, 30, missedRow)
        assertTrue(stats.closingShifts.offered.met)
        assertFalse(stats.closingShifts.met)
    }

    @Test
    fun `the planned-closings note stays local either way`() {
        val shifts = oneOffer + planned("2026-09-10", "16:00", "02:00")
        assertEquals(1, MonthStatistics.compute(shifts, september, 30, march).plannedClosingShifts)
        assertEquals(1, MonthStatistics.compute(shifts, september, 30).plannedClosingShifts)
    }

    @Test
    fun `without a payload the rows fall back to the local computation`() {
        val stats = MonthStatistics.compute(oneOffer, september, openDays = 30)
        assertEquals(Requirement(1, 12), stats.closingShifts.offered)
        assertEquals(Requirement(10, 100), stats.totalHours.offered)
        assertNull(stats.closingShifts.actual)
        assertNull(stats.totalHours.actual)
    }
}
