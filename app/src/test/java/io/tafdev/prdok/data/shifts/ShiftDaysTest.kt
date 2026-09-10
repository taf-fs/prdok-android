package io.tafdev.prdok.data.shifts

import io.tafdev.prdok.data.model.PragueTime
import io.tafdev.prdok.data.model.Shift
import io.tafdev.prdok.data.model.ShiftKind
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class ShiftDaysTest {

    private fun shift(id: Int, kind: ShiftKind, date: String, startHour: Int = 16): Shift {
        val start = LocalDate.parse(date).atTime(startHour, 0).atZone(PragueTime.ZONE)
        return Shift(id, kind, start, start.plusHours(8))
    }

    @Test
    fun `dates are keyed by the start day even when the shift crosses midnight`() {
        val days = ShiftDays.of(listOf(shift(1, ShiftKind.PLANNED, "2026-09-05", startHour = 20)))
        assertEquals(setOf(LocalDate.parse("2026-09-05")), days.planned)
        assertEquals(DayDot.SOLID, days.dotFor(LocalDate.parse("2026-09-05")))
        assertEquals(DayDot.NONE, days.dotFor(LocalDate.parse("2026-09-06")))
    }

    @Test
    fun `offered-only days are faint, planned or actual are solid`() {
        val days = ShiftDays.of(
            listOf(
                shift(1, ShiftKind.OFFERED, "2026-09-01"),
                shift(2, ShiftKind.OFFERED, "2026-09-02"),
                shift(3, ShiftKind.PLANNED, "2026-09-02"), // planned wins over offered
                shift(4, ShiftKind.ACTUAL, "2026-09-03"),
            )
        )
        assertEquals(DayDot.FAINT, days.dotFor(LocalDate.parse("2026-09-01")))
        assertEquals(DayDot.SOLID, days.dotFor(LocalDate.parse("2026-09-02")))
        assertEquals(DayDot.SOLID, days.dotFor(LocalDate.parse("2026-09-03")))
        assertEquals(DayDot.NONE, days.dotFor(LocalDate.parse("2026-09-04")))
    }

    @Test
    fun `empty input yields empty sets`() {
        assertEquals(ShiftDays(), ShiftDays.of(emptyList()))
    }
}
