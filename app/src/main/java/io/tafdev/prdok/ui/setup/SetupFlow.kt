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

private enum class SetupStep { LANDING, CREDENTIALS, QR_SCANNER }

/**
 * The unpaired part of the app: the landing screen, then either the credentials form or the
 * QR scanner. Three screens one step deep still don't justify a navigation library.
 */
@Composable
fun SetupFlow(
    pairingManager: PairingManager,
    modifier: Modifier = Modifier,
) {
    // An enum is saveable as it is, so the open step survives rotation.
    var step by rememberSaveable { mutableStateOf(SetupStep.LANDING) }

    if (step == SetupStep.LANDING) {
        SetupScreen(
            onConnect = { step = SetupStep.CREDENTIALS },
            onConnectWithQr = { step = SetupStep.QR_SCANNER },
            modifier = modifier,
        )
        return
    }

    // viewModel() scopes the instance to the Activity, so it survives rotation while an
    // in-flight pairing keeps running. Both steps get the same instance.
    val viewModel: PairingViewModel = viewModel { PairingViewModel(pairingManager) }
    val back = {
        // An error belongs to the screen that caused it, not to the next one opened.
        viewModel.dismissError()
        step = SetupStep.LANDING
    }
    BackHandler(onBack = back)

    when (step) {
        SetupStep.CREDENTIALS -> CredentialsScreen(viewModel = viewModel, onBack = back, modifier = modifier)
        SetupStep.QR_SCANNER -> QrScannerScreen(viewModel = viewModel, onClose = back, modifier = modifier)
        SetupStep.LANDING -> Unit
    }
}
