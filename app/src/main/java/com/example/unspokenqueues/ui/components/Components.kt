package com.example.unspokenqueues.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonElevation
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.example.unspokenqueues.model.CueStatus
import com.example.unspokenqueues.model.WatchConnection
import com.example.unspokenqueues.ui.theme.CueGreen
import com.example.unspokenqueues.ui.theme.CueYellow
import com.example.unspokenqueues.ui.theme.rememberReduceMotion

@Composable
fun ScreenTitle(title: String, subtitle: String? = null) {
    Column(Modifier.padding(bottom = 20.dp)) {
        Text(title, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        if (subtitle != null) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (title != null) {
                Text(
                    title.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            content()
        }
    }
}

@Composable
fun Dot(color: Color, size: Int = 10) {
    Box(Modifier.size(size.dp).background(color, CircleShape))
}

@Composable
fun WatchPill(state: WatchConnection) {
    val color = when (state) {
        WatchConnection.CONNECTED -> CueGreen
        WatchConnection.SYNCING -> CueYellow
        WatchConnection.DISCONNECTED -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (state == WatchConnection.SYNCING) {
                // A slow pulse says "working on it" without anything flashing.
                val pulse = rememberPulse()
                Box(Modifier.graphicsLayer { alpha = pulse() }) { Dot(color, 8) }
            } else {
                Dot(color, 8)
            }
            Text(state.label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
fun Chip(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Text(
            text,
            Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

/**
 * Round profile photo. [image] is anything Coil can load (a URL or a local Uri); the person's
 * initial shows when it is null or blank, while it loads, and if it fails to load.
 */
@Composable
fun Avatar(name: String, size: Int, image: Any? = null) {
    Box(
        Modifier.size(size.dp).background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name.trim().take(1).uppercase().ifEmpty { "?" },
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
        if (image != null && image != "") {
            AsyncImage(
                model = image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(CircleShape),
            )
        }
    }
}

/**
 * A ring in [status]'s color around [content] (a profile photo), so someone's cue reads before
 * their name does. [glow] adds a soft tinted halo outside the ring; keep it for the one or two
 * biggest avatars on a screen. The words that name the cue always sit beside it.
 */
@Composable
fun Halo(
    status: CueStatus,
    modifier: Modifier = Modifier,
    glow: Boolean = false,
    gap: Dp = 3.dp,
    content: @Composable () -> Unit,
) {
    Box(
        modifier
            .then(if (glow) Modifier.padding(HaloGlowWidth) else Modifier)
            .then(
                if (glow) {
                    Modifier.drawBehind {
                        drawCircle(status.color.copy(alpha = 0.14f), radius = size.minDimension / 2 + HaloGlowWidth.toPx())
                    }
                } else {
                    Modifier
                },
            )
            .border(2.dp, status.color, CircleShape)
            .padding(gap),
    ) { content() }
}

private val HaloGlowWidth = 6.dp

/** An outlined button whose edge is [MaterialTheme.colorScheme.onSurfaceVariant], not the 1.2:1 hairline. */
@Composable
fun OutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.outlinedShape,
    colors: ButtonColors = ButtonDefaults.outlinedButtonColors(),
    elevation: ButtonElevation? = null,
    border: BorderStroke? = BorderStroke(
        1.dp,
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.12f),
    ),
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    androidx.compose.material3.OutlinedButton(
        onClick, modifier, enabled, shape, colors, elevation, border, contentPadding, interactionSource, content,
    )
}

/**
 * A slow 0.4 to 1 alpha pulse for things that are waiting (loading placeholders, syncing).
 * Holds at 1 when the user has turned animations off.
 */
@Composable
fun rememberPulse(): () -> Float {
    if (rememberReduceMotion()) return { 1f }
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "pulseAlpha",
    )
    return { pulse }
}

// A grey stand-in for content that is still loading.
@Composable
private fun SkeletonBlock(modifier: Modifier, pulse: () -> Float, shape: Shape = RoundedCornerShape(8.dp)) {
    Box(
        modifier
            .graphicsLayer { alpha = pulse() }
            .background(MaterialTheme.colorScheme.outline, shape),
    )
}

/** Placeholder rows in the shape of the event list, shown while it loads. */
@Composable
fun EventListSkeleton(rows: Int = 3) {
    val pulse = rememberPulse()
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        repeat(rows) {
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SkeletonBlock(Modifier.fillMaxWidth(0.6f).height(18.dp), pulse)
                        SkeletonBlock(Modifier.fillMaxWidth(0.85f).height(12.dp), pulse)
                    }
                    SkeletonBlock(Modifier.padding(start = 12.dp).width(64.dp).height(28.dp), pulse, RoundedCornerShape(50))
                }
            }
        }
    }
}

/** Placeholder rows in the shape of an event's attendee list, shown while it loads. */
@Composable
fun AttendeeListSkeleton(rows: Int = 3) {
    val pulse = rememberPulse()
    SectionCard {
        repeat(rows) {
            Row(
                Modifier.padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SkeletonBlock(Modifier.size(46.dp), pulse, CircleShape)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SkeletonBlock(Modifier.width(140.dp).height(14.dp), pulse)
                    SkeletonBlock(Modifier.width(96.dp).height(10.dp), pulse)
                }
            }
        }
    }
}
