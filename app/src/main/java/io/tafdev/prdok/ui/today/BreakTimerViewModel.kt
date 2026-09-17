package io.tafdev.prdok.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.tafdev.prdok.data.breaktimer.ActiveBreak
import io.tafdev.prdok.data.breaktimer.BreakTimer
import io.tafdev.prdok.data.breaktimer.BreakTimerManager
import io.tafdev.prdok.data.settings.SettingsStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * [loaded] is false until the stored break has been read. Without it the first frame would
 * show both buttons and then animate a running timer in, as if the user had just started it.
 */
data class BreakTimerUiState(
    val loaded: Boolean = false,
    val active: ActiveBreak? = null,
)

class BreakTimerViewModel(
    private val manager: BreakTimerManager,
    private val settingsStore: SettingsStore,
) : ViewModel() {

    val uiState: StateFlow<BreakTimerUiState> = manager.active
        .map { BreakTimerUiState(loaded = true, active = it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BreakTimerUiState())

    init {
        // A break that ran out while the app was closed is cleared from storage here.
        viewModelScope.launch { manager.reconcile() }
    }

    /** Called once notifications are allowed, since a timer that can't notify is no use. */
    fun start(timer: BreakTimer) {
        viewModelScope.launch {
            settingsStore.setNotificationsEnabled(true)
            manager.start(timer)
        }
    }

    fun cancel() {
        viewModelScope.launch { manager.cancel() }
    }

    fun onTimeUp() {
        viewModelScope.launch { manager.reconcile() }
    }

    fun onNotificationsRefused() {
        viewModelScope.launch { settingsStore.setNotificationsEnabled(false) }
    }
}
