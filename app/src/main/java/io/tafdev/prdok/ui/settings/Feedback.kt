package io.tafdev.prdok.ui.settings

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import io.tafdev.prdok.BuildConfig
import io.tafdev.prdok.R

/** The developer's inbox, where bug reports and feature ideas land. */
private const val FEEDBACK_ADDRESS = "taf.fs.dev@gmail.com"

/**
 * Opens the phone's email app on a draft to the developer: addressed, titled, and with a block of
 * build and device details under a space for the user's own words. Returns false when no app on
 * the phone handles email, so the caller can show the address instead.
 */
fun Context.sendFeedback(): Boolean {
    val body = buildString {
        appendLine(getString(R.string.settings_feedback_prompt))
        repeat(3) { appendLine() }
        append(feedbackDiagnostics())
    }
    // SENDTO with a bare "mailto:" limits the choice to email apps; the extras fill in the draft.
    val intent = Intent(Intent.ACTION_SENDTO, "mailto:".toUri()).apply {
        putExtra(Intent.EXTRA_EMAIL, arrayOf(FEEDBACK_ADDRESS))
        putExtra(Intent.EXTRA_SUBJECT, "Prdok feedback ${BuildConfig.VERSION_NAME}")
        putExtra(Intent.EXTRA_TEXT, body)
    }
    return try {
        startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}

/**
 * What a bug report needs to be reproduced: which build, which Android, which phone, which
 * language. Deliberately nothing about the account; the pairing key and employee ids stay out
 * of email. The heading and labels stay English whatever the app language, since they're read by
 * the developer.
 */
private fun Context.feedbackDiagnostics(): String {
    val buildType = if (BuildConfig.DEBUG) " debug" else ""
    return """
        -- app install information --
        App:${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})$buildType
        Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})
        Device: ${Build.MANUFACTURER} ${Build.MODEL}
        Language: ${resources.configuration.locales[0].toLanguageTag()}
    """.trimIndent()
}

/**
 * Shown when no email app picked up the draft: the address to write to some other way, selectable
 * by long press and with a button that copies it.
 */
@Composable
fun FeedbackFallbackDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_feedback_no_email_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.settings_feedback_no_email_text))
                SelectionContainer {
                    Text(
                        text = FEEDBACK_ADDRESS,
                        style = MaterialTheme.typography.bodyLarge,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { context.copyFeedbackAddress(); onDismiss() }) {
                Text(stringResource(R.string.settings_feedback_copy))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.ok)) }
        },
    )
}

private fun Context.copyFeedbackAddress() {
    val clipboard = getSystemService(ClipboardManager::class.java)
    clipboard.setPrimaryClip(ClipData.newPlainText(FEEDBACK_ADDRESS, FEEDBACK_ADDRESS))
    // Android 13 and later confirm a copy with their own popup; older versions leave it to the app.
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        Toast.makeText(this, R.string.settings_feedback_copied, Toast.LENGTH_SHORT).show()
    }
}
