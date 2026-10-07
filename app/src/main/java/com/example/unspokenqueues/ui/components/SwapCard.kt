package com.example.unspokenqueues.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.example.unspokenqueues.model.CueStatus
import com.example.unspokenqueues.model.Profile
import kotlin.random.Random

// The card stays dark in both themes so every cue color (yellow included) reads against it.
private val CardInk = Color(0xFF1B1D22)
private val QrInk = Color(0xFF111111)

/**
 * A collectible S.W.A.P. card built from a profile. Pass [qrId] to show the scannable code
 * in the corner; [onQrClick] makes that code tappable (e.g. to enlarge it).
 */
@Composable
fun SwapCard(
    profile: Profile,
    status: CueStatus,
    modifier: Modifier = Modifier,
    qrId: String? = null,
    onQrClick: (() -> Unit)? = null,
) {
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp), color = CardInk) {
        Box(
            Modifier.background(
                Brush.radialGradient(
                    listOf(status.color.copy(alpha = 0.55f), Color.Transparent),
                    center = Offset(0f, 0f),
                    radius = 900f,
                ),
            ),
        ) {
            Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "S.W.A.P.",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.7f),
                    )
                    Spacer(Modifier.weight(1f))
                    StatusPill(status)
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(
                        Modifier.size(56.dp).border(2.dp, status.color, CircleShape).padding(4.dp)
                            .background(Color.White.copy(alpha = 0.12f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            profile.displayName.trim().take(1).uppercase().ifEmpty { "?" },
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                        )
                        if (profile.avatarUrl.isNotBlank()) {
                            AsyncImage(
                                model = profile.avatarUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize().clip(CircleShape),
                            )
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            profile.displayName,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(status.meaning, style = MaterialTheme.typography.bodyMedium, color = status.color)
                    }
                }

                if (profile.bio.isNotBlank()) {
                    Text(profile.bio, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.85f))
                }

                Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.12f)))

                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        CardList("Prefers", profile.preferences)
                        CardList("Boundaries", profile.boundaries)
                    }
                    if (qrId != null) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                Modifier.size(92.dp).clip(RoundedCornerShape(14.dp)).background(Color.White)
                                    .then(if (onQrClick != null) Modifier.clickable(onClick = onQrClick) else Modifier)
                                    .padding(8.dp),
                            ) { QrPlaceholder(qrId) }
                            if (onQrClick != null) {
                                Text(
                                    "Tap to enlarge",
                                    Modifier.padding(top = 6.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.6f),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusPill(status: CueStatus) {
    Row(
        Modifier.background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Dot(status.color, 8)
        Text(status.label, style = MaterialTheme.typography.labelMedium, color = Color.White)
    }
}

@Composable
private fun CardList(title: String, items: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title.uppercase(), style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.55f))
        if (items.isEmpty()) {
            Text("—", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.6f))
        }
        items.take(2).forEach {
            Text(
                "· $it",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (items.size > 2) {
            Text("+${items.size - 2} more", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.55f))
        }
    }
}

/** Full-screen, high-contrast QR for scanning. Tap anywhere to close. */
@Composable
fun FullScreenQr(qrId: String, name: String, status: CueStatus, onDismiss: () -> Unit) {
    Dialog(onDismiss, DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxSize().background(Color.White).clickable(onClick = onDismiss).padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, color = QrInk)
            Spacer(Modifier.height(24.dp))
            Box(
                Modifier.fillMaxWidth().aspectRatio(1f).border(4.dp, status.color, RoundedCornerShape(20.dp)).padding(20.dp),
            ) { QrPlaceholder(qrId) }
            Spacer(Modifier.height(24.dp))
            Text(
                "Tap anywhere to close",
                style = MaterialTheme.typography.bodyMedium,
                color = QrInk.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
            )
        }
    }
}

// Visual stand-in only. Real QR generation (opaque ID + resolver) is pending the Sprint 1 decision.
// Always black on white so it stays scannable in dark mode.
@Composable
fun QrPlaceholder(seed: String) {
    Canvas(Modifier.fillMaxSize()) {
        val n = 25
        val cell = size.minDimension / n
        val rnd = Random(seed.hashCode())
        fun finder(cx: Int, cy: Int) = cx < 7 && cy < 7 || cx >= n - 7 && cy < 7 || cx < 7 && cy >= n - 7
        for (y in 0 until n) for (x in 0 until n) {
            if (!finder(x, y) && rnd.nextBoolean()) {
                drawRect(QrInk, Offset(x * cell, y * cell), Size(cell, cell))
            }
        }
        listOf(0 to 0, n - 7 to 0, 0 to n - 7).forEach { (fx, fy) ->
            val o = Offset(fx * cell, fy * cell)
            drawRect(QrInk, o, Size(cell * 7, cell * 7))
            drawRect(Color.White, o + Offset(cell, cell), Size(cell * 5, cell * 5))
            drawRect(QrInk, o + Offset(cell * 2, cell * 2), Size(cell * 3, cell * 3))
        }
    }
}
