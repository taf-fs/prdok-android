package io.tafdev.prdok.ui.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import io.tafdev.prdok.BuildConfig
import io.tafdev.prdok.R
import io.tafdev.prdok.ui.common.GroupedRowColors
import io.tafdev.prdok.ui.common.GroupedSection
import io.tafdev.prdok.ui.common.GroupedSectionDivider

private const val PRIVACY_POLICY_URL = "https://taf-fs.github.io/prdok/"

/** The support page, which lists the common problems and their fixes. */
private const val COMMON_PROBLEMS_URL = "https://taf-fs.github.io/prdok/support.html"

private val SPECIAL_THANKS = listOf(
    "Ali",
    "Andrei",
    "Aneška",
    "Antonina",
    "Arča",
    "Eliška H.",
    "Jarda",
    "Karla",
    "Kačka J.",
    "Kryštof Pše.",
    "Káťa R.",
    "Martin M.",
    "Natálie K.",
    "Polina",
    "Vítek",
    "Zuza E.",
)

private const val SECRET_TAPS = 10

/**
 * About the app: logo, platform label and installed version, who made it, and links to
 * the privacy policy and common problems pages. Everything on it is static, so it needs no ViewModel.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Image(
                    painter = painterResource(R.drawable.cp_logo),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .size(88.dp)
                        .padding(bottom = 12.dp),
                )
                Text(
                    text = stringResource(R.string.about_platform),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    // VERSION_NAME and VERSION_CODE come from versionName/versionCode in app/build.gradle.kts.
                    text = stringResource(R.string.about_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }

            GroupedSection { CreditsCard() }

            GroupedSection {
                LinkRow(stringResource(R.string.about_privacy_policy)) { context.openLink(PRIVACY_POLICY_URL) }
                GroupedSectionDivider()
                LinkRow(stringResource(R.string.about_common_problems)) { context.openLink(COMMON_PROBLEMS_URL) }
            }
        }
    }
}

/**
 * Who made the app, and a secret: tap it [SECRET_TAPS] times and it grows to thank [SPECIAL_THANKS].
 * There's no ripple on tap, so nothing hints that the card reacts at all.
 */
@Composable
private fun CreditsCard() {
    var taps by rememberSaveable { mutableIntStateOf(0) }
    val revealed = taps >= SECRET_TAPS
    val haptics = LocalHapticFeedback.current

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = null,
                indication = null,
                enabled = !revealed,
            ) {
                taps++
                if (taps == SECRET_TAPS) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            }
            // Grows smoothly when the names appear, instead of jumping to its new height.
            .animateContentSize()
            .padding(16.dp),
    ) {
        Text(
            text = stringResource(R.string.about_credits),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.secondary,
            textAlign = TextAlign.Center,
        )
        if (revealed) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = stringResource(R.string.about_special_thanks),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                SPECIAL_THANKS.forEach { name ->
                    Text(
                        text = name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
            }
        }
    }
}

/** A row that leaves the app for a web page, marked with the same icon as the other such rows. */
@Composable
private fun LinkRow(title: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        trailingContent = { Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null) },
        colors = GroupedRowColors,
        modifier = Modifier.clickable(onClick = onClick),
    )
}

private fun Context.openLink(url: String) {
    try {
        startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
    } catch (_: ActivityNotFoundException) {
        // No browser on the phone; nothing sensible left to do.
    }
}
