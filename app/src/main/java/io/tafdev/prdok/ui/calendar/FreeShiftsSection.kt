package io.tafdev.prdok.ui.calendar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.tafdev.prdok.R
import io.tafdev.prdok.data.model.FreeShift
import io.tafdev.prdok.data.model.FreeShiftRole
import io.tafdev.prdok.data.model.PragueTime
import io.tafdev.prdok.ui.theme.PrdokForAndroidTheme
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DATE_COLUMN_WIDTH = 116.dp

/**
 * "Handlování směn": the open shifts anyone can pick up, under the statistics.
 * Read-only; refreshed by the calendar's refresh button, never on its own.
 *
 * A plain Column rather than a LazyColumn: the screen already scrolls, and a lazy
 * list inside a scrolling column has no height to lay out against.
 */
@Composable
fun FreeShiftsSection(load: FreeShiftsLoad, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.free_shifts_title),
            style = MaterialTheme.typography.titleMedium,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.SemiBold,
        )
        when (load) {
            FreeShiftsLoad.Loading -> Centered { CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) }
            FreeShiftsLoad.Failed -> Centered { Note(stringResource(R.string.free_shifts_error)) }
            is FreeShiftsLoad.Loaded -> if (load.shifts.isEmpty()) {
                Centered { Note(stringResource(R.string.free_shifts_empty)) }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    load.shifts.forEach { FreeShiftRow(it) }
                }
            }
        }
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun Note(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/**
 * Date, role and time on the left; the shift's bare pill on the shared time track on
 * the right. The track is too narrow for in-pill labels here, so the time is text.
 */
@Composable
private fun FreeShiftRow(shift: FreeShift) {
    val locale = Locale.getDefault()
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.width(DATE_COLUMN_WIDTH)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(text = shift.start.format(dayFormatter(locale)), style = sectionTextStyle())
                shift.role.label()?.let { role ->
                    Text(text = role, style = MaterialTheme.typography.labelSmall, color = muted)
                }
            }
            Text(text = shift.span().timeRange(), style = MaterialTheme.typography.labelSmall, color = muted)
        }
        ShiftIndicator(
            spans = listOf(shift.span()),
            color = OfferedColor,
            modifier = Modifier.weight(1f),
            showLabels = false,
        )
    }
}

/** "Sat 12.09." / "so 12.09.": short weekday, then the day and month. */
private fun dayFormatter(locale: Locale): DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d.M.", locale)

@Composable
private fun FreeShiftRole.label(): String? = when (this) {
    FreeShiftRole.MANAGER -> stringResource(R.string.free_shift_role_manager)
    FreeShiftRole.BARISTA -> stringResource(R.string.free_shift_role_barista)
    FreeShiftRole.REGULAR -> null
}

// --- Previews ---------------------------------------------------------------

private fun previewShift(id: Int, day: Int, from: Int, to: Int, role: FreeShiftRole): FreeShift {
    val start = ZonedDateTime.of(2026, 9, day, from, 0, 0, 0, PragueTime.ZONE)
    val end = if (to > from) start.withHour(to) else start.plusDays(1).withHour(to)
    return FreeShift(id, start, end, role)
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun FreeShiftsSectionPreview() {
    PrdokForAndroidTheme {
        Surface {
            FreeShiftsSection(
                load = FreeShiftsLoad.Loaded(
                    listOf(
                        previewShift(1, 12, 16, 23, FreeShiftRole.MANAGER),
                        previewShift(2, 13, 8, 16, FreeShiftRole.BARISTA),
                        previewShift(3, 19, 17, 1, FreeShiftRole.REGULAR),
                    )
                ),
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 360, name = "Empty")
@Composable
private fun FreeShiftsSectionEmptyPreview() {
    PrdokForAndroidTheme {
        Surface { FreeShiftsSection(load = FreeShiftsLoad.Loaded(emptyList()), modifier = Modifier.padding(16.dp)) }
    }
}
