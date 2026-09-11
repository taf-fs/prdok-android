package io.tafdev.prdok.data.export

import io.tafdev.prdok.data.model.PragueTime
import io.tafdev.prdok.data.model.Shift
import io.tafdev.prdok.data.model.ShiftKind
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class ExportPlannerTest {

    private fun shift(id: Int, day: Int) = Shift(
        id = id,
        kind = ShiftKind.PLANNED,
        start = ZonedDateTime.of(2026, 9, day, 16, 0, 0, 0, PragueTime.ZONE),
        end = ZonedDateTime.of(2026, 9, day, 23, 0, 0, 0, PragueTime.ZONE),
    )

    private val planned = listOf(shift(3, 20), shift(1, 5), shift(2, 12))

    @Test
    fun `add-only creates the missing shifts in date order and skips exported ones`() {
        val existing = listOf(ExportedEvent(eventId = 900, shiftId = 2))

        val ops = ExportPlanner.plan(planned, existing, ExportMode.ADD_ONLY)

        assertEquals(listOf(ExportOperation.Create(shift(1, 5)), ExportOperation.Create(shift(3, 20))), ops)
        assertEquals(ExportSummary(created = 2, updated = 0, deleted = 0, skipped = 1), ExportPlanner.summarize(planned, ops))
    }

    @Test
    fun `sync rewrites existing, deletes orphans and creates the rest`() {
        val existing = listOf(
            ExportedEvent(eventId = 900, shiftId = 2),
            ExportedEvent(eventId = 901, shiftId = 77), // no longer planned
        )

        val ops = ExportPlanner.plan(planned, existing, ExportMode.SYNC)

        assertEquals(
            listOf(
                ExportOperation.Delete(901),
                ExportOperation.Create(shift(1, 5)),
                ExportOperation.Update(900, shift(2, 12)),
                ExportOperation.Create(shift(3, 20)),
            ),
            ops,
        )
        assertEquals(ExportSummary(created = 2, updated = 1, deleted = 1, skipped = 0), ExportPlanner.summarize(planned, ops))
    }

    @Test
    fun `nothing planned and nothing exported is a no-op in both modes`() {
        for (mode in ExportMode.entries) {
            assertEquals(emptyList<ExportOperation>(), ExportPlanner.plan(emptyList(), emptyList(), mode))
        }
    }

    @Test
    fun `marker round-trips through the description`() {
        assertEquals("prdokShiftId=4242", CalendarStore.description(4242))
        assertEquals(4242, CalendarStore.shiftIdFrom("prdokShiftId=4242"))
        assertEquals(17, CalendarStore.shiftIdFrom("some note\nprdokShiftId=17 and more"))
        assertEquals(null, CalendarStore.shiftIdFrom("the user's own event"))
        assertEquals(null, CalendarStore.shiftIdFrom(null))
    }
}
