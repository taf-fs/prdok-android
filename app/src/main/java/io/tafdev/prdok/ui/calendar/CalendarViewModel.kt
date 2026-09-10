package io.tafdev.prdok.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.tafdev.prdok.data.api.OfferOutcome
import io.tafdev.prdok.data.api.PrdokApiException
import io.tafdev.prdok.data.api.RemoveOfferOutcome
import io.tafdev.prdok.data.model.PragueTime
import io.tafdev.prdok.data.model.Shift
import io.tafdev.prdok.data.shifts.MonthStatistics
import io.tafdev.prdok.data.shifts.OpenDaysRepository
import io.tafdev.prdok.data.shifts.ShiftDays
import io.tafdev.prdok.data.shifts.ShiftRepository
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CalendarUiState(
    val today: LocalDate,
    val displayedMonth: YearMonth,
    val selectedDate: LocalDate,
    /** Dot sets for the displayed year. */
    val shiftDays: ShiftDays = ShiftDays(),
    /** All shifts of [displayedMonth]; feeds the day sheet and the statistics. */
    val monthShifts: List<Shift> = emptyList(),
    val statistics: MonthStatistics? = null,
    val isMonthLoading: Boolean = false,
    /** The refresh button is running: grid and month buttons are disabled. */
    val isRefreshing: Boolean = false,
    /** An offer/removal request is in flight (day sheet). */
    val isSubmitting: Boolean = false,
) {
    fun shiftsOn(date: LocalDate): List<Shift> =
        monthShifts.filter { it.start.toLocalDate() == date }.sortedBy { it.start }
}

/**
 * One-shot things the screen reacts to (toast, haptic, closing the sheet) — as opposed
 * to [CalendarUiState], which describes what is on screen right now. Keeping them apart
 * avoids the classic "toast shows again after rotation" bug.
 */
sealed class CalendarEvent {
    data object Refreshed : CalendarEvent()
    data object OfferSaved : CalendarEvent()
    data object OfferRemoved : CalendarEvent()
    data class LoadFailed(val detail: String) : CalendarEvent()
    data class RefreshFailed(val detail: String) : CalendarEvent()
    data class RequestFailed(val detail: String) : CalendarEvent()

    /** The server refused the offer (facility freeze date); [serverMessage] explains why. */
    data class OfferRejected(val serverMessage: String) : CalendarEvent()
    data object OfferNotFound : CalendarEvent()
    data class UnexpectedResponse(val serverMessage: String) : CalendarEvent()

    val isError: Boolean get() = this !is Refreshed && this !is OfferSaved && this !is OfferRemoved
}

class CalendarViewModel(
    private val shifts: ShiftRepository,
    private val openDays: OpenDaysRepository,
) : ViewModel() {

    private val today: LocalDate = LocalDate.now(PragueTime.ZONE)

    private val _uiState = MutableStateFlow(
        CalendarUiState(today = today, displayedMonth = YearMonth.from(today), selectedDate = today)
    )
    val uiState: StateFlow<CalendarUiState> = _uiState.asStateFlow()

    // A Channel is a queue: events wait here until the screen collects them,
    // and each one is delivered exactly once.
    private val _events = Channel<CalendarEvent>(Channel.BUFFERED)
    val events: Flow<CalendarEvent> = _events.receiveAsFlow()

    private var monthJob: Job? = null
    private var yearJob: Job? = null

    /** The month whose load was last started, so paging back and forth doesn't reload. */
    private var requestedMonth: YearMonth? = null
    private var requestedYear: Int? = null

    /** Called by the grid whenever the settled month changes (swipe, buttons, initial jump). */
    fun showMonth(month: YearMonth) {
        _uiState.update { it.copy(displayedMonth = month) }
        if (month == requestedMonth) return
        requestedMonth = month

        monthJob?.cancel()
        monthJob = viewModelScope.launch {
            try {
                loadMonthNow(month, force = false)
            } catch (e: PrdokApiException) {
                requestedMonth = null // so coming back to this month tries again
                _events.send(CalendarEvent.LoadFailed(e.message ?: "Unknown error"))
            }
        }
        ensureYear(month.year)
    }

    fun selectDate(date: LocalDate) {
        _uiState.update { it.copy(selectedDate = date) }
    }

    /** Refresh button: force-refetch the displayed month, then rebuild the year's dots. */
    fun refresh() {
        if (_uiState.value.isRefreshing) return
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            // Runs alongside the network work; joined at the end so a fast server
            // can't make the spinner blink.
            val minimumSpin = launch { delay(MIN_REFRESH_MILLIS) }
            val month = _uiState.value.displayedMonth
            val failure = try {
                loadMonthNow(month, force = true)
                loadYearNow(month.year)
                null
            } catch (e: PrdokApiException) {
                e.message ?: "Unknown error"
            }
            minimumSpin.join()
            _uiState.update { it.copy(isRefreshing = false) }
            _events.send(if (failure == null) CalendarEvent.Refreshed else CalendarEvent.RefreshFailed(failure))
        }
    }

    fun offer(date: LocalDate, startHour: Int, endHour: Int) {
        submit {
            when (val outcome = shifts.offerShift(date, startHour, endHour)) {
                OfferOutcome.Saved -> CalendarEvent.OfferSaved
                is OfferOutcome.Rejected -> CalendarEvent.OfferRejected(outcome.serverMessage)
                is OfferOutcome.Unexpected -> CalendarEvent.UnexpectedResponse(outcome.serverMessage)
            }
        }
    }

    fun removeOffer(shiftId: Int) {
        submit {
            when (val outcome = shifts.removeOffer(shiftId)) {
                RemoveOfferOutcome.Removed -> CalendarEvent.OfferRemoved
                RemoveOfferOutcome.NotFound -> CalendarEvent.OfferNotFound
                is RemoveOfferOutcome.Unexpected -> CalendarEvent.UnexpectedResponse(outcome.serverMessage)
            }
        }
    }

    /**
     * Shared shape of both mutations: flag the request, report its outcome, and after a
     * success re-read the month from the server so dots and statistics reflect the change.
     */
    private fun submit(request: suspend () -> CalendarEvent) {
        if (_uiState.value.isSubmitting) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true) }
            val event = try {
                request()
            } catch (e: PrdokApiException) {
                CalendarEvent.RequestFailed(e.message ?: "Unknown error")
            }
            _uiState.update { it.copy(isSubmitting = false) }
            _events.send(event)

            if (!event.isError) {
                val month = _uiState.value.displayedMonth
                try {
                    loadMonthNow(month, force = true)
                    loadYearNow(month.year)
                } catch (e: PrdokApiException) {
                    _events.send(CalendarEvent.LoadFailed(e.message ?: "Unknown error"))
                }
            }
        }
    }

    /**
     * Loads the month's shifts and its open-day count side by side. Only the shifts can
     * fail the load; a missing open-day count just means the statistics use the
     * calendar day count instead.
     */
    private suspend fun loadMonthNow(month: YearMonth, force: Boolean) = coroutineScope {
        _uiState.update { it.copy(isMonthLoading = true) }
        try {
            val shiftsDeferred = async { shifts.shiftsForMonth(month, force) }
            val openDaysDeferred = async {
                // Not runCatching: that would also swallow the coroutine's own cancellation.
                try {
                    openDays.openDays(month, force)
                } catch (e: PrdokApiException) {
                    null
                }
            }
            val list = shiftsDeferred.await()
            val days = openDaysDeferred.await()
            _uiState.update { state ->
                // The user may have paged on while we were loading; don't show a stale month.
                if (state.displayedMonth != month) return@update state
                state.copy(monthShifts = list, statistics = MonthStatistics.compute(list, month, days))
            }
        } finally {
            _uiState.update { it.copy(isMonthLoading = false) }
        }
    }

    private suspend fun loadYearNow(year: Int) {
        val all = shifts.shiftsForYear(year)
        _uiState.update { it.copy(shiftDays = ShiftDays.of(all)) }
    }

    /**
     * Rebuilds the dot sets when the displayed year changes. Runs in its own job so its
     * up-to-twelve fetches never hold up the month load; failures are silent here
     * because the month load already reports network trouble.
     */
    private fun ensureYear(year: Int) {
        if (year == requestedYear) return
        requestedYear = year
        yearJob?.cancel()
        yearJob = viewModelScope.launch {
            try {
                loadYearNow(year)
            } catch (e: PrdokApiException) {
                requestedYear = null
            }
        }
    }

    private companion object {
        const val MIN_REFRESH_MILLIS = 350L
    }
}
