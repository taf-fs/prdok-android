package io.tafdev.prdok.data.export

import io.tafdev.prdok.data.api.PrdokApi
import io.tafdev.prdok.data.pairing.FakePairingStore
import io.tafdev.prdok.data.pairing.Pairing
import io.tafdev.prdok.data.shifts.ShiftRepository
import java.io.File
import java.time.Instant
import java.time.YearMonth
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** An in-memory calendar: just enough to see what the exporter asked it to do. */
private class FakeCalendarStore : CalendarStore {
    val events = mutableMapOf<Long, EventDraft>()
    private var nextId = 100L

    override suspend fun writableCalendars() = listOf(DeviceCalendar(1, "Personal", null))

    override suspend fun exportedEvents(calendarId: Long, from: Instant, to: Instant): List<ExportedEvent> =
        events.map { (id, draft) -> ExportedEvent(id, draft.shift.id) }

    override suspend fun insert(calendarId: Long, draft: EventDraft): Long {
        val id = nextId++
        events[id] = draft
        return id
    }

    override suspend fun update(eventId: Long, draft: EventDraft) {
        events[eventId] = draft
    }

    override suspend fun delete(eventId: Long) {
        events.remove(eventId)
    }
}

class ShiftCalendarExporterTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var server: MockWebServer
    private lateinit var store: FakeCalendarStore
    private lateinit var exporter: ShiftCalendarExporter
    private val september = YearMonth.of(2026, 9)

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        store = FakeCalendarStore()
        val repository = ShiftRepository(
            PrdokApi(server.url("/").toString().trimEnd('/')),
            FakePairingStore(Pairing(klic = "7abc", id = "99", ids = "s", provoz = "cp", skladnik = null)),
            File(tmp.root, "shifts"),
        )
        exporter = ShiftCalendarExporter(repository, store)
    }

    @After
    fun tearDown() = server.shutdown()

    /** Two planned shifts and one offered one, which must not be exported. */
    private fun monthResponse() = MockResponse().setBody(
        """{"ulozsi":[],"err":[],"smeny":{"dochazka":[],
           "moznosti":[{"id":"9","kdy":"2026-09-19","od":"12:00:00","do":"20:00:00"}],
           "plan":[{"id":"1","kdy":"2026-09-05","od":"16:00:00","do":"23:00:00"},
                   {"id":"2","kdy":"2026-09-12","od":"16:00:00","do":"01:00:00"}]}}"""
    )

    @Test
    fun `exports only planned shifts with the title and alarm`() = runBlocking {
        server.enqueue(monthResponse())

        val summary = exporter.export(september, calendarId = 1, title = "Shift", alarmMinutesBefore = 30, mode = ExportMode.ADD_ONLY)

        assertEquals(ExportSummary(created = 2, updated = 0, deleted = 0, skipped = 0), summary)
        assertEquals(setOf(1, 2), store.events.values.map { it.shift.id }.toSet())
        assertEquals(setOf("Shift"), store.events.values.map { it.title }.toSet())
        assertEquals(setOf(30), store.events.values.map { it.alarmMinutesBefore }.toSet())
    }

    @Test
    fun `second add-only run skips what is already there and inspect reports it`() = runBlocking {
        server.enqueue(monthResponse())
        exporter.export(september, 1, "Shift", null, ExportMode.ADD_ONLY)

        // The month is cached now: no second network call needed.
        val inspection = exporter.inspect(september, 1)
        assertEquals(ExportInspection(plannedShifts = 2, alreadyExported = 2), inspection)
        assertEquals(true, inspection.shouldOfferSync)

        val again = exporter.export(september, 1, "Shift", null, ExportMode.ADD_ONLY)
        assertEquals(ExportSummary(created = 0, updated = 0, deleted = 0, skipped = 2), again)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `sync rewrites the title and removes events whose shift disappeared`() = runBlocking {
        server.enqueue(monthResponse())
        exporter.export(september, 1, "Old title", 30, ExportMode.ADD_ONLY)
        // Simulate an event left over from a shift the roster no longer has.
        store.insert(1, EventDraft(store.events.values.first().shift.copy(id = 555), "Old title", null))

        val summary = exporter.export(september, 1, "New title", null, ExportMode.SYNC)

        assertEquals(ExportSummary(created = 0, updated = 2, deleted = 1, skipped = 0), summary)
        assertEquals(setOf("New title"), store.events.values.map { it.title }.toSet())
        assertEquals(setOf<Int?>(null), store.events.values.map { it.alarmMinutesBefore }.toSet())
    }
}
