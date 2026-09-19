package io.tafdev.prdok.ui.common

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import io.tafdev.prdok.R

/** True when a notification posted now would actually show. */
fun Context.notificationsAllowed(): Boolean = NotificationManagerCompat.from(this).areNotificationsEnabled()

/**
 * Gives [content] a `requestAccess { ... }` function that runs its block only once notifications
 * are allowed, asking for the permission first if that is still possible.
 *
 * Since Android 13 notifications are a runtime permission. The system shows its dialog at most
 * twice; after that a request is refused without any dialog at all. Android doesn't say which
 * of those happened, but `shouldShowRequestPermissionRationale` is false once the dialog can no
 * longer appear, so a refusal with it false gets the dialog below, pointing to the settings.
 * A first refusal leaves the user alone. Before Android 13 there is nothing to ask: notifications
 * are simply on or off in the settings.
 *
 * [onRefused] runs on every refusal, so callers can switch their own toggles off.
 */
@Composable
fun WithNotificationAccess(
    onRefused: () -> Unit,
    content: @Composable (requestAccess: (onGranted: () -> Unit) -> Unit) -> Unit,
) {
    val context = LocalContext.current
    var showDeniedDialog by rememberSaveable { mutableStateOf(false) }
    // What to run once the system dialog answers "yes". Plain memory: it only has to outlive the dialog.
    val pending = remember { PendingGrant() }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val onGranted = pending.onGranted
        pending.onGranted = null
        if (granted) {
            onGranted?.invoke()
        } else {
            onRefused()
            if (!context.canAskForNotifications()) showDeniedDialog = true
        }
    }

    content { onGranted ->
        when {
            context.notificationsAllowed() -> onGranted()
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED -> {
                pending.onGranted = onGranted
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            // Permission granted (or not a thing yet) but notifications are switched off in the settings.
            else -> {
                onRefused()
                showDeniedDialog = true
            }
        }
    }

    if (showDeniedDialog) {
        AlertDialog(
            onDismissRequest = { showDeniedDialog = false },
            title = { Text(stringResource(R.string.notifications_denied_title)) },
            text = { Text(stringResource(R.string.notifications_denied_text)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeniedDialog = false
                    context.openNotificationSettings()
                }) { Text(stringResource(R.string.open_settings)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeniedDialog = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

private class PendingGrant {
    var onGranted: (() -> Unit)? = null
}

private fun Context.canAskForNotifications(): Boolean {
    val activity = findActivity() ?: return false
    return ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.POST_NOTIFICATIONS)
}

/** Compose hands out the Activity wrapped in other Contexts; this peels them off. */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** The app's own notification page in the system settings, where they can be switched back on. */
private fun Context.openNotificationSettings() {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
    try {
        startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        // No such screen on this build; nothing better to offer.
    }
}
