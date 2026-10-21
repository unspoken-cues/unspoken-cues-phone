package com.example.unspokenqueues.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Primary is ink, not the indigo accent: buttons stay neutral so the only saturated colors on
// screen are the four cues. The accent lives on in chips, avatars and the selected tab.
private val LightColorScheme = lightColorScheme(
    primary = Ink,
    onPrimary = Paper,
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
    primary = InkDark,
    onPrimary = PaperDark,
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
