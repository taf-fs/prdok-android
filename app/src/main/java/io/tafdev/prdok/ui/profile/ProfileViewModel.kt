package io.tafdev.prdok.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.tafdev.prdok.data.api.PrdokApiException
import io.tafdev.prdok.data.profile.Profile
import io.tafdev.prdok.data.profile.ProfileRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** The screen is in exactly one of these at a time, so a sealed type rather than three flags. */
sealed interface ProfileUiState {
    data object Loading : ProfileUiState
    data class Loaded(val profile: Profile) : ProfileUiState
    data object Failed : ProfileUiState
}

class ProfileViewModel(private val repository: ProfileRepository) : ViewModel() {

    private val _uiState = MutableStateFlow<ProfileUiState>(ProfileUiState.Loading)
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    private var loading: Job? = null

    /**
     * Fetches afresh. Called every time the screen opens, so the spinner shows each time rather
     * than a profile left over from the last visit (or from an account since unpaired).
     */
    fun load() {
        loading?.cancel()
        _uiState.value = ProfileUiState.Loading
        loading = viewModelScope.launch {
            _uiState.value = try {
                ProfileUiState.Loaded(repository.profile())
            } catch (_: PrdokApiException) {
                ProfileUiState.Failed
            }
        }
    }
}
