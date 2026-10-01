package com.example.unspokenqueues.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    background = Paper,
    onBackground = Ink,
    surface = Card,
    onSurface = Ink,
    surfaceVariant = Paper,
    onSurfaceVariant = InkSoft,
    outline = Line,
    outlineVariant = Line,
    secondaryContainer = Color(0xFFE3E6FA),
    onSecondaryContainer = Accent,
)

private val DarkColorScheme = darkColorScheme(
    primary = AccentDark,
    onPrimary = Color(0xFF0E1140),
    background = PaperDark,
    onBackground = InkDark,
    surface = CardDark,
    onSurface = InkDark,
    surfaceVariant = PaperDark,
    onSurfaceVariant = InkSoftDark,
    outline = LineDark,
    outlineVariant = LineDark,
    secondaryContainer = Color(0xFF2A2F5C),
    onSecondaryContainer = AccentDark,
)

// Dynamic color is off on purpose: the four cue colors must stay consistent everywhere.
@Composable
fun UnspokenQueuesTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        content = content
    )
}
