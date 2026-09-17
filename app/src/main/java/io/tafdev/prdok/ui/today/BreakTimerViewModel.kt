package io.tafdev.prdok.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.tafdev.prdok.data.breaktimer.ActiveBreak
import io.tafdev.prdok.data.breaktimer.BreakTimer
import io.tafdev.prdok.data.breaktimer.BreakTimerManager
import io.tafdev.prdok.data.settings.SettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * [loaded] is false until the stored break has been read. Without it the first frame would
 * show both buttons and then animate a running timer in, as if the user had just started it.
 *
 * [justStarted] is true from the tap that starts [active] until the tile has announced it.
 * It lives only in memory, so reopening the app never counts as a fresh start.
 */
data class BreakTimerUiState(
    val loaded: Boolean = false,
    val active: ActiveBreak? = null,
    val justStarted: Boolean = false,
)

class BreakTimerViewModel(
    private val manager: BreakTimerManager,
    private val settingsStore: SettingsStore,
) : ViewModel() {

    /**
     * Two sources write here: the store, whenever it changes, and [start]/[cancel] directly.
     * The direct write lets the tiles move on the very frame of the tap; waiting for the store
     * would hold the animation back until the file had been written and read back. The store
     * then confirms the same value, which changes nothing on screen.
     */
    private val _uiState = MutableStateFlow(BreakTimerUiState())
    val uiState: StateFlow<BreakTimerUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            // A break that ran out while the app was closed is cleared from storage first.
            manager.reconcile()
            manager.active.collect { active ->
                _uiState.update { current ->
                    // The store confirming the break that was just tapped must not end its announcement.
                    BreakTimerUiState(loaded = true, active = active, justStarted = current.justStarted && current.active == active)
                }
            }
        }
    }

    /** Called once notifications are allowed, since a timer that can't notify is no use. */
    fun start(timer: BreakTimer) {
        val active = manager.breakFor(timer)
        _uiState.value = BreakTimerUiState(loaded = true, active = active, justStarted = true)
        viewModelScope.launch {
            manager.start(active)
            settingsStore.setNotificationsEnabled(true)
        }
    }

    fun cancel() {
        _uiState.value = BreakTimerUiState(loaded = true, active = null)
        viewModelScope.launch { manager.cancel() }
    }

    fun onStartAnnounced() = _uiState.update { it.copy(justStarted = false) }

    fun onTimeUp() {
        viewModelScope.launch { manager.reconcile() }
    }

    fun onNotificationsRefused() {
        viewModelScope.launch { settingsStore.setNotificationsEnabled(false) }
    }
}
