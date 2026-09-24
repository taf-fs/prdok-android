package io.tafdev.prdok.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.tafdev.prdok.R
import io.tafdev.prdok.ui.common.DripLoadingAnimation

/**
 * Credentials form with two modes: the three typed fields, or a pasted pairing link.
 * All state lives in [PairingViewModel]; this composable only renders and forwards events.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CredentialsScreen(
    viewModel: PairingViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Field contents are local UI state; rememberSaveable keeps them across rotation.
    var id by rememberSaveable { mutableStateOf("") }
    var ids by rememberSaveable { mutableStateOf("") }
    var provoz by rememberSaveable { mutableStateOf("") }
    var link by rememberSaveable { mutableStateOf("") }
    var linkMode by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.setup_connect)) },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !uiState.isLoading) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (linkMode) {
                    OutlinedTextField(
                        value = link,
                        onValueChange = { link = it; viewModel.dismissError() },
                        label = { Text(stringResource(R.string.field_link)) },
                        singleLine = true,
                        enabled = !uiState.isLoading,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    OutlinedTextField(
                        value = id,
                        onValueChange = { id = it; viewModel.dismissError() },
                        label = { Text(stringResource(R.string.field_id)) },
                        singleLine = true,
                        enabled = !uiState.isLoading,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = ids,
                        onValueChange = { ids = it; viewModel.dismissError() },
                        label = { Text(stringResource(R.string.field_ids)) },
                        singleLine = true,
                        enabled = !uiState.isLoading,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = provoz,
                        onValueChange = { provoz = it; viewModel.dismissError() },
                        label = { Text(stringResource(R.string.field_provoz)) },
                        singleLine = true,
                        enabled = !uiState.isLoading,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                uiState.error?.let { error ->
                    Text(
                        text = pairingErrorMessage(error),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                Spacer(Modifier.height(4.dp))

                Button(
                    onClick = {
                        if (linkMode) viewModel.pairWithLink(link)
                        else viewModel.pairWithCredentials(id, ids, provoz)
                    },
                    enabled = !uiState.isLoading,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.setup_pair))
                }

                TextButton(
                    onClick = { linkMode = !linkMode; viewModel.dismissError() },
                    enabled = !uiState.isLoading,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    Text(
                        stringResource(
                            if (linkMode) R.string.setup_switch_to_credentials
                            else R.string.setup_switch_to_link
                        )
                    )
                }
            }

            if (uiState.isLoading) {
                DripLoadingAnimation(modifier = Modifier.align(Alignment.Center))
            }
        }
    }
}

/** Shared with the QR scanner, which shows the same errors in a dialog. */
@Composable
internal fun pairingErrorMessage(error: PairingError): String = when (error) {
    PairingError.MissingFields -> stringResource(R.string.error_missing_fields)
    PairingError.InvalidLink -> stringResource(R.string.error_invalid_link)
    PairingError.InvalidQr -> stringResource(R.string.error_invalid_qr)
    is PairingError.Server -> stringResource(R.string.error_pairing_failed, error.message)
}
