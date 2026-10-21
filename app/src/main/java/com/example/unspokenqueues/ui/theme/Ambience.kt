package com.example.unspokenqueues.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * A soft wash of [cue] spilling in from the top-left corner behind a screen, so the app always
 * carries a hint of the person's current status. It is faint (16%) so text on it keeps its
 * contrast, and it fades from one cue to the next when the status changes.
 */
fun Modifier.cueAmbience(cue: Color): Modifier = composed {
    val wash by animateColorAsState(cue.copy(alpha = 0.16f), tween(MotionLong, easing = EaseOutQuart), label = "ambience")
    drawBehind {
        drawRect(
            Brush.radialGradient(
                listOf(wash, Color.Transparent),
                center = Offset(0f, 0f),
                radius = size.width * 1.1f,
            ),
        )
    }
}
