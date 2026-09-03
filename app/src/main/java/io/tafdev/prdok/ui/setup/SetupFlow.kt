package io.tafdev.prdok.ui.setup

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import io.tafdev.prdok.data.pairing.PairingManager

/**
 * The unpaired part of the app: landing screen -> credentials form.
 * Two screens don't justify a navigation library yet; a boolean does the job.
 */
@Composable
fun SetupFlow(
    pairingManager: PairingManager,
    modifier: Modifier = Modifier,
) {
    var showForm by rememberSaveable { mutableStateOf(false) }

    if (showForm) {
        // viewModel() scopes the instance to the Activity, so it survives rotation
        // while an in-flight pairing keeps running.
        val viewModel: PairingViewModel = viewModel { PairingViewModel(pairingManager) }
        BackHandler { showForm = false }
        CredentialsScreen(
            viewModel = viewModel,
            onBack = { showForm = false },
            modifier = modifier,
        )
    } else {
        SetupScreen(onConnect = { showForm = true }, modifier = modifier)
    }
}
