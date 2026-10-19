package org.marshsoft.timora.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

private val Base = FontFamily.SansSerif

private fun t(
    weight: FontWeight,
    size: Int,
    line: Int,
    letterSpacing: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit.Unspecified,
) = TextStyle(
    fontFamily = Base,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = letterSpacing,
)

val Typography = Typography(
    displayLarge = t(FontWeight.Bold, 57, 64, letterSpacing = (-0.25).sp),
    displayMedium = t(FontWeight.Bold, 45, 52),
    displaySmall = t(FontWeight.SemiBold, 36, 44),
    headlineLarge = t(FontWeight.SemiBold, 32, 40),
    headlineMedium = t(FontWeight.SemiBold, 28, 36),
    headlineSmall = t(FontWeight.SemiBold, 24, 32),
    titleLarge = t(FontWeight.SemiBold, 22, 28),
    titleMedium = t(FontWeight.SemiBold, 16, 24, letterSpacing = 0.15.sp),
    titleSmall = t(FontWeight.Medium, 14, 20, letterSpacing = 0.1.sp),
    bodyLarge = t(FontWeight.Normal, 16, 24, letterSpacing = 0.5.sp),
    bodyMedium = t(FontWeight.Normal, 14, 20, letterSpacing = 0.25.sp),
    bodySmall = t(FontWeight.Normal, 12, 16, letterSpacing = 0.4.sp),
    labelLarge = t(FontWeight.Medium, 14, 20, letterSpacing = 0.1.sp),
    labelMedium = t(FontWeight.Medium, 12, 16, letterSpacing = 0.5.sp),
    labelSmall = t(FontWeight.Medium, 11, 16, letterSpacing = 0.5.sp),
)

/** Very large tabular-figure style for the running timer. */
val TimerDisplayStyle = TextStyle(
    fontFamily = Base,
    fontWeight = FontWeight.Bold,
    fontSize = 88.sp,
    lineHeight = 96.sp,
    letterSpacing = (-0.02).em,
    fontFeatureSettings = "tnum",
)

/** Medium tabular style for durations in lists/editor. */
val TimerDurationStyle = TextStyle(
    fontFamily = Base,
    fontWeight = FontWeight.SemiBold,
    fontSize = 20.sp,
    lineHeight = 28.sp,
    fontFeatureSettings = "tnum",
)

val Typography.timerDisplay: TextStyle get() = TimerDisplayStyle
val Typography.timerDuration: TextStyle get() = TimerDurationStyle

@Suppress("unused")
@Composable
@ReadOnlyComposable
internal fun timerDisplayStyle(): TextStyle = MaterialTheme.typography.timerDisplay
