package io.tafdev.prdok.ui.calendar

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import io.tafdev.prdok.R
import io.tafdev.prdok.data.export.DeviceCalendar
import io.tafdev.prdok.data.export.ExportInspection
import io.tafdev.prdok.ui.theme.PrdokForAndroidTheme
import java.time.YearMonth
import java.util.Locale

private val SUBMIT_HEIGHT = 52.dp

private val CALENDAR_PERMISSIONS = arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)

/** Reminder offsets on offer, in minutes; `null` is "no notification". */
private val ALARM_OPTIONS: List<Int?> = listOf(null, 30, 60, 120, 360, 720, 1440)

/**
 * "Upload to calendar": exports the displayed month's planned shifts into a device
 * calendar. Asks for the calendar permission on opening; the rest of the form only
 * appears once it is granted.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportSheet(
    month: YearMonth,
    state: ExportUiState,
    onPermissionResult: (granted: Boolean) -> Unit,
    onSelectCalendar: (Long) -> Unit,
    onExport: (title: String, alarmMinutesBefore: Int?) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current

    // The system dialog is an Activity result: register a launcher, fire it, get called back.
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results -> onPermissionResult(results.values.all { it }) }

    LaunchedEffect(Unit) {
        val alreadyGranted = CALENDAR_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
        if (alreadyGranted) onPermissionResult(true) else permissionLauncher.launch(CALENDAR_PERMISSIONS)
    }

    ModalBottomSheet(onDismissRequest = { if (!state.isExporting) onDismiss() }, sheetState = sheetState) {
        ExportContent(month = month, state = state, onSelectCalendar = onSelectCalendar, onExport = onExport)
    }
}

@Composable
fun ExportContent(
    month: YearMonth,
    state: ExportUiState,
    onSelectCalendar: (Long) -> Unit,
    onExport: (title: String, alarmMinutesBefore: Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = Locale.getDefault()
    val defaultTitle = stringResource(R.string.export_default_event_title)
    var title by rememberSaveable { mutableStateOf(defaultTitle) }
    var alarm by rememberSaveable { mutableStateOf<Int?>(30) }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.export_title),
                style = MaterialTheme.typography.titleLarge,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = monthLabel(month, locale),
                style = MaterialTheme.typography.labelMedium,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (state.permissionGranted == false) {
            Note(stringResource(R.string.export_permission_needed), isError = true)
            return@Column
        }

        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text(stringResource(R.string.export_event_title)) },
            placeholder = { Text(defaultTitle) },
            singleLine = true,
            enabled = !state.isExporting,
            modifier = Modifier.fillMaxWidth(),
        )

        when {
            // Before the permission dialog is answered there is nothing to say yet.
            state.permissionGranted == null || state.isLoadingCalendars -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Note(stringResource(R.string.export_loading_calendars))
            }
            state.calendars.isEmpty() -> Note(stringResource(R.string.export_no_calendars))
            else -> Dropdown(
                label = stringResource(R.string.export_calendar),
                options = state.calendars,
                selected = state.selectedCalendar ?: state.calendars.first(),
                optionLabel = { it.label() },
                onSelect = { onSelectCalendar(it.id) },
                enabled = !state.isExporting,
            )
        }

        Dropdown(
            label = stringResource(R.string.export_alert),
            options = ALARM_OPTIONS,
            selected = alarm,
            optionLabel = { alarmLabel(it) },
            onSelect = { alarm = it },
            enabled = !state.isExporting,
        )

        InspectionNote(isInspecting = state.isInspecting, inspection = state.inspection)

        Button(
            onClick = { onExport(title, alarm) },
            enabled = state.canExport,
            modifier = Modifier
                .fillMaxWidth()
                .height(SUBMIT_HEIGHT),
        ) {
            when {
                state.isExporting -> CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                state.inspection?.shouldOfferSync == true -> Text(stringResource(R.string.export_sync))
                else -> Text(stringResource(R.string.export_add))
            }
        }
    }
}

@Composable
private fun InspectionNote(isInspecting: Boolean, inspection: ExportInspection?) {
    when {
        isInspecting -> Note(stringResource(R.string.export_checking))
        inspection != null -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Note(pluralStringResource(R.plurals.export_planned_count, inspection.plannedShifts, inspection.plannedShifts))
            if (inspection.shouldOfferSync) Note(stringResource(R.string.export_sync_footer))
        }
    }
}

@Composable
private fun Note(text: String, isError: Boolean = false) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** "Personal (david@…)" when the account name adds anything; otherwise just the calendar name. */
private fun DeviceCalendar.label(): String =
    if (account != null && !name.contains(account)) "$name ($account)" else name

@Composable
private fun alarmLabel(minutes: Int?): String = when {
    minutes == null -> stringResource(R.string.export_alert_none)
    minutes < 60 -> stringResource(R.string.export_alert_minutes, minutes)
    else -> pluralStringResource(R.plurals.export_alert_hours, minutes / 60, minutes / 60)
}

/**
 * A read-only text field that opens a menu: Material's stand-in for a spinner.
 * Generic over the option type so the same widget serves calendars and alarm offsets.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> Dropdown(
    label: String,
    options: List<T>,
    selected: T,
    optionLabel: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    enabled: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { if (enabled) expanded = it }) {
        OutlinedTextField(
            value = optionLabel(selected),
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            enabled = enabled,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

// --- Previews ---------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PreviewExport(state: ExportUiState) {
    PrdokForAndroidTheme {
        Surface(color = BottomSheetDefaults.ContainerColor) {
            Column {
                BottomSheetDefaults.DragHandle(modifier = Modifier.align(Alignment.CenterHorizontally))
                ExportContent(month = YearMonth.of(2026, 9), state = state, onSelectCalendar = {}, onExport = { _, _ -> })
            }
        }
    }
}

private val previewCalendars = listOf(
    DeviceCalendar(1, "Personal", "david@example.com"),
    DeviceCalendar(2, "Work", null),
)

@Preview(showBackground = true, name = "Add")
@Composable
private fun ExportAddPreview() = PreviewExport(
    ExportUiState(
        permissionGranted = true,
        calendars = previewCalendars,
        selectedCalendarId = 1,
        inspection = ExportInspection(plannedShifts = 9, alreadyExported = 0),
    )
)

@Preview(showBackground = true, name = "Sync")
@Composable
private fun ExportSyncPreview() = PreviewExport(
    ExportUiState(
        permissionGranted = true,
        calendars = previewCalendars,
        selectedCalendarId = 2,
        inspection = ExportInspection(plannedShifts = 9, alreadyExported = 7),
    )
)

@Preview(showBackground = true, name = "Permission denied")
@Composable
private fun ExportDeniedPreview() = PreviewExport(ExportUiState(permissionGranted = false))
