package io.tafdev.prdok.ui.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.tafdev.prdok.R
import io.tafdev.prdok.ui.common.DripLoadingAnimation
import io.tafdev.prdok.ui.common.GroupedRowColors
import io.tafdev.prdok.ui.common.GroupedSection
import io.tafdev.prdok.ui.common.GroupedSectionDivider
import io.tafdev.prdok.ui.common.TabTitle
import io.tafdev.prdok.ui.common.WithNotificationAccess
import io.tafdev.prdok.ui.common.notificationsAllowed

/**
 * Settings: app language, colour theme, notifications, feedback, about, and the destructive unpair, grouped into
 * rounded sections under the same big title the tabs use.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onOpenAbout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val theme by viewModel.theme.collectAsStateWithLifecycle()
    val notificationsEnabled by viewModel.notificationsEnabled.collectAsStateWithLifecycle()
    var confirmUnpair by rememberSaveable { mutableStateOf(false) }
    var showThemeSheet by rememberSaveable { mutableStateOf(false) }
    var showLanguageSheet by rememberSaveable { mutableStateOf(false) }
    var showFeedbackFallback by rememberSaveable { mutableStateOf(false) }
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
            // Only the back arrow: the title sits in the content, set like every tab's.
            TopAppBar(
                title = {},
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
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                TabTitle(stringResource(R.string.settings_title))

                GroupedSection(title = stringResource(R.string.settings_general)) {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.settings_language)) },
                        trailingContent = if (hasSystemLanguagePicker) {
                            // It leaves the app for the system's screen, so say so.
                            { Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null) }
                        } else {
                            null
                        },
                        colors = GroupedRowColors,
                        modifier = Modifier.clickable {
                            if (hasSystemLanguagePicker) context.openLanguageSettings() else showLanguageSheet = true
                        },
                    )
                    GroupedSectionDivider()
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.settings_theme)) },
                        colors = GroupedRowColors,
                        modifier = Modifier.clickable { showThemeSheet = true },
                    )
                }

                GroupedSection(title = stringResource(R.string.settings_notifications)) {
                    WithNotificationAccess(onRefused = { viewModel.setNotificationsEnabled(false) }) { requestAccess ->
                        // toggleable makes the whole row the switch; the Switch itself only draws (onCheckedChange = null),
                        // so a screen reader hears one control instead of a row and a switch.
                        ListItem(
                            headlineContent = { Text(stringResource(R.string.settings_notifications_toggle)) },
                            trailingContent = { Switch(checked = notificationsEnabled, onCheckedChange = null) },
                            colors = GroupedRowColors,
                            modifier = Modifier.toggleable(value = notificationsEnabled, role = Role.Switch) { on ->
                                if (on) {
                                    requestAccess { viewModel.setNotificationsEnabled(true) }
                                } else {
                                    viewModel.setNotificationsEnabled(false)
                                }
                            },
                        )
                    }
                }

                GroupedSection(title = stringResource(R.string.settings_support)) {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.settings_feedback)) },
                        trailingContent = { Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null) },
                        colors = GroupedRowColors,
                        modifier = Modifier.clickable {
                            if (!context.sendFeedback()) showFeedbackFallback = true
                        },
                    )
                    GroupedSectionDivider()
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.settings_about)) },
                        colors = GroupedRowColors,
                        modifier = Modifier.clickable(onClick = onOpenAbout),
                    )
                }

                GroupedSection {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.settings_unpair)) },
                        colors = ListItemDefaults.colors(
                            containerColor = Color.Transparent,
                            headlineColor = MaterialTheme.colorScheme.error,
                        ),
                        modifier = Modifier.clickable(enabled = !uiState.isUnpairing) { confirmUnpair = true },
                    )
                }
            }

            // Full-screen overlay while the unpair request is in flight.
            if (uiState.isUnpairing) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center,
                ) {
                    DripLoadingAnimation()
                }
            }
        }
    }

    if (showLanguageSheet) {
        LanguageSheet(
            selected = AppLanguage.current(),
            onSelect = { language ->
                // Closed first: picking a language recreates the screen, and the sheet
                // shouldn't come back with it.
                showLanguageSheet = false
                language.makeCurrent()
            },
            onDismiss = { showLanguageSheet = false },
        )
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

    if (showFeedbackFallback) {
        FeedbackFallbackDialog(onDismiss = { showFeedbackFallback = false })
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
 * Android 13 added a per-app language screen to the system settings (built from
 * res/xml/locales_config.xml). From there on the app hands off to it; below, it picks in-app.
 */
private val hasSystemLanguagePicker = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun Context.openLanguageSettings() {
    val intent = Intent(Settings.ACTION_APP_LOCALE_SETTINGS, Uri.fromParts("package", packageName, null))
    try {
        startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        // Some manufacturers' builds leave the screen out; the in-app sheet would be the
        // fallback, but no such device has turned up yet.
    }
}
