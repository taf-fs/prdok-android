package io.tafdev.prdok.ui.profile

import android.content.res.Configuration
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.tafdev.prdok.R
import io.tafdev.prdok.data.profile.Competency
import io.tafdev.prdok.data.profile.CompetencyStatus
import io.tafdev.prdok.data.profile.Profile
import io.tafdev.prdok.ui.common.GroupedRowColors
import io.tafdev.prdok.ui.common.GroupedSection
import io.tafdev.prdok.ui.common.GroupedSectionDivider
import io.tafdev.prdok.ui.theme.PrdokForAndroidTheme
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * The paired employee's profile: short name, start date and the seven competencies with the
 * month each was gained. Reached from Today's person icon.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Keyed on Unit: runs once each time the screen enters the composition, i.e. on every open.
    LaunchedEffect(Unit) { viewModel.load() }

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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center,
        ) {
            when (val state = uiState) {
                ProfileUiState.Loading -> Loading()
                ProfileUiState.Failed -> LoadError(onRetry = viewModel::load)
                is ProfileUiState.Loaded -> ProfileContent(state.profile)
            }
        }
    }
}

@Composable
private fun ProfileContent(profile: Profile, modifier: Modifier = Modifier) {
    val locale = Locale.getDefault()
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
    ) {
        Header(profile, locale, Modifier.padding(start = 12.dp, end = 12.dp, top = 24.dp, bottom = 16.dp))

        GroupedSection(title = stringResource(R.string.profile_competencies)) {
            profile.competencies.forEachIndexed { index, status ->
                if (index > 0) GroupedSectionDivider()
                CompetencyRow(status, locale)
            }
        }
    }
}

@Composable
private fun Header(profile: Profile, locale: Locale, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val nameStyle = MaterialTheme.typography.headlineLarge
            Text(
                text = profile.name,
                style = nameStyle,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                // A long name shrinks, down to 70 %, before it would be cut off.
                autoSize = TextAutoSize.StepBased(
                    minFontSize = nameStyle.fontSize * 0.7f,
                    maxFontSize = nameStyle.fontSize,
                ),
            )
            Text(
                text = stringResource(R.string.profile_member_since, profile.startDate.longDate(locale)),
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
        Image(
            painter = painterResource(R.drawable.cp_logo),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(56.dp),
        )
    }
}

@Composable
private fun CompetencyRow(status: CompetencyStatus, locale: Locale) {
    ListItem(
        headlineContent = { Text(stringResource(status.competency.title)) },
        trailingContent = {
            val gained = status.gained
            Text(
                // An en dash stands in for a competency not gained yet.
                text = gained?.monthLabel(locale) ?: "–",
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                color = if (gained != null) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.tertiary,
            )
        },
        colors = GroupedRowColors,
    )
}

@Composable
private fun Loading() {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        CircularProgressIndicator()
        Text(stringResource(R.string.profile_loading), color = MaterialTheme.colorScheme.secondary)
    }
}

@Composable
private fun LoadError(onRetry: () -> Unit) {
    Column(
        modifier = Modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.profile_error),
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.secondary,
            textAlign = TextAlign.Center,
        )
        TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
    }
}

@get:StringRes
private val Competency.title: Int
    get() = when (this) {
        Competency.GENERAL_SKILLS -> R.string.competency_general_skills
        Competency.PLACAR -> R.string.competency_placar
        Competency.BARMAN -> R.string.competency_barman
        Competency.VRCHNI -> R.string.competency_vrchni
        Competency.VYCEP -> R.string.competency_vycep
        Competency.BARISTA -> R.string.competency_barista
        Competency.KUCHAR -> R.string.competency_kuchar
    }

/** "30. srpna 2024" / "August 30, 2024". */
private fun LocalDate.longDate(locale: Locale): String =
    format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(locale))

/** "září 2024" / "September 2024": `LLLL` is the stand-alone month name (nominative in Czech). */
private fun YearMonth.monthLabel(locale: Locale): String =
    format(DateTimeFormatter.ofPattern("LLLL y", locale))

private val previewProfile = Profile(
    name = "Jana N.",
    startDate = LocalDate.of(2024, 8, 30),
    competencies = Competency.entries.mapIndexed { index, competency ->
        CompetencyStatus(competency, if (index % 3 == 2) null else YearMonth.of(2024, 9 + index % 4))
    },
)

@Preview(widthDp = 360, heightDp = 640)
@Composable
private fun ProfilePreview() {
    PrdokForAndroidTheme(darkTheme = false) {
        Scaffold { padding -> ProfileContent(previewProfile, Modifier.padding(padding)) }
    }
}

@Preview(widthDp = 360, heightDp = 640, name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ProfileDarkPreview() {
    PrdokForAndroidTheme(darkTheme = true) {
        Scaffold { padding -> ProfileContent(previewProfile, Modifier.padding(padding)) }
    }
}
