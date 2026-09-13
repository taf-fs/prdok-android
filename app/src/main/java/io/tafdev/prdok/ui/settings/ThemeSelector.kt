package io.tafdev.prdok.ui.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.tafdev.prdok.R
import io.tafdev.prdok.data.settings.ThemePreference
import io.tafdev.prdok.ui.theme.PrdokForAndroidTheme

/** The palette's own colours, so each swatch shows the theme it stands for rather than a guess. */
private val LightSwatch = SwatchColors(
    background = Color(0xFFF4F1DA),
    ink = Color(0xFF1F2018),
    muted = Color(0xFF90917A),
)
private val DarkSwatch = SwatchColors(
    background = Color(0xFF1C160F),
    ink = Color(0xFFF3EAD7),
    muted = Color(0xFF8E7D68),
)

private class SwatchColors(val background: Color, val ink: Color, val muted: Color)

/**
 * The three theme choices, as a radio group. A group of exclusive options is one control to a
 * screen reader, so each option is [selectable] with [Role.RadioButton] rather than clickable.
 */
@Composable
fun ThemeSelector(
    selected: ThemePreference,
    onSelect: (ThemePreference) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        ThemePreference.entries.forEach { preference ->
            ThemeOption(
                preference = preference,
                selected = preference == selected,
                onSelect = { onSelect(preference) },
            )
        }
    }
}

@Composable
private fun ThemeOption(preference: ThemePreference, selected: Boolean, onSelect: () -> Unit) {
    val outline = MaterialTheme.colorScheme.outlineVariant
    Column(
        modifier = Modifier
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(horizontal = 8.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Canvas(Modifier.size(width = 64.dp, height = 88.dp)) { drawSwatch(preference, outline) }
        Text(text = preference.label(), style = MaterialTheme.typography.bodyMedium)
        Icon(
            imageVector = if (selected) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
            contentDescription = null,
            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        )
    }
}

/** System shows both halves of the split, which is what "follow the device" looks like. */
private fun DrawScope.drawSwatch(preference: ThemePreference, outline: Color) {
    when (preference) {
        ThemePreference.LIGHT -> miniScreen(LightSwatch)
        ThemePreference.DARK -> miniScreen(DarkSwatch)
        ThemePreference.SYSTEM -> {
            miniScreen(LightSwatch)
            // Draw the dark one over the right half only; clipRect confines every following
            // draw to that rectangle, so the rounded corners of the half still line up.
            clipRect(left = size.width / 2) { miniScreen(DarkSwatch) }
        }
    }
    drawRoundRect(color = outline, cornerRadius = corner(), style = Stroke(width = 2f))
}

/** A phone-shaped block of the theme: its background, a title bar and two lines of text. */
private fun DrawScope.miniScreen(colors: SwatchColors) {
    drawRoundRect(color = colors.background, cornerRadius = corner())
    val inset = size.width * 0.16f
    val width = size.width - inset * 2
    val barHeight = size.height * 0.07f
    fun bar(top: Float, fraction: Float, color: Color) = drawRoundRect(
        color = color,
        topLeft = Offset(inset, size.height * top),
        size = Size(width * fraction, barHeight),
        cornerRadius = CornerRadius(barHeight / 2),
    )
    bar(top = 0.22f, fraction = 1f, color = colors.ink)
    bar(top = 0.45f, fraction = 0.75f, color = colors.muted)
    bar(top = 0.60f, fraction = 0.9f, color = colors.muted)
}

private fun DrawScope.corner() = CornerRadius(size.minDimension * 0.16f)

@Composable
internal fun ThemePreference.label(): String = stringResource(
    when (this) {
        ThemePreference.SYSTEM -> R.string.settings_theme_system
        ThemePreference.LIGHT -> R.string.settings_theme_light
        ThemePreference.DARK -> R.string.settings_theme_dark
    }
)

@Preview(name = "Light", showBackground = true)
@Composable
private fun ThemeSelectorPreview() {
    PrdokForAndroidTheme(darkTheme = false) {
        Surface { ThemeSelector(selected = ThemePreference.SYSTEM, onSelect = {}) }
    }
}

@Preview(name = "Dark", showBackground = true)
@Composable
private fun ThemeSelectorDarkPreview() {
    PrdokForAndroidTheme(darkTheme = true) {
        Surface { ThemeSelector(selected = ThemePreference.DARK, onSelect = {}) }
    }
}
