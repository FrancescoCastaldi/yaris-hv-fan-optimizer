package com.yaris.hvfan.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = GrRedPrimary,
    secondary = AccentCyan,
    tertiary = SuccessGreen,
    background = DarkBackground,
    surface = SurfaceDark,
    surfaceVariant = CardBackground,
    onPrimary = TextPrimary,
    onSecondary = DarkBackground,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    outline = CardBorder
)

@Composable
fun YarisHvFanTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
