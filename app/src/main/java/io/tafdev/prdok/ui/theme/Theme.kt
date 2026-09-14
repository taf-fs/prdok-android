package io.tafdev.prdok.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/*
 * There is no separate accent (yet): buttons, switches and checkmarks are drawn in the primary
 * foreground, so they read as dark ink on a light theme and as light ink on a dark one.
 */

private val LightColorScheme = lightColorScheme(
    primary = CpForegroundPrimaryLight,
    onPrimary = CpBackgroundSecondaryLight,
    primaryContainer = CpBackgroundElevatedLight,
    onPrimaryContainer = CpForegroundPrimaryLight,
    secondary = CpForegroundSecondaryLight,
    onSecondary = CpBackgroundSecondaryLight,
    secondaryContainer = CpBackgroundElevatedLight,
    onSecondaryContainer = CpForegroundPrimaryLight,
    tertiary = CpForegroundMutedLight,
    onTertiary = CpBackgroundSecondaryLight,
    tertiaryContainer = CpBackgroundElevatedLight,
    onTertiaryContainer = CpForegroundPrimaryLight,
    background = CpBackgroundPrimaryLight,
    onBackground = CpForegroundPrimaryLight,
    surface = CpBackgroundPrimaryLight,
    onSurface = CpForegroundPrimaryLight,
    surfaceVariant = CpBackgroundElevatedLight,
    onSurfaceVariant = CpForegroundSecondaryLight,
    // Light mode counts down from the near-white Secondary to the tinted Elevated.
    surfaceContainerLowest = CpBackgroundSecondaryLight,
    surfaceContainerLow = CpSurfaceLowLight,
    surfaceContainer = CpBackgroundPrimaryLight,
    surfaceContainerHigh = CpBackgroundElevatedLight,
    surfaceContainerHighest = CpSurfaceHighestLight,
    surfaceTint = CpForegroundPrimaryLight,
    outline = CpForegroundMutedLight,
    outlineVariant = CpOutlineVariantLight,
    inverseSurface = CpForegroundPrimaryLight,
    inverseOnSurface = CpBackgroundSecondaryLight,
    inversePrimary = CpForegroundPrimaryDark,
    error = CpErrorLight,
    onError = CpBackgroundSecondaryLight,
    errorContainer = CpErrorContainerLight,
    onErrorContainer = CpOnErrorContainerLight,
)

private val DarkColorScheme = darkColorScheme(
    primary = CpForegroundPrimaryDark,
    onPrimary = CpBackgroundPrimaryDark,
    primaryContainer = CpBackgroundElevatedDark,
    onPrimaryContainer = CpForegroundPrimaryDark,
    secondary = CpForegroundSecondaryDark,
    onSecondary = CpBackgroundPrimaryDark,
    secondaryContainer = CpBackgroundElevatedDark,
    onSecondaryContainer = CpForegroundPrimaryDark,
    tertiary = CpForegroundMutedDark,
    onTertiary = CpBackgroundPrimaryDark,
    tertiaryContainer = CpBackgroundElevatedDark,
    onTertiaryContainer = CpForegroundPrimaryDark,
    background = CpBackgroundPrimaryDark,
    onBackground = CpForegroundPrimaryDark,
    surface = CpBackgroundPrimaryDark,
    onSurface = CpForegroundPrimaryDark,
    surfaceVariant = CpBackgroundElevatedDark,
    onSurfaceVariant = CpForegroundSecondaryDark,
    // Dark mode counts up instead: the deeper the level, the lighter the surface.
    surfaceContainerLowest = CpSurfaceLowestDark,
    surfaceContainerLow = CpBackgroundPrimaryDark,
    surfaceContainer = CpBackgroundSecondaryDark,
    surfaceContainerHigh = CpBackgroundElevatedDark,
    surfaceContainerHighest = CpSurfaceHighestDark,
    surfaceTint = CpForegroundPrimaryDark,
    outline = CpForegroundMutedDark,
    outlineVariant = CpOutlineVariantDark,
    inverseSurface = CpForegroundPrimaryDark,
    inverseOnSurface = CpBackgroundPrimaryDark,
    inversePrimary = CpForegroundPrimaryLight,
    error = CpErrorDark,
    onError = CpOnErrorDark,
    errorContainer = CpErrorContainerDark,
    onErrorContainer = CpErrorContainerLight,
)

private val LocalBackgroundSecondary = staticCompositionLocalOf { CpBackgroundSecondaryLight }

/**
 * The palette's second background, the one the lower part of Today sits on. It maps to no single
 * Material role (surfaceContainerLowest in light, surfaceContainer in dark), so it is handed down
 * on its own.
 */
val MaterialTheme.backgroundSecondary: Color
    @Composable @ReadOnlyComposable
    get() = LocalBackgroundSecondary.current

@Composable
fun PrdokForAndroidTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalBackgroundSecondary provides if (darkTheme) CpBackgroundSecondaryDark else CpBackgroundSecondaryLight,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
            typography = Typography,
            content = content,
        )
    }
}

/**
 * Status and navigation bar icons follow the *system* dark mode (enableEdgeToEdge sets them up
 * that way). When the app overrides its own theme, the icons have to be told too, or they
 * turn white on a light background and vanish.
 */
@Composable
fun SystemBarsAppearance(darkTheme: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return
    SideEffect {
        val window = (view.context as Activity).window
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !darkTheme
            isAppearanceLightNavigationBars = !darkTheme
        }
    }
}
