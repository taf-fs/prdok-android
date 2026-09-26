package io.tafdev.prdok.ui.common

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri

/** Leaves the app for [url] in the phone's browser. */
fun Context.openLink(url: String) {
    try {
        startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
    } catch (_: ActivityNotFoundException) {
        // No browser on the phone; nothing sensible left to do.
    }
}
