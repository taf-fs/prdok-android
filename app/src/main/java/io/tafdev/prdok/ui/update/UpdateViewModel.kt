package io.tafdev.prdok.ui.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.tafdev.prdok.data.update.AppRelease
import io.tafdev.prdok.data.update.UpdateChecker
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class UpdateViewModel(private val checker: UpdateChecker) : ViewModel() {

    /** The release to offer in the dialog, or null when there's nothing to say. */
    val prompt: StateFlow<AppRelease?> = checker.prompt.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = null,
    )

    /** Cheap to call often: the checker skips the network unless the last check is old enough. */
    fun checkIfDue() {
        viewModelScope.launch { checker.checkIfDue() }
    }

    /** "Later": no dialog for this version again, only for the next one. */
    fun dismiss(release: AppRelease) {
        viewModelScope.launch { checker.dismiss(release) }
    }
}
