package io.tafdev.prdok.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.tafdev.prdok.data.api.PrdokApiException
import io.tafdev.prdok.data.model.PragueTime
import io.tafdev.prdok.data.model.Shift
import io.tafdev.prdok.data.shifts.ShiftRepository
import io.tafdev.prdok.data.shifts.TodayOverview
import java.time.YearMonth
import java.time.ZonedDateTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Load status of the shift data behind the screen. */
private sealed class ShiftsLoad {
    data object Loading : ShiftsLoad()
    data class Loaded(val shifts: List<Shift>) : ShiftsLoad()
    data class Failed(val message: String) : ShiftsLoad()
}

data class TodayUiState(
    val now: ZonedDateTime,
    val isLoading: Boolean = false,
    val overview: TodayOverview? = null,
    val errorMessage: String? = null,
)

class TodayViewModel(private val repository: ShiftRepository) : ViewModel() {

    private val shifts = MutableStateFlow<ShiftsLoad>(ShiftsLoad.Loading)

    /**
     * Emits the current time once per minute, aligned to minute boundaries so the
     * countdown ticks exactly when the wall clock does. It's a cold flow: it only
     * runs while something collects [uiState], and stops when the screen goes away.
     */
    private val ticker: Flow<ZonedDateTime> = flow {
        while (true) {
            val now = ZonedDateTime.now(PragueTime.ZONE)
            emit(now)
            delay(millisUntilNextMinute(now))
        }
    }

    val uiState: StateFlow<TodayUiState> = combine(shifts, ticker) { load, now ->
        when (load) {
            ShiftsLoad.Loading -> TodayUiState(now = now, isLoading = true)
            is ShiftsLoad.Loaded -> TodayUiState(now = now, overview = TodayOverview.compute(load.shifts, now))
            is ShiftsLoad.Failed -> TodayUiState(now = now, errorMessage = load.message)
        }
    }.stateIn(
        scope = viewModelScope,
        // Keep computing for 5 s after the last collector leaves, so a rotation
        // doesn't restart the ticker; stop entirely when the tab is really gone.
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = TodayUiState(now = ZonedDateTime.now(PragueTime.ZONE), isLoading = true),
    )

    init {
        load()
    }

    fun refresh() = load()

    private fun load() {
        viewModelScope.launch {
            shifts.value = ShiftsLoad.Loading
            val thisMonth = YearMonth.now(PragueTime.ZONE)
            shifts.value = try {
                ShiftsLoad.Loaded(repository.shiftsForMonths(listOf(thisMonth, thisMonth.plusMonths(1))))
            } catch (e: PrdokApiException) {
                ShiftsLoad.Failed(e.message ?: "Unknown error")
            }
        }
    }

    private fun millisUntilNextMinute(now: ZonedDateTime): Long {
        val millisIntoMinute = now.toInstant().toEpochMilli() % 60_000
        return 60_000 - millisIntoMinute
    }
}
