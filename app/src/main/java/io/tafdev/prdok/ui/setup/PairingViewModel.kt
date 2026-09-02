package io.tafdev.prdok.ui.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.tafdev.prdok.data.model.Credentials
import io.tafdev.prdok.data.pairing.PairingInput
import io.tafdev.prdok.data.pairing.PairingManager
import io.tafdev.prdok.data.pairing.PairingResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Why the user could not pair. Kept as a type (not a string) so the ViewModel stays
 * free of Android resources; the screen maps each case to a localized message.
 */
sealed class PairingError {
    data object MissingFields : PairingError()
    data object InvalidLink : PairingError()
    data class Server(val message: String) : PairingError()
}

data class PairingUiState(
    val isLoading: Boolean = false,
    val error: PairingError? = null,
)

class PairingViewModel(private val pairingManager: PairingManager) : ViewModel() {

    private val _uiState = MutableStateFlow(PairingUiState())
    val uiState: StateFlow<PairingUiState> = _uiState.asStateFlow()

    fun pairWithCredentials(id: String, ids: String, provoz: String) {
        val credentials = PairingInput.fromTyped(id, ids, provoz)
        if (credentials == null) {
            _uiState.update { it.copy(error = PairingError.MissingFields) }
            return
        }
        pair(credentials)
    }

    fun pairWithLink(link: String) {
        val credentials = PairingInput.parse(link)
        if (credentials == null) {
            _uiState.update { it.copy(error = PairingError.InvalidLink) }
            return
        }
        pair(credentials)
    }

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }

    private fun pair(credentials: Credentials) {
        if (_uiState.value.isLoading) return
        viewModelScope.launch {
            _uiState.value = PairingUiState(isLoading = true)
            val startedAt = System.currentTimeMillis()
            val result = pairingManager.pair(credentials)

            // Minimum spinner duration so fast networks don't produce a flicker.
            val elapsed = System.currentTimeMillis() - startedAt
            if (elapsed < MIN_SPINNER_MS) delay(MIN_SPINNER_MS - elapsed)

            _uiState.value = when (result) {
                // On success there's nothing to show: the root screen observes the
                // PairingStore and switches to the main UI on its own.
                PairingResult.Success -> PairingUiState()
                is PairingResult.Failure -> PairingUiState(error = PairingError.Server(result.message))
            }
        }
    }

    private companion object {
        const val MIN_SPINNER_MS = 600L
    }
}
