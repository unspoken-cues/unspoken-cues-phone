package com.example.unspokenqueues.ui.theme

import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

// Motion is short and only ever shows a change of state. Everything decelerates; nothing bounces.

val EaseOutQuart = CubicBezierEasing(0.25f, 1f, 0.5f, 1f)
val EaseOutExpo = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

const val MotionShort = 150
const val MotionMedium = 250
const val MotionLong = 400

/**
 * Whether the user has turned animations off ("Remove animations" in accessibility settings).
 * Compose already shortens one-shot animations to nothing in that case; this is for the looping
 * ones (pulses), which should not run at all.
 */
@Composable
fun rememberReduceMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) {
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}
