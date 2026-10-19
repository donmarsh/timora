package org.marshsoft.timora.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Resolves whether dark theme should be used. */
fun ThemeMode.isDark(systemDark: Boolean): Boolean = when (this) {
    ThemeMode.SYSTEM -> systemDark
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

enum class SegmentTypeKey { WORK, REST, PREP, COOLDOWN }

@Immutable
data class SegmentColors(val container: Color, val onContainer: Color, val accent: Color)

@Immutable
data class SegmentPalette(
    val work: SegmentColors,
    val rest: SegmentColors,
    val prep: SegmentColors,
    val cooldown: SegmentColors,
) {
    operator fun get(type: SegmentTypeKey): SegmentColors = when (type) {
        SegmentTypeKey.WORK -> work
        SegmentTypeKey.REST -> rest
        SegmentTypeKey.PREP -> prep
        SegmentTypeKey.COOLDOWN -> cooldown
    }
}

internal val LightSegmentPalette = SegmentPalette(
    work = SegmentColors(Color(0xFFFFDBCF), Color(0xFF3A0B00), Color(0xFFD9512A)),
    rest = SegmentColors(Color(0xFFCDE8F7), Color(0xFF001E2E), Color(0xFF1F78A8)),
    prep = SegmentColors(Color(0xFFFFE9A8), Color(0xFF241A00), Color(0xFFB88700)),
    cooldown = SegmentColors(Color(0xFFE3DDFF), Color(0xFF1B1048), Color(0xFF6B58C9)),
)

internal val DarkSegmentPalette = SegmentPalette(
    work = SegmentColors(Color(0xFF7A2A0F), Color(0xFFFFDBCF), Color(0xFFFF8F6B)),
    rest = SegmentColors(Color(0xFF0C4A6B), Color(0xFFCDE8F7), Color(0xFF7CC4EE)),
    prep = SegmentColors(Color(0xFF5A4300), Color(0xFFFFE9A8), Color(0xFFF2C94C)),
    cooldown = SegmentColors(Color(0xFF3F3190), Color(0xFFE3DDFF), Color(0xFFB4A7FF)),
)

/** Eight card accents, tuned to read on both light and dark surfaces. */
internal val AccentColors: List<Color> = listOf(
    Color(0xFF0F9D8F), // teal
    Color(0xFFE5603A), // coral
    Color(0xFF3B82C4), // blue
    Color(0xFF8B6BD6), // violet
    Color(0xFFD9A400), // amber
    Color(0xFFD6477A), // rose
    Color(0xFF55A630), // green
    Color(0xFF5C6BC0), // indigo
)

/** Wraps any (including negative) index into the accent list. */
fun accentIndex(colorIndex: Int): Int = Math.floorMod(colorIndex, AccentColors.size)

fun cardAccentColor(colorIndex: Int): Color = AccentColors[accentIndex(colorIndex)]

val CardAccentCount: Int get() = AccentColors.size

fun segmentColorsFor(type: SegmentTypeKey, dark: Boolean): SegmentColors =
    (if (dark) DarkSegmentPalette else LightSegmentPalette)[type]

private val LightColorScheme = lightColorScheme(
    primary = LPrimary, onPrimary = LOnPrimary, primaryContainer = LPrimaryContainer,
    onPrimaryContainer = LOnPrimaryContainer, secondary = LSecondary, onSecondary = LOnSecondary,
    secondaryContainer = LSecondaryContainer, onSecondaryContainer = LOnSecondaryContainer,
    tertiary = LTertiary, onTertiary = LOnTertiary, tertiaryContainer = LTertiaryContainer,
    onTertiaryContainer = LOnTertiaryContainer, error = LError, onError = LOnError,
    errorContainer = LErrorContainer, onErrorContainer = LOnErrorContainer,
    background = LBackground, onBackground = LOnBackground, surface = LSurface,
    onSurface = LOnSurface, surfaceVariant = LSurfaceVariant, onSurfaceVariant = LOnSurfaceVariant,
    outline = LOutline, outlineVariant = LOutlineVariant, inverseSurface = LInverseSurface,
    inverseOnSurface = LInverseOnSurface, inversePrimary = LInversePrimary,
    surfaceDim = LSurfaceDim, surfaceBright = LSurfaceBright,
    surfaceContainerLowest = LSurfaceContainerLowest, surfaceContainerLow = LSurfaceContainerLow,
    surfaceContainer = LSurfaceContainer, surfaceContainerHigh = LSurfaceContainerHigh,
    surfaceContainerHighest = LSurfaceContainerHighest,
)

private val DarkColorScheme = darkColorScheme(
    primary = DPrimary, onPrimary = DOnPrimary, primaryContainer = DPrimaryContainer,
    onPrimaryContainer = DOnPrimaryContainer, secondary = DSecondary, onSecondary = DOnSecondary,
    secondaryContainer = DSecondaryContainer, onSecondaryContainer = DOnSecondaryContainer,
    tertiary = DTertiary, onTertiary = DOnTertiary, tertiaryContainer = DTertiaryContainer,
    onTertiaryContainer = DOnTertiaryContainer, error = DError, onError = DOnError,
    errorContainer = DErrorContainer, onErrorContainer = DOnErrorContainer,
    background = DBackground, onBackground = DOnBackground, surface = DSurface,
    onSurface = DOnSurface, surfaceVariant = DSurfaceVariant, onSurfaceVariant = DOnSurfaceVariant,
    outline = DOutline, outlineVariant = DOutlineVariant, inverseSurface = DInverseSurface,
    inverseOnSurface = DInverseOnSurface, inversePrimary = DInversePrimary,
    surfaceDim = DSurfaceDim, surfaceBright = DSurfaceBright,
    surfaceContainerLowest = DSurfaceContainerLowest, surfaceContainerLow = DSurfaceContainerLow,
    surfaceContainer = DSurfaceContainer, surfaceContainerHigh = DSurfaceContainerHigh,
    surfaceContainerHighest = DSurfaceContainerHighest,
)

private val LocalSegmentPalette = staticCompositionLocalOf { LightSegmentPalette }
private val LocalIsDark = staticCompositionLocalOf { false }

@Composable
fun TimoraTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dark = themeMode.isDark(isSystemInDarkTheme())
    val colorScheme: ColorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        dark -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.setDecorFitsSystemWindows(window, false)
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !dark
            controller.isAppearanceLightNavigationBars = !dark
        }
    }

    CompositionLocalProvider(
        LocalSegmentPalette provides (if (dark) DarkSegmentPalette else LightSegmentPalette),
        LocalIsDark provides dark,
    ) {
        MaterialTheme(colorScheme = colorScheme, typography = Typography, shapes = TimoraShapes, content = content)
    }
}

object TimoraTheme {
    /** Colors for a segment type in the current theme. */
    @Composable
    @ReadOnlyComposable
    fun segmentColors(type: SegmentTypeKey): SegmentColors = LocalSegmentPalette.current[type]

    /** Card accent for a timer's colorIndex (wraps modulo 8, negatives safe). */
    fun cardAccent(colorIndex: Int): Color = cardAccentColor(colorIndex)

    val isDark: Boolean
        @Composable @ReadOnlyComposable get() = LocalIsDark.current

    val timerDisplay get() = TimerDisplayStyle
}
