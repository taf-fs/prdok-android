package io.tafdev.prdok.ui.theme

import androidx.compose.ui.graphics.Color

/*
 * The palette: three backgrounds and three foregrounds, each with a light and a dark value.
 * Material asks for far more roles than six, so the steps between the backgrounds and the
 * error colours are derived here. Theme.kt is where they all get their Material names.
 */

// Backgrounds. The light values don't get steadily darker or lighter: Secondary is the
// near-white one (cards, list rows) and Elevated the tinted one. In dark mode they do stack
// Primary -> Secondary -> Elevated, which is what Material's container ramp expects.
internal val CpBackgroundPrimaryLight = Color(0xFFF4F1DA)
internal val CpBackgroundPrimaryDark = Color(0xFF1C160F)
internal val CpBackgroundSecondaryLight = Color(0xFFFFFDF3)
internal val CpBackgroundSecondaryDark = Color(0xFF2A2118)
internal val CpBackgroundElevatedLight = Color(0xFFEFECCF)
internal val CpBackgroundElevatedDark = Color(0xFF352A1F)

// Foregrounds: Primary is body text and the tint controls are drawn in, Secondary the muted
// text, Muted the hairlines.
internal val CpForegroundPrimaryLight = Color(0xFF1F2018)
internal val CpForegroundPrimaryDark = Color(0xFFF3EAD7)
internal val CpForegroundSecondaryLight = Color(0xFF5F6051)
internal val CpForegroundSecondaryDark = Color(0xFFC1B29C)
internal val CpForegroundMutedLight = Color(0xFF90917A)
internal val CpForegroundMutedDark = Color(0xFF8E7D68)

// Steps between the three backgrounds, so Material's five container levels stay monotonic.
internal val CpSurfaceLowLight = Color(0xFFFAF7E9)
internal val CpSurfaceHighestLight = Color(0xFFE9E5C4)
internal val CpSurfaceLowestDark = Color(0xFF140F0A)
internal val CpSurfaceHighestDark = Color(0xFF403327)
internal val CpOutlineVariantLight = Color(0xFFD5D2B8)
internal val CpOutlineVariantDark = Color(0xFF4A3D2E)

// Errors stay Material's own reds: the palette has no error colour, and a warning that
// blends into the rest of the app is a worse warning.
internal val CpErrorLight = Color(0xFFB3261E)
internal val CpErrorDark = Color(0xFFF2B8B5)
internal val CpErrorContainerLight = Color(0xFFF9DEDC)
internal val CpErrorContainerDark = Color(0xFF8C1D18)
internal val CpOnErrorDark = Color(0xFF601410)
internal val CpOnErrorContainerLight = Color(0xFF410E0B)
