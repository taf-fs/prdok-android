package io.tafdev.prdok.ui.update

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.tafdev.prdok.R
import io.tafdev.prdok.ui.common.openLink

/**
 * Offers a newer release in a dialog. It checks each time the app comes to the foreground,
 * which the checker turns into at most one request every few hours.
 *
 * "Download" opens the APK link in the browser; Android's installer takes it from there.
 * "Later" hides this version for good. Tapping outside or going back only hides it until
 * the app is next started, so a stray tap doesn't lose the reminder.
 */
@Composable
fun UpdateDialog(viewModel: UpdateViewModel) {
    LifecycleStartEffect(viewModel) {
        viewModel.checkIfDue()
        onStopOrDispose {}
    }

    val release by viewModel.prompt.collectAsStateWithLifecycle()
    var hiddenVersion by rememberSaveable { mutableStateOf<String?>(null) }
    val shown = release?.takeIf { it.version.name != hiddenVersion } ?: return
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = { hiddenVersion = shown.version.name },
        text = { Text(stringResource(R.string.update_text, shown.version.name)) },
        confirmButton = {
            TextButton(onClick = {
                hiddenVersion = shown.version.name
                context.openLink(shown.downloadUrl)
            }) { Text(stringResource(R.string.update_download)) }
        },
        dismissButton = {
            TextButton(onClick = { viewModel.dismiss(shown) }) { Text(stringResource(R.string.update_later)) }
        },
    )
}
