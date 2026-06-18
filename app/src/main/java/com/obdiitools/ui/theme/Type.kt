package com.obdiitools.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val OBDTypography = Typography(
    displayLarge = TextStyle(
        fontFamily   = FontFamily.Monospace,
        fontWeight   = FontWeight.Bold,
        fontSize     = 57.sp,
        color        = TextPrimary,
        letterSpacing = (-0.25).sp,
    ),
    displayMedium = TextStyle(
        fontFamily   = FontFamily.Monospace,
        fontWeight   = FontWeight.Bold,
        fontSize     = 45.sp,
        color        = TextPrimary,
    ),
    headlineLarge = TextStyle(
        fontFamily   = FontFamily.Monospace,
        fontWeight   = FontWeight.Bold,
        fontSize     = 32.sp,
        color        = TextPrimary,
    ),
    headlineMedium = TextStyle(
        fontFamily   = FontFamily.Monospace,
        fontWeight   = FontWeight.SemiBold,
        fontSize     = 28.sp,
        color        = TextPrimary,
    ),
    titleLarge = TextStyle(
        fontFamily   = FontFamily.Monospace,
        fontWeight   = FontWeight.SemiBold,
        fontSize     = 22.sp,
        color        = TextPrimary,
    ),
    titleMedium = TextStyle(
        fontFamily   = FontFamily.Monospace,
        fontWeight   = FontWeight.Medium,
        fontSize     = 16.sp,
        color        = TextPrimary,
        letterSpacing = 0.15.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily   = FontFamily.Default,
        fontWeight   = FontWeight.Normal,
        fontSize     = 16.sp,
        color        = TextPrimary,
        lineHeight   = 24.sp,
        letterSpacing = 0.5.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily   = FontFamily.Default,
        fontWeight   = FontWeight.Normal,
        fontSize     = 14.sp,
        color        = TextSecondary,
        lineHeight   = 20.sp,
        letterSpacing = 0.25.sp,
    ),
    labelLarge = TextStyle(
        fontFamily   = FontFamily.Monospace,
        fontWeight   = FontWeight.Medium,
        fontSize     = 14.sp,
        color        = TextSecondary,
        letterSpacing = 0.1.sp,
    ),
    labelSmall = TextStyle(
        fontFamily   = FontFamily.Monospace,
        fontWeight   = FontWeight.Medium,
        fontSize     = 11.sp,
        color        = TextSecondary,
        letterSpacing = 0.5.sp,
    ),
)
