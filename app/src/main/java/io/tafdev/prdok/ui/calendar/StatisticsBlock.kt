package io.tafdev.prdok.ui.calendar

import android.content.res.Configuration
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.tafdev.prdok.R
import io.tafdev.prdok.data.shifts.EitherRequirement
import io.tafdev.prdok.data.shifts.MonthStatistics
import io.tafdev.prdok.data.shifts.Requirement
import io.tafdev.prdok.ui.theme.PrdokForAndroidTheme

private val ROW_SPACING = 6.dp

private val UNIT_SIZE = 10.sp

private const val LOADING_ALPHA = 0.4f

/**
 * The monthly requirements under the grid: one row each, label left, numbers right.
 * A side that meets its requirement reads green, and so does the label once the
 * requirement as a whole is met (for the either/or ones, either side is enough).
 */
@Composable
fun StatisticsBlock(statistics: MonthStatistics?, isLoading: Boolean, modifier: Modifier = Modifier) {
    val spans = valueSpans()
    val offeredUnit = stringResource(R.string.stats_suffix_offered)
    val actualUnit = stringResource(R.string.stats_suffix_actual)
    val or = stringResource(R.string.stats_or)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (isLoading) LOADING_ALPHA else 1f),
        verticalArrangement = Arrangement.spacedBy(ROW_SPACING),
    ) {
        StatRow(
            label = stringResource(R.string.stats_weekend_hours),
            met = statistics?.weekendHours?.met == true,
            spans = spans,
            values = buildAnnotatedString {
                appendSide(statistics?.weekendHours, offeredUnit, spans)
            },
        )
        StatRow(
            label = stringResource(R.string.stats_closing_shifts),
            // The planned count is informational, so it hangs off the label rather
            // than competing with the two numbers that actually decide the row.
            note = statistics?.let { stringResource(R.string.stats_planned_inline, it.plannedClosingShifts) },
            met = statistics?.closingShifts?.met == true,
            spans = spans,
            values = buildAnnotatedString {
                appendSide(statistics?.closingShifts?.actual, actualUnit, spans)
                withStyle(spans.conjunction) { append(" $or ") }
                appendSide(statistics?.closingShifts?.offered, offeredUnit, spans)
            },
        )
        StatRow(
            label = stringResource(R.string.stats_total_hours),
            met = statistics?.totalHours?.met == true,
            spans = spans,
            values = buildAnnotatedString {
                appendSide(statistics?.totalHours?.actual, actualUnit, spans)
                withStyle(spans.conjunction) { append(" $or ") }
                appendSide(statistics?.totalHours?.offered, offeredUnit, spans)
            },
        )
    }
}

@Composable
private fun StatRow(
    label: String,
    met: Boolean,
    spans: ValueSpans,
    values: AnnotatedString,
    note: String? = null,
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = buildAnnotatedString {
                append(label)
                if (note != null) withStyle(spans.unit) { append(" $note") }
            },
            style = labelTextStyle(),
            color = if (met) metColor() else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            // Unweighted children are measured first, so the numbers claim the width
            // they need and only the label gives way on a narrow screen.
            modifier = Modifier.weight(1f),
        )
        Text(text = values, style = valueTextStyle(), maxLines = 1, softWrap = false)
    }
}

/**
 * Appends one side of a requirement - "12/12 nab." - with the numbers coloured by
 * whether that side is met. Mixing sizes and colours inside a single [AnnotatedString]
 * rather than across several Texts keeps every piece on one shared baseline.
 */
private fun AnnotatedString.Builder.appendSide(
    requirement: Requirement?,
    unit: String,
    spans: ValueSpans,
) {
    withStyle(if (requirement?.met == true) spans.met else spans.plain) {
        append(requirement?.let { "${it.value}/${it.required}" } ?: "-")
    }
    withStyle(spans.unit) { append(" $unit") }
}

/** The span styles a value line mixes, resolved once per composition. */
private data class ValueSpans(val met: SpanStyle, val plain: SpanStyle, val unit: SpanStyle, val conjunction: SpanStyle)

@Composable
private fun valueSpans(): ValueSpans {
    val unit = SpanStyle(
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = UNIT_SIZE,
        fontWeight = FontWeight.Normal,
        fontFamily = FontFamily.Monospace, // planned-count note lives inside the label's AnnotatedString, so it would have inherited the serif automatically
    )
    return ValueSpans(
        met = SpanStyle(color = metColor()),
        plain = SpanStyle(color = MaterialTheme.colorScheme.onSurface),
        unit = unit,
        // copy() keeps everything but the one field named, so "or" stays sized and coloured like a unit.
        conjunction = unit.copy(fontFamily = FontFamily.Serif),
    )
}

@Composable
private fun labelTextStyle(): TextStyle = MaterialTheme.typography.bodyMedium.copy(
    fontFamily = FontFamily.Serif,
    fontWeight = FontWeight.Medium,
)

@Composable
private fun valueTextStyle(): TextStyle = MaterialTheme.typography.bodyMedium.copy(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Normal,
)

@Composable
private fun metColor(): Color = if (isSystemInDarkTheme()) Color(0xFF4CC417) else Color(0xFF2E7D32)

// --- Previews ---------------------------------------------------------------

/** The numbers from a real August: weekend and total met, closings met on the offered side only. */
private val previewStatistics = MonthStatistics(
    openDays = 31,
    weekendHours = Requirement(value = 27, required = 19),
    closingShifts = EitherRequirement(offered = Requirement(12, 12), actual = Requirement(0, 4)),
    plannedClosingShifts = 2,
    totalHours = EitherRequirement(offered = Requirement(126, 103), actual = Requirement(47, 74)),
)

@Preview(showBackground = true, widthDp = 360)
@Preview(showBackground = true, widthDp = 360, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun StatisticsBlockPreview() {
    PrdokForAndroidTheme {
        Surface {
            StatisticsBlock(
                statistics = previewStatistics,
                isLoading = false,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

/**
 * The widest the rows can realistically get: three-digit hour totals on both sides and a
 * two-digit planned count. Rendered at 360 dp - the narrowest phone worth designing for -
 * so a label that would clip on a small screen shows up here first.
 */
private val previewWorstCase = MonthStatistics(
    openDays = 31,
    weekendHours = Requirement(value = 100, required = 19),
    closingShifts = EitherRequirement(offered = Requirement(24, 12), actual = Requirement(18, 4)),
    plannedClosingShifts = 12,
    totalHours = EitherRequirement(offered = Requirement(188, 103), actual = Requirement(166, 74)),
)

@Preview(showBackground = true, widthDp = 360, name = "Widest numbers")
@Composable
private fun StatisticsBlockWorstCasePreview() {
    PrdokForAndroidTheme {
        Surface {
            StatisticsBlock(
                statistics = previewWorstCase,
                isLoading = false,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 360, name = "Nothing loaded yet")
@Composable
private fun StatisticsBlockEmptyPreview() {
    PrdokForAndroidTheme {
        Surface {
            StatisticsBlock(statistics = null, isLoading = true, modifier = Modifier.padding(16.dp))
        }
    }
}
