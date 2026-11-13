package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val SogoDarkColorScheme = darkColorScheme(
    primary = BurgundyPrimary,
    onPrimary = TextWhite,
    primaryContainer = BurgundyContainer,
    onPrimaryContainer = GoldLight,
    secondary = GoldAccent,
    onSecondary = MatteBlackBackground,
    secondaryContainer = GoldContainer,
    onSecondaryContainer = GoldLight,
    tertiary = BurgundyLight,
    onTertiary = TextWhite,
    background = MatteBlackBackground,
    onBackground = TextWhite,
    surface = MatteBlackSurface,
    onSurface = TextWhite,
    surfaceVariant = MatteBlackSurfaceVariant,
    onSurfaceVariant = TextMuted,
    outline = OutlineSubtle,
    error = AlertRed,
    onError = TextWhite
)

private val SogoLightColorScheme = lightColorScheme(
    primary = BurgundyPrimary,
    onPrimary = TextWhite,
    primaryContainer = BurgundyContainer,
    onPrimaryContainer = GoldLight,
    secondary = GoldAccent,
    onSecondary = MatteBlackBackground,
    background = MatteBlackBackground,
    onBackground = TextWhite,
    surface = MatteBlackSurface,
    onSurface = TextWhite
)

@Composable
fun SogoChatTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = SogoDarkColorScheme,
        typography = Typography,
        content = content
    )
}
