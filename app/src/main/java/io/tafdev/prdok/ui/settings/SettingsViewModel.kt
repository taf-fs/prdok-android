package io.tafdev.prdok.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.tafdev.prdok.data.api.PrdokApiException
import io.tafdev.prdok.data.pairing.PairingManager
import io.tafdev.prdok.data.settings.SettingsStore
import io.tafdev.prdok.data.settings.ThemePreference
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val isUnpairing: Boolean = false,
    val unpairError: String? = null,
)

class SettingsViewModel(
    private val pairingManager: PairingManager,
    private val settingsStore: SettingsStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    /**
     * The stored preference, as state the screen can read straight away.
     *
     * `stateIn` turns the store's cold Flow — which would start a fresh read for every
     * collector — into one shared value. It keeps running for 5 s after the last collector
     * leaves, so a rotation doesn't re-read the file.
     */
    val theme: StateFlow<ThemePreference> = settingsStore.theme.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ThemePreference.SYSTEM,
    )

    /** Writes the choice; the new value comes back through [theme] and repaints the app. */
    fun setTheme(preference: ThemePreference) {
        viewModelScope.launch { settingsStore.setTheme(preference) }
    }

    val notificationsEnabled: StateFlow<Boolean> = settingsStore.notificationsEnabled.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = false,
    )

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setNotificationsEnabled(enabled) }
    }

    /**
     * On success the PairingStore empties and the root screen switches to Setup by
     * itself. On failure the pairing is kept and the error is shown.
     */
    fun unpair() {
        if (_uiState.value.isUnpairing) return
        viewModelScope.launch {
            _uiState.value = SettingsUiState(isUnpairing = true)
            try {
                pairingManager.unpair()
                _uiState.value = SettingsUiState()
            } catch (e: PrdokApiException) {
                _uiState.value = SettingsUiState(unpairError = e.message ?: "Unknown error")
            }
        }
    }

    fun dismissError() = _uiState.update { it.copy(unpairError = null) }
}
