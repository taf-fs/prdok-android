package io.tafdev.prdok.data.export

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import android.provider.CalendarContract.Reminders
import io.tafdev.prdok.data.model.PragueTime
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The real [CalendarStore]: talks to the system calendar provider through
 * `CalendarContract`. Every method needs READ_CALENDAR/WRITE_CALENDAR to have been
 * granted first; the provider throws `SecurityException` otherwise, surfaced here
 * as [CalendarAccessException].
 *
 * A ContentResolver is a cursor-based database API, so this is the one file in the
 * app that reads columns by name and writes `ContentValues` rows.
 */
class ContentResolverCalendarStore(private val resolver: ContentResolver) : CalendarStore {

    override suspend fun writableCalendars(): List<DeviceCalendar> = io {
        val projection = arrayOf(
            Calendars._ID,
            Calendars.CALENDAR_DISPLAY_NAME,
            Calendars.ACCOUNT_NAME,
        )
        // Contributor access and above may create events; below that the calendar is read-only.
        val selection = "${Calendars.CALENDAR_ACCESS_LEVEL} >= ? AND ${Calendars.VISIBLE} = 1"
        val args = arrayOf(Calendars.CAL_ACCESS_CONTRIBUTOR.toString())
        val calendars = mutableListOf<DeviceCalendar>()
        resolver.query(Calendars.CONTENT_URI, projection, selection, args, Calendars.CALENDAR_DISPLAY_NAME)
            ?.use { cursor ->
                while (cursor.moveToNext()) {
                    calendars += DeviceCalendar(
                        id = cursor.getLong(0),
                        name = cursor.getString(1).orEmpty(),
                        account = cursor.getString(2)?.takeIf { it.isNotBlank() },
                    )
                }
            }
        calendars
    }

    override suspend fun exportedEvents(calendarId: Long, from: Instant, to: Instant): List<ExportedEvent> = io {
        val projection = arrayOf(Events._ID, Events.DESCRIPTION)
        val selection = "${Events.CALENDAR_ID} = ? AND ${Events.DTSTART} >= ? AND ${Events.DTSTART} < ?" +
            " AND ${Events.DELETED} = 0 AND ${Events.DESCRIPTION} LIKE ?"
        val args = arrayOf(
            calendarId.toString(),
            from.toEpochMilli().toString(),
            to.toEpochMilli().toString(),
            "%${CalendarStore.MARKER_PREFIX}%",
        )
        val events = mutableListOf<ExportedEvent>()
        resolver.query(Events.CONTENT_URI, projection, selection, args, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                val shiftId = CalendarStore.shiftIdFrom(cursor.getString(1)) ?: continue
                events += ExportedEvent(eventId = cursor.getLong(0), shiftId = shiftId)
            }
        }
        events
    }

    override suspend fun insert(calendarId: Long, draft: EventDraft): Long = io {
        val values = eventValues(draft).apply { put(Events.CALENDAR_ID, calendarId) }
        val uri = resolver.insert(Events.CONTENT_URI, values)
            ?: throw CalendarAccessException("The calendar provider refused the event")
        val eventId = ContentUris.parseId(uri)
        writeReminder(eventId, draft.alarmMinutesBefore)
        eventId
    }

    override suspend fun update(eventId: Long, draft: EventDraft): Unit = io {
        resolver.update(eventUri(eventId), eventValues(draft), null, null)
        // Reminders are their own table: clear ours and write the current choice.
        resolver.delete(Reminders.CONTENT_URI, "${Reminders.EVENT_ID} = ?", arrayOf(eventId.toString()))
        writeReminder(eventId, draft.alarmMinutesBefore)
    }

    override suspend fun delete(eventId: Long): Unit = io {
        // Reminders cascade with the event; nothing else to clean up.
        resolver.delete(eventUri(eventId), null, null)
    }

    private fun eventValues(draft: EventDraft) = ContentValues().apply {
        put(Events.DTSTART, draft.shift.start.toInstant().toEpochMilli())
        put(Events.DTEND, draft.shift.end.toInstant().toEpochMilli())
        put(Events.EVENT_TIMEZONE, PragueTime.ZONE.id)
        put(Events.TITLE, draft.title)
        put(Events.DESCRIPTION, CalendarStore.description(draft.shift.id))
        put(Events.HAS_ALARM, if (draft.alarmMinutesBefore != null) 1 else 0)
    }

    private fun writeReminder(eventId: Long, minutesBefore: Int?) {
        if (minutesBefore == null) return
        val values = ContentValues().apply {
            put(Reminders.EVENT_ID, eventId)
            put(Reminders.MINUTES, minutesBefore)
            put(Reminders.METHOD, Reminders.METHOD_ALERT)
        }
        resolver.insert(Reminders.CONTENT_URI, values)
    }

    private fun eventUri(eventId: Long) = ContentUris.withAppendedId(Events.CONTENT_URI, eventId)

    /** Provider calls block, so they run on the IO dispatcher; a permission refusal becomes our exception. */
    private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) {
        try {
            block()
        } catch (e: SecurityException) {
            throw CalendarAccessException("Calendar access denied", e)
        }
    }
}
