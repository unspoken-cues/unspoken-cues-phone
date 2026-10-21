package com.example.unspokenqueues.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.selection.toggleable
import com.example.unspokenqueues.R
import com.example.unspokenqueues.model.CollectedCard
import com.example.unspokenqueues.model.CueStatus
import com.example.unspokenqueues.model.Profile
import com.example.unspokenqueues.model.binderCounts
import com.example.unspokenqueues.model.filterBinder
import com.example.unspokenqueues.ui.components.Avatar
import com.example.unspokenqueues.ui.components.Dot
import com.example.unspokenqueues.ui.components.Halo

/**
 * The binder: your own card at the top, how many cards you hold in each cue, a search box and cue
 * filters, then the collection as a two-column grid of little cards. Tapping a card opens that
 * person's full card.
 */
@Composable
fun BinderScreen(
    status: CueStatus,
    profile: Profile,
    collection: List<CollectedCard>,
    onRemove: (CollectedCard) -> Unit,
) {
    var opened by remember { mutableStateOf<CollectedCard?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    // Null means every cue.
    var cueFilter by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedCue = cueFilter?.let { name -> CueStatus.entries.firstOrNull { it.name == name } }
    val counts = remember(collection) { binderCounts(collection) }
    val shown = remember(collection, query, selectedCue) { filterBinder(collection, query, selectedCue) }

    opened?.let { card ->
        PersonDetail(
            card.profile, card.status,
            onRemove = {
                opened = null
                onRemove(card)
            },
        ) { opened = null }
    }
    ScreenColumn {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Binder", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                Text(
                    "Cards you've collected through S.W.A.P.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        MyCardStrip(status, profile)

        if (collection.isEmpty()) {
            EmptyBinder()
        } else {
            OutlinedTextField(
                query, { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("Search your cards") },
                leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            )
            CueFilterRow(counts, total = collection.size, selected = selectedCue, onSelect = { cueFilter = it?.name })

            if (shown.isEmpty()) {
                Text(
                    "No cards match. Clear the search or pick another cue.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            shown.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { card -> BinderCardTile(card, Modifier.weight(1f)) { opened = card } }
                    if (row.size < 2) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

/** Your own card, kept on the dark ground of a S.W.A.P. card so it reads as the one you hand out. */
@Composable
private fun MyCardStrip(status: CueStatus, profile: Profile) {
    Surface(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = Color(0xFF1B1D22),
    ) {
        Box {
            Canvas(Modifier.matchParentSize()) {
                drawCircle(status.color.copy(alpha = 0.45f), radius = size.width * 0.55f, center = Offset(0f, 0f))
            }
            Row(
                Modifier.padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Halo(status, glow = true) { Avatar(profile.displayName, 56, profile.cardPicture) }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "MY CARD",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.7f),
                    )
                    Text(
                        profile.displayName.ifBlank { "You" },
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Dot(status.color, 8)
                        Text(status.meaning, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.85f))
                    }
                }
            }
        }
    }
}

/** How many cards are in each cue, as filters. "All" clears the filter; each pill names its cue in words. */
@Composable
private fun CueFilterRow(
    counts: Map<CueStatus, Int>,
    total: Int,
    selected: CueStatus?,
    onSelect: (CueStatus?) -> Unit,
) {
    val options: List<CueStatus?> = listOf(null) + CueStatus.entries
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(options, key = { it?.name ?: "all" }) { cue ->
            val on = cue == selected
            Surface(
                Modifier
                    .height(40.dp)
                    .clip(CircleShape)
                    .toggleable(value = on, role = Role.RadioButton, onValueChange = { onSelect(if (on) null else cue) }),
                shape = CircleShape,
                color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                contentColor = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                border = if (on) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
                Row(
                    Modifier.padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (cue != null) Dot(cue.color, 10)
                    Text(
                        "${cue?.label ?: "All"} · ${if (cue == null) total else counts[cue] ?: 0}",
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}

/**
 * One collected card as a small trading card: a field in their cue color with their photo set in
 * it, then their name and what their cue means in words.
 */
@Composable
private fun BinderCardTile(card: CollectedCard, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        modifier.aspectRatio(0.74f).clip(RoundedCornerShape(24.dp)).clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column {
            Box(
                Modifier.fillMaxWidth().weight(0.46f).background(card.status.color),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    drawCircle(Color.White.copy(alpha = 0.16f), radius = size.height * 0.9f, center = Offset(0f, size.height))
                    drawCircle(Color.Black.copy(alpha = 0.07f), radius = size.height * 0.6f, center = Offset(size.width, 0f))
                }
                Box(Modifier.background(Color.White, CircleShape).padding(3.dp)) {
                    Avatar(card.name, 64, card.profile.cardPicture)
                }
            }
            Column(
                Modifier.fillMaxWidth().weight(0.54f).padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    card.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                StatusLabel(card.status)
            }
        }
    }
}

@Composable
private fun EmptyBinder() {
    Column(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CueStatus.entries.forEach { Dot(it.color, 14) }
        }
        Text("No cards yet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            "Swap with someone from the QR tab and their card lands here.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
