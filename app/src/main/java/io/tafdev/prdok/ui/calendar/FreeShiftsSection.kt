package io.tafdev.prdok.ui.calendar

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import java.time.LocalDate
import java.time.ZonedDateTime

/**
 * "Handlování směn": the open shifts anyone can pick up, under the statistics.
 * One row per day on a shared time axis; tapping a day opens who is on shift then.
 * Read-only; refreshed by the calendar's refresh button, never on its own.
 */
@Composable
fun FreeShiftsSection(load: FreeShiftsLoad, onOpenDay: (LocalDate) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.free_shifts_title),
            style = MaterialTheme.typography.titleMedium,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        when (load) {
            FreeShiftsLoad.Loading -> Centered { CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) }
            FreeShiftsLoad.Failed -> Centered { Note(stringResource(R.string.free_shifts_error)) }
            is FreeShiftsLoad.Loaded -> if (load.shifts.isEmpty()) {
                Centered { Note(stringResource(R.string.free_shifts_empty)) }
            } else {
                // map is an inline function, so its lambda may call the composable timelineEntry().
                ShiftDayTimeline(entries = load.shifts.map { it.timelineEntry() }, onOpenDay = onOpenDay)
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

/** The bars show no times and only a role letter, so a screen reader gets the times and roles spelled out instead. */
@Composable
private fun FreeShift.timelineEntry() = ShiftTimelineEntry(
    start = start,
    end = end,
    letter = role.letter,
    description = listOfNotNull(span().timeRange(), role.label()).joinToString(" "),
)

/** The portal's own `typ` marker, shown inside the bar. The screen reader gets [label] instead. */
private val FreeShiftRole.letter: String?
    get() = when (this) {
        FreeShiftRole.MANAGER -> "v"
        FreeShiftRole.BARISTA -> "b"
        FreeShiftRole.REGULAR -> null
    }

@Composable
private fun FreeShiftRole.label(): String? = when (this) {
    FreeShiftRole.MANAGER -> stringResource(R.string.free_shift_role_manager)
    FreeShiftRole.BARISTA -> stringResource(R.string.free_shift_role_barista)
    FreeShiftRole.REGULAR -> null
}

// --- Previews ---------------------------------------------------------------

private fun previewShift(id: Int, day: Int, from: Int, to: Int, role: FreeShiftRole = FreeShiftRole.REGULAR): FreeShift {
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
                        previewShift(1, 11, 9, 13),
                        // Two pairs overlapping: two lanes.
                        previewShift(2, 17, 7, 8),
                        previewShift(3, 17, 7, 11, FreeShiftRole.BARISTA),
                        previewShift(4, 17, 16, 0),
                        previewShift(5, 17, 17, 0),
                        previewShift(6, 20, 8, 11, FreeShiftRole.MANAGER),
                        // Three at once between 13 and 14: the row grows a third lane.
                        previewShift(7, 21, 10, 17),
                        previewShift(8, 21, 11, 14),
                        previewShift(9, 21, 13, 14),
                        // Touching in one lane, then the portal's 24:00-25:00.
                        previewShift(10, 25, 10, 16),
                        previewShift(11, 25, 16, 18),
                        previewShift(12, 25, 0, 1),
                    )
                ),
                onOpenDay = {},
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 360, name = "Empty")
@Composable
private fun FreeShiftsSectionEmptyPreview() {
    PrdokForAndroidTheme {
        Surface {
            FreeShiftsSection(load = FreeShiftsLoad.Loaded(emptyList()), onOpenDay = {}, modifier = Modifier.padding(16.dp))
        }
    }
}
