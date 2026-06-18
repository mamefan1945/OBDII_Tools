package com.obdiitools.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColors = darkColorScheme(
    primary         = NeonCyan,
    onPrimary       = BackgroundDeep,
    secondary       = NeonOrange,
    onSecondary     = BackgroundDeep,
    tertiary        = NeonGreen,
    background      = BackgroundDeep,
    surface         = BackgroundCard,
    surfaceVariant  = SurfaceElevated,
    onBackground    = TextPrimary,
    onSurface       = TextPrimary,
    onSurfaceVariant= TextSecondary,
    error           = NeonRed,
    onError         = BackgroundDeep,
    outline         = SurfaceBorder,
)

@Composable
fun OBDIITheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        typography  = OBDTypography,
        content     = content,
    )
}
