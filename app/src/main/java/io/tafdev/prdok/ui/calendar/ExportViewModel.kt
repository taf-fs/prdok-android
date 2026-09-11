package io.tafdev.prdok.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.tafdev.prdok.data.api.PrdokApiException
import io.tafdev.prdok.data.export.CalendarAccessException
import io.tafdev.prdok.data.export.DeviceCalendar
import io.tafdev.prdok.data.export.ExportInspection
import io.tafdev.prdok.data.export.ExportMode
import io.tafdev.prdok.data.export.ExportSummary
import io.tafdev.prdok.data.export.ShiftCalendarExporter
import java.time.YearMonth
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ExportUiState(
    /** Null until the permission dialog has been answered. */
    val permissionGranted: Boolean? = null,
    val isLoadingCalendars: Boolean = false,
    val calendars: List<DeviceCalendar> = emptyList(),
    val selectedCalendarId: Long? = null,
    val isInspecting: Boolean = false,
    /** What the selected calendar already holds for the month; drives Add vs Synchronize. */
    val inspection: ExportInspection? = null,
    val isExporting: Boolean = false,
) {
    val selectedCalendar: DeviceCalendar? get() = calendars.firstOrNull { it.id == selectedCalendarId }
    val canExport: Boolean get() = selectedCalendar != null && !isExporting && !isInspecting && !isLoadingCalendars
}

sealed class ExportEvent {
    data class Finished(val mode: ExportMode, val summary: ExportSummary) : ExportEvent()
    data object AccessDenied : ExportEvent()
    data class Failed(val detail: String) : ExportEvent()

    val isError: Boolean get() = this !is Finished
}

/**
 * State behind the export sheet. Its own ViewModel rather than more fields on
 * [CalendarViewModel]: the calendar screen never needs to know which device
 * calendars exist, and a write in progress survives rotation here just the same.
 */
class ExportViewModel(private val exporter: ShiftCalendarExporter) : ViewModel() {

    private val _uiState = MutableStateFlow(ExportUiState())
    val uiState: StateFlow<ExportUiState> = _uiState.asStateFlow()

    private val _events = Channel<ExportEvent>(Channel.BUFFERED)
    val events: Flow<ExportEvent> = _events.receiveAsFlow()

    private var inspectJob: Job? = null

    /**
     * The sheet answers this once per opening, so it doubles as "the sheet opened for
     * [month]": the previous month's inspection is dropped, the calendar list is kept.
     */
    fun onPermissionResult(granted: Boolean, month: YearMonth) {
        inspectJob?.cancel()
        _uiState.update { it.copy(permissionGranted = granted, inspection = null, isInspecting = false) }
        if (granted) loadCalendars(month)
    }

    private fun loadCalendars(month: YearMonth) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingCalendars = true) }
            try {
                val calendars = exporter.writableCalendars()
                _uiState.update { state ->
                    val keep = state.selectedCalendarId?.takeIf { id -> calendars.any { it.id == id } }
                    state.copy(calendars = calendars, selectedCalendarId = keep ?: calendars.firstOrNull()?.id)
                }
                _uiState.value.selectedCalendarId?.let { inspect(it, month) }
            } catch (e: CalendarAccessException) {
                _uiState.update { it.copy(permissionGranted = false) }
            } finally {
                _uiState.update { it.copy(isLoadingCalendars = false) }
            }
        }
    }

    fun selectCalendar(calendarId: Long, month: YearMonth) {
        _uiState.update { it.copy(selectedCalendarId = calendarId) }
        inspect(calendarId, month)
    }

    private fun inspect(calendarId: Long, month: YearMonth) {
        inspectJob?.cancel()
        inspectJob = viewModelScope.launch {
            _uiState.update { it.copy(isInspecting = true, inspection = null) }
            try {
                val inspection = exporter.inspect(month, calendarId)
                _uiState.update { it.copy(inspection = inspection) }
            } catch (e: PrdokApiException) {
                _events.send(ExportEvent.Failed(e.message ?: "Unknown error"))
            } catch (e: CalendarAccessException) {
                _events.send(ExportEvent.AccessDenied)
            } finally {
                _uiState.update { it.copy(isInspecting = false) }
            }
        }
    }

    fun export(month: YearMonth, title: String, alarmMinutesBefore: Int?) {
        val state = _uiState.value
        val calendar = state.selectedCalendar ?: return
        if (!state.canExport) return
        val mode = if (state.inspection?.shouldOfferSync == true) ExportMode.SYNC else ExportMode.ADD_ONLY

        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true) }
            val event = try {
                val summary = exporter.export(month, calendar.id, title.trim().ifEmpty { DEFAULT_TITLE }, alarmMinutesBefore, mode)
                ExportEvent.Finished(mode, summary)
            } catch (e: PrdokApiException) {
                ExportEvent.Failed(e.message ?: "Unknown error")
            } catch (e: CalendarAccessException) {
                ExportEvent.AccessDenied
            }
            _uiState.update { it.copy(isExporting = false) }
            _events.send(event)
        }
    }

    private companion object {
        /** Last resort when the user blanks the title field; the sheet normally supplies a localized one. */
        const val DEFAULT_TITLE = "Směna"
    }
}
