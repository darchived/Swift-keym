package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val SwiftKeyMDarkScheme = darkColorScheme(
    primary = PureWhite,
    onPrimary = BgDarkGrey,
    primaryContainer = SurfaceElevatedGrey,
    onPrimaryContainer = PureWhite,
    secondary = SoftWhite,
    onSecondary = BgDarkGrey,
    tertiary = MutedGrey,
    background = BgDarkGrey,
    onBackground = PureWhite,
    surface = SurfaceGrey,
    onSurface = PureWhite,
    surfaceVariant = SurfaceElevatedGrey,
    onSurfaceVariant = MutedGrey,
    outline = BorderGrey,
    error = DangerMuted
)

@Composable
fun SwiftKeyMTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SwiftKeyMDarkScheme,
        typography = CloudHudTypography,
        content = content
    )
}
