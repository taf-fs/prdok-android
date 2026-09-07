package io.tafdev.prdok.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.tafdev.prdok.data.api.PrdokApiException
import io.tafdev.prdok.data.pairing.PairingManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val isUnpairing: Boolean = false,
    val unpairError: String? = null,
)

class SettingsViewModel(private val pairingManager: PairingManager) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

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
