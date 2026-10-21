package com.example.unspokenqueues.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.unspokenqueues.R

// Two families: Display (Bricolage Grotesque) for the cue name, screen titles and names on cards;
// Body (Figtree) for the rest. Both are variable fonts, one file each; a weight is picked by
// variation setting. Below API 26 variation settings are ignored and each weight falls back to
// the font's default instance.
@OptIn(ExperimentalTextApi::class)
private fun displayFont(weight: FontWeight) = Font(
    R.font.bricolage_grotesque,
    weight,
    // Optical size 36 suits the 24 to 45sp the display styles are set at.
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight), FontVariation.Setting("opsz", 36f)),
)

@OptIn(ExperimentalTextApi::class)
private fun bodyFont(weight: FontWeight) = Font(
    R.font.figtree,
    weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

val DisplayFamily: FontFamily = FontFamily(
    displayFont(FontWeight.Normal),
    displayFont(FontWeight.Medium),
    displayFont(FontWeight.SemiBold),
    displayFont(FontWeight.Bold),
)

val BodyFamily: FontFamily = FontFamily(
    bodyFont(FontWeight.Normal),
    bodyFont(FontWeight.Medium),
    bodyFont(FontWeight.SemiBold),
    bodyFont(FontWeight.Bold),
)

private val base = Typography()

val Typography = Typography(
    displayLarge = base.displayLarge.copy(fontFamily = DisplayFamily),
    displayMedium = base.displayMedium.copy(fontFamily = DisplayFamily),
    displaySmall = base.displaySmall.copy(fontFamily = DisplayFamily),
    headlineLarge = base.headlineLarge.copy(fontFamily = DisplayFamily),
    headlineMedium = base.headlineMedium.copy(fontFamily = DisplayFamily),
    headlineSmall = base.headlineSmall.copy(fontFamily = DisplayFamily),
    titleLarge = base.titleLarge.copy(fontFamily = BodyFamily),
    titleMedium = base.titleMedium.copy(fontFamily = BodyFamily),
    titleSmall = base.titleSmall.copy(fontFamily = BodyFamily),
    bodyLarge = TextStyle(
        fontFamily = BodyFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
    bodyMedium = base.bodyMedium.copy(fontFamily = BodyFamily),
    bodySmall = base.bodySmall.copy(fontFamily = BodyFamily),
    labelLarge = base.labelLarge.copy(fontFamily = BodyFamily),
    labelMedium = base.labelMedium.copy(fontFamily = BodyFamily),
    labelSmall = base.labelSmall.copy(fontFamily = BodyFamily),
)
