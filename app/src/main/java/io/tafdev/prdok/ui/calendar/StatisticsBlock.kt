package io.tafdev.prdok.ui.calendar

import android.content.res.Configuration
import android.text.format.DateFormat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
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
import io.tafdev.prdok.data.bonus.BonusCondition
import io.tafdev.prdok.data.bonus.BonusEither
import io.tafdev.prdok.data.bonus.BonusStructure
import io.tafdev.prdok.data.shifts.MonthStatistics
import io.tafdev.prdok.data.shifts.Requirement
import io.tafdev.prdok.ui.theme.PrdokForAndroidTheme
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val CARD_CORNER = 16.dp
private val CARD_PADDING = 16.dp
private val ROW_SPACING = 6.dp
private val HEADER_GAP = 10.dp
private val UNIT_SIZE = 10.sp
private val CHEVRON_SIZE = 16.dp
private const val LOADING_ALPHA = 0.4f
private const val TOGGLE_MILLIS = 200

/** The green wash over an earned month. Low enough that the card stays readable in dark mode. */
private const val EARNED_TINT_ALPHA = 0.12f

/** Shown instead of a number nobody can state. */
private const val DASH = "-"

/**
 * The monthly bonus card under the calendar: a header saying whether this month's bonus
 * was earned, and the six conditions behind that verdict folded in underneath.
 *
 * Green is the whole verdict — the header turns green when the bonus came out, a row's
 * label when its condition was met, a single number when that number is above its limit.
 */
@Composable
fun StatisticsBlock(
    statistics: MonthStatistics?,
    bonus: BonusStructure?,
    isLoading: Boolean,
    modifier: Modifier = Modifier,
) {
    // Here rather than in CalendarUiState: an employee who opened the conditions keeps
    // them open while paging from month to month.
    var expanded by rememberSaveable { mutableStateOf(false) }
    StatisticsCard(
        statistics = statistics,
        bonus = bonus,
        isLoading = isLoading,
        expanded = expanded,
        onToggle = { expanded = !expanded },
        modifier = modifier,
    )
}

/** The card itself, with its open/closed state handed in so the previews can force it open. */
@Composable
private fun StatisticsCard(
    statistics: MonthStatistics?,
    bonus: BonusStructure?,
    isLoading: Boolean,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val earned = bonus?.earned == true
    // Laid over the card colour rather than replacing it, so an earned month reads as the
    // same card with a wash on it.
    val tint = if (earned) metColor().copy(alpha = EARNED_TINT_ALPHA) else Color.Transparent

    Surface(
        shape = RoundedCornerShape(CARD_CORNER),
        color = MaterialTheme.colorScheme.surfaceVariant,
        // Header included: until the fetch lands, the score on it is the previous month's.
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (isLoading) LOADING_ALPHA else 1f),
    ) {
        Column(Modifier.background(tint).padding(CARD_PADDING)) {
            BonusHeader(bonus = bonus, expanded = expanded, onToggle = onToggle)
            AnimatedVisibility(
                visible = expanded,
                // As one block, not row by row: the calendar above is already moving.
                enter = fadeIn(tween(TOGGLE_MILLIS)) + expandVertically(tween(TOGGLE_MILLIS)),
                exit = fadeOut(tween(TOGGLE_MILLIS)) + shrinkVertically(tween(TOGGLE_MILLIS)),
            ) {
                Conditions(
                    statistics = statistics,
                    bonus = bonus,
                    modifier = Modifier.padding(top = HEADER_GAP),
                )
            }
        }
    }
}

/**
 * Did the bonus come out, and how close was it — the only part most months need. The
 * fraction counts conditions *actually met*, so an earned month routinely reads 5/6 with
 * a joker covering the sixth; the pass line isn't printed, since it moves with the
 * employee's joker count.
 */
@Composable
private fun BonusHeader(bonus: BonusStructure?, expanded: Boolean, onToggle: () -> Unit) {
    val earned = bonus?.earned == true
    val headerColor = if (earned) metColor() else MaterialTheme.colorScheme.onSurface
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(TOGGLE_MILLIS),
        label = "chevron",
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable(interactionSource = null, indication = null, onClick = onToggle),
    ) {
        Text(
            text = stringResource(R.string.stats_bonus_title),
            style = MaterialTheme.typography.bodyLarge.copy(
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.SemiBold,
            ),
            color = headerColor,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = bonus?.let { "${it.score}/${it.maxScore}" } ?: DASH,
            style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
            color = if (bonus == null) MaterialTheme.colorScheme.onSurfaceVariant else headerColor,
        )
        Icon(
            imageVector = Icons.Default.KeyboardArrowDown,
            contentDescription = stringResource(
                if (expanded) R.string.stats_bonus_collapse else R.string.stats_bonus_expand
            ),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(start = 4.dp)
                .size(CHEVRON_SIZE)
                .rotate(rotation),
        )
    }
}

/**
 * The six conditions, one row each: label left, numbers right. Rows 1-3 come through
 * [MonthStatistics], which has already decided whether the server or the local shift math
 * owns them; rows 4-6 only the server can state, so they read [bonus] and dash without it.
 */
@Composable
private fun Conditions(statistics: MonthStatistics?, bonus: BonusStructure?, modifier: Modifier = Modifier) {
    val spans = valueSpans()
    val offeredUnit = stringResource(R.string.stats_suffix_offered)
    val actualUnit = stringResource(R.string.stats_suffix_actual)
    val hoursUnit = stringResource(R.string.stats_suffix_hours)
    val competencyUnit = stringResource(R.string.stats_suffix_competencies)
    val or = stringResource(R.string.stats_or)

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ROW_SPACING)) {
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
            // Informational, so it hangs off the label rather than competing with the two
            // numbers that decide the row.
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
        // A yes/no, granted automatically in months where no meeting is held. The note is
        // the server's own sentence saying so, on a line of its own because the label line
        // is one line that ellipsizes.
        StatRow(
            label = stringResource(R.string.stats_meeting),
            detail = bonus?.meetingNote?.takeIf { it.isNotEmpty() },
            met = bonus?.meeting?.met == true,
            spans = spans,
            values = buildAnnotatedString {
                val met = bonus?.meeting?.met
                withStyle(if (met == true) spans.met else if (met == null) spans.unknown else spans.plain) {
                    append(
                        when (met) {
                            true -> stringResource(R.string.stats_mark_met)
                            false -> stringResource(R.string.stats_mark_unmet)
                            null -> DASH
                        }
                    )
                }
            },
        )
        StatRow(
            label = stringResource(R.string.stats_early_offers),
            note = bonus?.earlyOffersDeadline?.let {
                stringResource(R.string.stats_deadline_inline, it.format(rememberDeadlineFormatter()))
            },
            met = bonus?.earlyOffers?.met == true,
            spans = spans,
            values = buildAnnotatedString {
                appendSide(bonus?.earlyOffers?.asRequirement(), hoursUnit, spans)
            },
        )
        // A count, not a fraction: no threshold is sent, so the colour is the server's flag.
        StatRow(
            label = stringResource(R.string.stats_competencies),
            note = bonus?.competencyGained?.let { stringResource(R.string.stats_new_competency_inline, it) },
            met = bonus?.competencies?.met == true,
            spans = spans,
            values = buildAnnotatedString {
                val competencies = bonus?.competencies
                withStyle(if (competencies == null) spans.unknown else if (competencies.met) spans.met else spans.plain) {
                    append(competencies?.value?.toString() ?: DASH)
                }
                withStyle(spans.unit) { append(" $competencyUnit") }
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
    detail: String? = null,
) {
    Column(Modifier.fillMaxWidth()) {
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
        if (detail != null) {
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Appends one side of a requirement - "12/12 off." - coloured by whether that side is met.
 * One [AnnotatedString] rather than several Texts keeps the mixed sizes on a shared
 * baseline. A null side prints a muted dash: nobody counted this, which is not a zero.
 */
private fun AnnotatedString.Builder.appendSide(
    requirement: Requirement?,
    unit: String,
    spans: ValueSpans,
) {
    withStyle(if (requirement == null) spans.unknown else if (requirement.met) spans.met else spans.plain) {
        append(requirement?.let { "${it.value}/${it.required}" } ?: DASH)
    }
    withStyle(spans.unit) { append(" $unit") }
}

private fun BonusCondition.asRequirement() = Requirement(value ?: 0, required ?: 0, met)

/** The span styles a value line mixes, resolved once per composition. */
private data class ValueSpans(
    val met: SpanStyle,
    val plain: SpanStyle,
    val unknown: SpanStyle,
    val unit: SpanStyle,
    val conjunction: SpanStyle,
)

@Composable
private fun valueSpans(): ValueSpans {
    val unit = SpanStyle(
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = UNIT_SIZE,
        fontWeight = FontWeight.Normal,
        // Stated, not inherited: the planned-count note sits inside the label's serif text.
        fontFamily = FontFamily.Monospace,
    )
    return ValueSpans(
        met = SpanStyle(color = metColor()),
        plain = SpanStyle(color = MaterialTheme.colorScheme.onSurface),
        unknown = SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant),
        unit = unit,
        conjunction = unit.copy(fontFamily = FontFamily.Serif),
    )
}

/**
 * "18 Aug" / "18. srp" - asked for by skeleton, so the note follows the locale's own
 * day-and-month order and punctuation.
 */
@Composable
private fun rememberDeadlineFormatter(locale: Locale = Locale.getDefault()): DateTimeFormatter =
    remember(locale) { DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "dMMM"), locale) }

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

/** March 2026 as the server scored it: the meeting was missed, and a joker covered it. */
private val previewEarned = BonusStructure(
    score = 5,
    maxScore = 6,
    jokers = 1,
    jokersUsed = 1,
    earned = true,
    bonusCzkPerHour = 20,
    monthState = 1,
    weekendHours = BonusCondition(met = true, value = 28, required = 19),
    closingShifts = BonusEither(
        met = true,
        offered = BonusCondition(met = true, value = 12, required = 12),
        worked = BonusCondition(met = false, value = 2, required = 4),
    ),
    hours = BonusEither(
        met = true,
        offered = BonusCondition(met = true, value = 140, required = 103),
        worked = BonusCondition(met = false, value = 42, required = 74),
    ),
    meeting = BonusCondition(met = false),
    meetingNote = "",
    earlyOffers = BonusCondition(met = true, value = 104, required = 20),
    earlyOffersDeadline = LocalDate.of(2026, 2, 16),
    competencies = BonusCondition(met = true, value = 6),
    competencyGained = "barman",
)

/** A month that ended without the bonus, with the holiday note under the meeting row. */
private val previewMissed = previewEarned.copy(
    score = 4,
    jokersUsed = 0,
    earned = false,
    weekendHours = BonusCondition(met = false, value = 11, required = 19),
    closingShifts = BonusEither(
        met = false,
        offered = BonusCondition(met = false, value = 7, required = 12),
        worked = BonusCondition(met = true, value = 5, required = 4),
    ),
    meeting = BonusCondition(met = true),
    meetingNote = "O prázdninách není zaměstnanecká schůze.",
    earlyOffers = BonusCondition(met = false, value = 0, required = 20),
    earlyOffersDeadline = LocalDate.of(2026, 7, 16),
    competencyGained = null,
)

@Composable
private fun PreviewCard(statistics: MonthStatistics?, bonus: BonusStructure?) {
    PrdokForAndroidTheme {
        Surface {
            StatisticsCard(
                statistics = statistics,
                bonus = bonus,
                isLoading = false,
                // Open: a closed card is one line and proves nothing about the rows.
                expanded = true,
                onToggle = {},
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 360, name = "Bonus earned")
@Preview(showBackground = true, widthDp = 360, name = "Bonus earned (dark)", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun StatisticsBlockEarnedPreview() {
    PreviewCard(MonthStatistics.compute(emptyList(), YearMonth.of(2026, 3), 31, previewEarned), previewEarned)
}

@Preview(showBackground = true, widthDp = 360, name = "Bonus missed")
@Composable
private fun StatisticsBlockMissedPreview() {
    PreviewCard(MonthStatistics.compute(emptyList(), YearMonth.of(2026, 7), 31, previewMissed), previewMissed)
}

/**
 * The widest the rows realistically get: three-digit totals on both sides and a two-digit
 * planned count, at 360 dp, so a label that would clip on a small screen clips here first.
 */
private val previewWorstCase = previewEarned.copy(
    weekendHours = BonusCondition(met = true, value = 100, required = 19),
    closingShifts = BonusEither(
        met = true,
        offered = BonusCondition(met = true, value = 24, required = 12),
        worked = BonusCondition(met = true, value = 18, required = 4),
    ),
    hours = BonusEither(
        met = true,
        offered = BonusCondition(met = true, value = 188, required = 103),
        worked = BonusCondition(met = true, value = 166, required = 74),
    ),
    earlyOffers = BonusCondition(met = true, value = 188, required = 20),
)

@Preview(showBackground = true, widthDp = 360, name = "Widest numbers")
@Composable
private fun StatisticsBlockWorstCasePreview() {
    val statistics = MonthStatistics.compute(emptyList(), YearMonth.of(2026, 3), 31, previewWorstCase)
    PreviewCard(statistics.copy(plannedClosingShifts = 12), previewWorstCase)
}

/**
 * No pay structure: rows 1-3 fall back to the local computation with their worked sides
 * dashed, rows 4-6 dash entirely, and the header has no score to show.
 */
@Preview(showBackground = true, widthDp = 360, name = "No pay structure")
@Composable
private fun StatisticsBlockNoBonusPreview() {
    PreviewCard(MonthStatistics.compute(emptyList(), YearMonth.of(2026, 9), 26), bonus = null)
}
