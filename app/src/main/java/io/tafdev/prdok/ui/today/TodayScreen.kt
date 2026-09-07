package io.tafdev.prdok.ui.today

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.tafdev.prdok.R
import io.tafdev.prdok.data.model.PragueTime
import io.tafdev.prdok.data.model.Shift
import io.tafdev.prdok.data.model.ShiftKind
import io.tafdev.prdok.data.shifts.Countdown
import io.tafdev.prdok.data.shifts.CountdownUnit
import io.tafdev.prdok.data.shifts.RemainingTime
import io.tafdev.prdok.data.shifts.StartsIn
import io.tafdev.prdok.data.shifts.TodayOverview
import io.tafdev.prdok.ui.theme.PrdokForAndroidTheme
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm")
private val SHORT_DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.")

/**
 * Stateful entry point: the only part that knows a ViewModel exists. It collects the
 * state and hands it to [TodayContent], which is what previews and UI tests can drive.
 */
@Composable
fun TodayScreen(
    viewModel: TodayViewModel,
    onOpenProfile: () -> Unit,
    onOpenSettings: () -> Unit,
    onWhoIsOnShift: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    TodayContent(
        uiState = uiState,
        onOpenProfile = onOpenProfile,
        onOpenSettings = onOpenSettings,
        onWhoIsOnShift = onWhoIsOnShift,
        onRetry = viewModel::refresh,
        modifier = modifier,
    )
}

/** Stateless rendering of [TodayUiState]: no ViewModel, no clock, no network. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayContent(
    uiState: TodayUiState,
    onOpenProfile: () -> Unit,
    onOpenSettings: () -> Unit,
    onWhoIsOnShift: (LocalDate) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val headerDate = uiState.now.format(DateTimeFormatter.ofPattern("d. MMMM", Locale.getDefault()))

    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = headerDate,
                        style = MaterialTheme.typography.labelLarge,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onOpenProfile) {
                        Icon(Icons.Default.Person, contentDescription = stringResource(R.string.today_profile))
                    }
                },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.today_settings))
                    }
                },
            )
        },
    ) { innerPadding ->
        val overview = uiState.overview
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                CountdownCard(
                    isLoading = uiState.isLoading,
                    errorMessage = uiState.errorMessage,
                    countdown = overview?.countdown,
                    onRetry = onRetry,
                )
            }
            if (overview != null) {
                item {
                    OutlinedButton(
                        onClick = { onWhoIsOnShift(overview.whoIsOnShiftDate) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.today_who_is_on_shift))
                    }
                }
                if (overview.upcoming.isNotEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.today_upcoming),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    items(overview.upcoming, key = { it.id }) { shift ->
                        ListItem(
                            headlineContent = { Text(shift.timeRange()) },
                            supportingContent = { Text(shift.start.format(SHORT_DATE_FORMAT)) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CountdownCard(
    isLoading: Boolean,
    errorMessage: String?,
    countdown: Countdown?,
    onRetry: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            contentAlignment = Alignment.Center,
        ) {
            when {
                isLoading -> CircularProgressIndicator()
                errorMessage != null -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(R.string.error_load_failed, errorMessage),
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = onRetry) { Text(stringResource(R.string.retry)) }
                }
                countdown != null -> CountdownContent(countdown)
            }
        }
    }
}

@Composable
private fun CountdownContent(countdown: Countdown) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        when (countdown) {
            is Countdown.Ongoing -> {
                Text(stringResource(R.string.countdown_current_ends), style = MaterialTheme.typography.labelLarge)
                Text(remainingText(countdown.remaining), style = MaterialTheme.typography.displaySmall)
                ShiftDetails(countdown.shift)
            }
            is Countdown.Upcoming -> {
                Text(stringResource(R.string.countdown_next_starts), style = MaterialTheme.typography.labelLarge)
                Text(
                    text = when (val s = countdown.startsIn) {
                        StartsIn.Today -> stringResource(R.string.countdown_today)
                        StartsIn.Tomorrow -> stringResource(R.string.countdown_tomorrow)
                        is StartsIn.Later -> stringResource(R.string.countdown_in, remainingText(s.remaining))
                    },
                    style = MaterialTheme.typography.displaySmall,
                )
                ShiftDetails(countdown.shift)
            }
            Countdown.None -> Text(stringResource(R.string.today_no_shifts))
        }
    }
}

@Composable
private fun ShiftDetails(shift: Shift) {
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(shift.timeRange(), style = MaterialTheme.typography.bodyLarge)
        Text(
            shift.start.format(SHORT_DATE_FORMAT),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun remainingText(remaining: RemainingTime): String {
    val count = remaining.value.toInt()
    val res = when (remaining.unit) {
        CountdownUnit.DAYS -> R.plurals.countdown_days
        CountdownUnit.HOURS -> R.plurals.countdown_hours
        CountdownUnit.MINUTES -> R.plurals.countdown_minutes
    }
    return pluralStringResource(res, count, count)
}

private fun Shift.timeRange(): String = "${start.format(TIME_FORMAT)} - ${end.format(TIME_FORMAT)}"
@Preview(name = "Ongoing shift (dark)", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun TodayOngoingDarkPreview() = PreviewTodayContent(TodayPreviewData.state(TodayPreviewData.ongoing))
