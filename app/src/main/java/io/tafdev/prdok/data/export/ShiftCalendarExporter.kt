package io.tafdev.prdok.data.export

import io.tafdev.prdok.data.model.PragueTime
import io.tafdev.prdok.data.model.ShiftKind
import io.tafdev.prdok.data.shifts.ShiftRepository
import java.time.Instant
import java.time.YearMonth

/** What the export sheet learns before offering a button: how much is there to do. */
data class ExportInspection(val plannedShifts: Int, val alreadyExported: Int) {
    /** Once the calendar holds our events, the sensible action is a sync, not a second add. */
    val shouldOfferSync: Boolean get() = alreadyExported > 0
}

/**
 * Exports a month's planned shifts into a device calendar. Orchestrates the repository
 * (which shifts), [ExportPlanner] (which operations) and the [CalendarStore] (doing them).
 */
class ShiftCalendarExporter(
    private val shifts: ShiftRepository,
    private val store: CalendarStore,
) {
    suspend fun writableCalendars(): List<DeviceCalendar> = store.writableCalendars()

    suspend fun inspect(month: YearMonth, calendarId: Long): ExportInspection {
        val planned = plannedShifts(month)
        val existing = exportedEvents(month, calendarId)
        return ExportInspection(plannedShifts = planned.size, alreadyExported = existing.size)
    }

    suspend fun export(
        month: YearMonth,
        calendarId: Long,
        title: String,
        alarmMinutesBefore: Int?,
        mode: ExportMode,
    ): ExportSummary {
        val planned = plannedShifts(month)
        val existing = exportedEvents(month, calendarId)
        val operations = ExportPlanner.plan(planned, existing, mode)

        for (operation in operations) {
            when (operation) {
                is ExportOperation.Create ->
                    store.insert(calendarId, EventDraft(operation.shift, title, alarmMinutesBefore))
                is ExportOperation.Update ->
                    store.update(operation.eventId, EventDraft(operation.shift, title, alarmMinutesBefore))
                is ExportOperation.Delete -> store.delete(operation.eventId)
            }
        }
        return ExportPlanner.summarize(planned, operations)
    }

    private suspend fun plannedShifts(month: YearMonth) =
        shifts.shiftsForMonth(month).filter { it.kind == ShiftKind.PLANNED }

    /** Our events start on a shift's start, so "starts inside the month" finds them all. */
    private suspend fun exportedEvents(month: YearMonth, calendarId: Long): List<ExportedEvent> {
        val from: Instant = month.atDay(1).atStartOfDay(PragueTime.ZONE).toInstant()
        val to: Instant = month.plusMonths(1).atDay(1).atStartOfDay(PragueTime.ZONE).toInstant()
        return store.exportedEvents(calendarId, from, to)
    }
}
