package io.tafdev.prdok.ui.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.tafdev.prdok.R
import io.tafdev.prdok.ui.common.WithNotificationAccess
import io.tafdev.prdok.ui.common.notificationsAllowed

/** Settings: app language (handed to the OS), colour theme, notifications, and the destructive unpair. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val theme by viewModel.theme.collectAsStateWithLifecycle()
    val notificationsEnabled by viewModel.notificationsEnabled.collectAsStateWithLifecycle()
    var confirmUnpair by rememberSaveable { mutableStateOf(false) }
    var showThemeSheet by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current

    // Runs each time the screen comes back to the front, including on return from the system
    // settings: if notifications were blocked in the meantime, the toggle follows.
    LifecycleResumeEffect(Unit) {
        if (!context.notificationsAllowed()) viewModel.setNotificationsEnabled(false)
        onPauseOrDispose { }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !uiState.isUnpairing) {
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
            Column(Modifier.verticalScroll(rememberScrollState())) {
                HorizontalDivider()
                // Android has no in-app language picker of its own: the OS owns the per-app
                // language list (declared in res/xml/locales_config.xml), so this hands off.
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_language)) },
                    trailingContent = {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                    },
                    modifier = Modifier.clickable { context.openLanguageSettings() },
                )
                HorizontalDivider()
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_theme)) },
                    supportingContent = { Text(theme.label()) },
                    modifier = Modifier.clickable { showThemeSheet = true },
                )
                HorizontalDivider()

                Text(
                    text = stringResource(R.string.settings_notifications),
                    style = MaterialTheme.typography.bodyLarge,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp),
                )
                HorizontalDivider()
                WithNotificationAccess(onRefused = { viewModel.setNotificationsEnabled(false) }) { requestAccess ->
                    // toggleable makes the whole row the switch; the Switch itself only draws (onCheckedChange = null),
                    // so a screen reader hears one control instead of a row and a switch.
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.settings_notifications_toggle)) },
                        trailingContent = { Switch(checked = notificationsEnabled, onCheckedChange = null) },
                        modifier = Modifier.toggleable(value = notificationsEnabled, role = Role.Switch) { on ->
                            if (on) {
                                requestAccess { viewModel.setNotificationsEnabled(true) }
                            } else {
                                viewModel.setNotificationsEnabled(false)
                            }
                        },
                    )
                }
                HorizontalDivider()

                Spacer(Modifier.height(24.dp))
                HorizontalDivider()
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_unpair)) },
                    colors = ListItemDefaults.colors(headlineColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.clickable(enabled = !uiState.isUnpairing) { confirmUnpair = true },
                )
                HorizontalDivider()
            }

            // Full-screen overlay while the unpair request is in flight.
            if (uiState.isUnpairing) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }

    if (showThemeSheet) {
        ModalBottomSheet(onDismissRequest = { showThemeSheet = false }) {
            Text(
                text = stringResource(R.string.settings_theme),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            // The sheet stays open: the app repaints underneath it, which is the whole point.
            ThemeSelector(
                selected = theme,
                onSelect = viewModel::setTheme,
                modifier = Modifier.padding(vertical = 16.dp),
            )
        }
    }

    if (confirmUnpair) {
        AlertDialog(
            onDismissRequest = { confirmUnpair = false },
            title = { Text(stringResource(R.string.settings_unpair_confirm_title)) },
            text = { Text(stringResource(R.string.settings_unpair_confirm_text)) },
            confirmButton = {
                TextButton(onClick = { confirmUnpair = false; viewModel.unpair() }) {
                    Text(stringResource(R.string.settings_unpair), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmUnpair = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    uiState.unpairError?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::dismissError,
            title = { Text(stringResource(R.string.settings_unpair_failed_title)) },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = viewModel::dismissError) { Text(stringResource(R.string.ok)) }
            },
        )
    }
}

/**
 * Opens the app's language screen on Android 13+, where per-app languages exist, and the
 * app's system settings page below that — the nearest thing those versions have.
 */
private fun Context.openLanguageSettings() {
    val uri = Uri.fromParts("package", packageName, null)
    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Intent(Settings.ACTION_APP_LOCALE_SETTINGS, uri)
    } else {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, uri)
    }
    try {
        startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        // Some builds ship no such screen; there is nothing useful to fall back to.
    }
}
