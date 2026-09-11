package io.tafdev.prdok.data.export

import io.tafdev.prdok.data.model.Shift
import java.time.Instant

/** A calendar on the device the user is allowed to write into. */
data class DeviceCalendar(
    val id: Long,
    val name: String,
    /** The Google/Exchange/local account it belongs to; helps tell two "Personal" calendars apart. */
    val account: String?,
)

/** An event this app wrote earlier, recognised by the shift-id marker in its description. */
data class ExportedEvent(val eventId: Long, val shiftId: Int)

/** Everything a calendar event needs to be written or rewritten from a shift. */
data class EventDraft(
    val shift: Shift,
    val title: String,
    /** `null` means no reminder. */
    val alarmMinutesBefore: Int?,
)

/** Thrown when the device refuses calendar access (permission missing or revoked). */
class CalendarAccessException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * The device calendar, reduced to the five things the exporter needs. An interface so the
 * exporter can be driven by a fake in unit tests, where there is no ContentResolver.
 */
interface CalendarStore {
    suspend fun writableCalendars(): List<DeviceCalendar>

    /** Events in [calendarId] starting in `[from, to)` that carry this app's marker. */
    suspend fun exportedEvents(calendarId: Long, from: Instant, to: Instant): List<ExportedEvent>

    /** Returns the new event's id. */
    suspend fun insert(calendarId: Long, draft: EventDraft): Long
    suspend fun update(eventId: Long, draft: EventDraft)
    suspend fun delete(eventId: Long)

    companion object {
        /**
         * Prefix stored in the event description so our events can be told apart from the
         * user's own. Same key as the iOS app, so both write compatible events.
         */
        const val MARKER_PREFIX = "prdokShiftId="

        fun description(shiftId: Int): String = MARKER_PREFIX + shiftId

        /** The shift id from a marked description, or null when the marker is missing. */
        fun shiftIdFrom(description: String?): Int? {
            val start = description?.indexOf(MARKER_PREFIX) ?: return null
            if (start < 0) return null
            return description.substring(start + MARKER_PREFIX.length).takeWhile { it.isDigit() }.toIntOrNull()
        }
    }
}
