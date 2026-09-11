package io.tafdev.prdok.data.export

import io.tafdev.prdok.data.model.Shift

enum class ExportMode {
    /** Create events for shifts that have none; leave everything already there alone. */
    ADD_ONLY,

    /** Also rewrite events whose shift still exists and delete events whose shift is gone. */
    SYNC,
}

sealed class ExportOperation {
    data class Create(val shift: Shift) : ExportOperation()
    data class Update(val eventId: Long, val shift: Shift) : ExportOperation()
    data class Delete(val eventId: Long) : ExportOperation()
}

data class ExportSummary(val created: Int, val updated: Int, val deleted: Int, val skipped: Int)

/**
 * Decides what to do to a calendar without touching one: a pure function from
 * "what is planned" and "what is already there" to a list of operations.
 * The exporter then carries the operations out, in order.
 */
object ExportPlanner {

    fun plan(planned: List<Shift>, existing: List<ExportedEvent>, mode: ExportMode): List<ExportOperation> {
        val eventByShiftId = existing.associate { it.shiftId to it.eventId }
        val plannedIds = planned.map { it.id }.toSet()
        val operations = mutableListOf<ExportOperation>()

        if (mode == ExportMode.SYNC) {
            for (event in existing) {
                if (event.shiftId !in plannedIds) operations += ExportOperation.Delete(event.eventId)
            }
        }
        for (shift in planned.sortedBy { it.start }) {
            val eventId = eventByShiftId[shift.id]
            when {
                eventId == null -> operations += ExportOperation.Create(shift)
                mode == ExportMode.SYNC -> operations += ExportOperation.Update(eventId, shift)
                // ADD_ONLY and already exported: skipped, which the summary counts below.
            }
        }
        return operations
    }

    fun summarize(planned: List<Shift>, operations: List<ExportOperation>): ExportSummary {
        val created = operations.count { it is ExportOperation.Create }
        val updated = operations.count { it is ExportOperation.Update }
        return ExportSummary(
            created = created,
            updated = updated,
            deleted = operations.count { it is ExportOperation.Delete },
            skipped = planned.size - created - updated,
        )
    }
}
