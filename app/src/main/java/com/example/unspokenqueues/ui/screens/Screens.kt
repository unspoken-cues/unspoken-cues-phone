package com.example.unspokenqueues.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.unspokenqueues.model.CollectedCard
import com.example.unspokenqueues.model.CueStatus
import com.example.unspokenqueues.model.MockData
import com.example.unspokenqueues.model.Profile
import com.example.unspokenqueues.model.WatchConnection
import com.example.unspokenqueues.ui.components.Chip
import com.example.unspokenqueues.ui.components.Dot
import com.example.unspokenqueues.ui.components.ScreenTitle
import com.example.unspokenqueues.ui.components.SectionCard
import com.example.unspokenqueues.ui.components.WatchPill
import kotlin.random.Random

@Composable
private fun ScreenColumn(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) { content() }
}

// ---------- 1. Sign in ----------

@Composable
fun SignInScreen(onSignIn: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CueStatus.entries.forEach { Dot(it.color, 14) }
        }
        Spacer(Modifier.height(20.dp))
        Text("Unspoken Cues", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold)
        Text(
            "Let people know how you're doing — without saying a word.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(32.dp))
        OutlinedTextField(email, { email = it }, label = { Text("Email") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            password, { password = it }, label = { Text("Password") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(24.dp))
        Button(onSignIn, Modifier.fillMaxWidth().height(52.dp)) { Text("Sign in") }
        TextButton(onSignIn, Modifier.fillMaxWidth()) { Text("Create an account") }
    }
}

// ---------- 2. Cue (status) ----------

@Composable
fun CueScreen(status: CueStatus, watch: WatchConnection, onStatusChange: (CueStatus) -> Unit) {
    val heroColor by animateColorAsState(status.color, label = "cue")
    ScreenColumn {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("My Cue", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            WatchPill(watch)
        }

        Surface(
            Modifier.fillMaxWidth().height(220.dp),
            shape = RoundedCornerShape(28.dp),
            color = heroColor,
        ) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.Bottom) {
                Text("CURRENTLY", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.8f))
                Text(status.label, style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold, color = Color.White)
                Text(status.meaning, style = MaterialTheme.typography.titleMedium, color = Color.White.copy(alpha = 0.9f))
            }
        }

        Text("Change status", style = MaterialTheme.typography.titleMedium)
        CueStatus.entries.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { cue -> CueOption(cue, cue == status, Modifier.weight(1f)) { onStatusChange(cue) } }
            }
        }
        Text(
            "Updates sync to your watch automatically.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CueOption(cue: CueStatus, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        modifier = modifier.clip(RoundedCornerShape(20.dp)).clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = if (selected) cue.color.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) cue.color else MaterialTheme.colorScheme.outline),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Dot(cue.color, 22)
            Text(cue.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(cue.meaning, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ---------- 3. Profile ----------

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileScreen(profile: Profile, onEdit: () -> Unit, onVisibilityChange: (Boolean) -> Unit) {
    val p = profile
    ScreenColumn {
        ScreenTitle("Profile")
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Avatar(p.displayName, 72)
            Column(Modifier.weight(1f)) {
                Text(p.displayName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                if (p.bio.isNotBlank()) {
                    Text(p.bio, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        OutlinedButton(onEdit, Modifier.fillMaxWidth()) { Text("Edit profile") }

        SectionCard(title = "Preferences") { ChipsOrEmpty(p.preferences, "No preferences yet") }
        SectionCard(title = "Boundaries") { ChipsOrEmpty(p.boundaries, "No boundaries yet") }
        SectionCard(title = "Privacy") {
            ToggleRow("Public profile", "Anyone who scans your QR can view it", p.isPublic, onVisibilityChange)
        }
    }
}

@Composable
private fun Avatar(name: String, size: Int) {
    Box(
        Modifier.size(size.dp).background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name.trim().take(1).uppercase().ifEmpty { "?" },
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipsOrEmpty(items: List<String>, emptyText: String, onRemove: ((String) -> Unit)? = null) {
    if (items.isEmpty()) {
        Text(emptyText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { item ->
            if (onRemove == null) Chip(item) else Chip("$item  ✕", Modifier.clip(RoundedCornerShape(50)).clickable { onRemove(item) })
        }
    }
}

// ---------- 3b. Edit profile ----------

@Composable
fun EditProfileScreen(initial: Profile, onSave: (Profile) -> Unit, onCancel: () -> Unit) {
    var name by remember { mutableStateOf(initial.displayName) }
    var bio by remember { mutableStateOf(initial.bio) }
    var preferences by remember { mutableStateOf(initial.preferences) }
    var boundaries by remember { mutableStateOf(initial.boundaries) }
    var isPublic by remember { mutableStateOf(initial.isPublic) }

    ScreenColumn {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onCancel) { Text("Cancel") }
            Spacer(Modifier.weight(1f))
            Text("Edit profile", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            TextButton(
                onClick = { onSave(Profile(name.trim(), bio.trim(), preferences, boundaries, isPublic)) },
                enabled = name.isNotBlank(),
            ) { Text("Save") }
        }

        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Avatar(name, 88)
            TextButton({}) { Text("Change photo") }
        }

        SectionCard(title = "Identity") {
            OutlinedTextField(
                name, { name = it }, label = { Text("Display name") }, singleLine = true,
                isError = name.isBlank(),
                supportingText = if (name.isBlank()) { { Text("Name is required") } } else null,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                bio, { if (it.length <= 120) bio = it }, label = { Text("Bio") },
                supportingText = { Text("${bio.length}/120") },
                minLines = 2, maxLines = 4, modifier = Modifier.fillMaxWidth(),
            )
        }

        EditableListCard("Preferences", "e.g. Text over calls", preferences) { preferences = it }
        EditableListCard("Boundaries", "e.g. No photos", boundaries) { boundaries = it }

        SectionCard(title = "Privacy") {
            ToggleRow("Public profile", "Anyone who scans your QR can view it", isPublic) { isPublic = it }
        }
    }
}

@Composable
private fun EditableListCard(title: String, hint: String, items: List<String>, onChange: (List<String>) -> Unit) {
    var draft by remember { mutableStateOf("") }
    fun add() {
        val v = draft.trim()
        if (v.isNotEmpty() && v !in items) onChange(items + v)
        draft = ""
    }
    SectionCard(title = title) {
        ChipsOrEmpty(items, "Nothing added yet", onRemove = { onChange(items - it) })
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                draft, { draft = it }, placeholder = { Text(hint) }, singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { add() }),
                modifier = Modifier.weight(1f),
            )
            Button(::add, enabled = draft.isNotBlank()) { Text("Add") }
        }
    }
}

// ---------- 4. QR ----------

@Composable
fun QrScreen(status: CueStatus, profile: Profile) {
    ScreenColumn {
        ScreenTitle("My QR", "Let others scan to see your public profile.")
        SectionCard {
            Box(
                Modifier.fillMaxWidth().aspectRatio(1f).border(3.dp, status.color, RoundedCornerShape(16.dp)).padding(20.dp),
            ) { QrPlaceholder(MockData.qrId) }
            Text(
                profile.displayName,
                Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button({}, Modifier.weight(1f).height(52.dp)) { Text("Share") }
            OutlinedButton({}, Modifier.weight(1f).height(52.dp)) { Text("Scan a code") }
        }
    }
}

// Visual stand-in only. Real QR generation (opaque ID + resolver) is pending the Sprint 1 decision.
@Composable
private fun QrPlaceholder(seed: String) {
    val ink = MaterialTheme.colorScheme.onSurface
    val paper = MaterialTheme.colorScheme.surface
    Canvas(Modifier.fillMaxSize()) {
        val n = 25
        val cell = size.minDimension / n
        val rnd = Random(seed.hashCode())
        fun finder(cx: Int, cy: Int) = cx < 7 && cy < 7 || cx >= n - 7 && cy < 7 || cx < 7 && cy >= n - 7
        for (y in 0 until n) for (x in 0 until n) {
            if (!finder(x, y) && rnd.nextBoolean()) {
                drawRect(ink, Offset(x * cell, y * cell), Size(cell, cell))
            }
        }
        listOf(0 to 0, n - 7 to 0, 0 to n - 7).forEach { (fx, fy) ->
            val o = Offset(fx * cell, fy * cell)
            drawRect(ink, o, Size(cell * 7, cell * 7))
            drawRect(paper, o + Offset(cell, cell), Size(cell * 5, cell * 5))
            drawRect(ink, o + Offset(cell * 2, cell * 2), Size(cell * 3, cell * 3))
        }
    }
}

// ---------- 5. S.W.A.P. / Binder ----------

@Composable
fun BinderScreen(status: CueStatus, profile: Profile) {
    ScreenColumn {
        ScreenTitle("Binder", "Cards you've collected through S.W.A.P.")
        Surface(
            Modifier.fillMaxWidth().height(160.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.onSurface,
        ) {
            Row(Modifier.padding(20.dp)) {
                Box(Modifier.width(6.dp).fillMaxHeightBar(status.color))
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.SpaceBetween) {
                    Text("MY CARD", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
                    Column {
                        Text(profile.displayName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.surface)
                        Text(status.meaning, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f))
                    }
                }
            }
        }
        Button({}, Modifier.fillMaxWidth().height(52.dp)) { Text("Collect My Card") }

        Text("Collection · ${MockData.collection.size}", style = MaterialTheme.typography.titleMedium)
        MockData.collection.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { CollectedTile(it, Modifier.weight(1f)) }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

private fun Modifier.fillMaxHeightBar(color: Color) =
    this.height(120.dp).background(color, RoundedCornerShape(3.dp))

@Composable
private fun CollectedTile(card: CollectedCard, modifier: Modifier) {
    SectionCard(modifier.aspectRatio(0.8f)) {
        Dot(card.status.color, 16)
        Spacer(Modifier.weight(1f))
        Text(card.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    }
}

// ---------- 6. Settings / Pairing ----------

@Composable
fun SettingsScreen(watch: WatchConnection, onReconnect: () -> Unit, onSignOut: () -> Unit) {
    var demoMode by remember { mutableStateOf(false) }
    ScreenColumn {
        ScreenTitle("Settings")
        SectionCard(title = "Watch") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Wear OS watch", style = MaterialTheme.typography.titleMedium)
                    Text("Last synced just now", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                WatchPill(watch)
            }
            OutlinedButton(onReconnect, Modifier.fillMaxWidth()) {
                Text(if (watch == WatchConnection.CONNECTED) "Disconnect" else "Reconnect")
            }
        }
        SectionCard(title = "App") {
            ToggleRow("Tutorial / demo mode", "Walk through the app with sample data", demoMode) { demoMode = it }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            Text("About Unspoken Cues", style = MaterialTheme.typography.bodyLarge)
        }
        TextButton(onSignOut, Modifier.fillMaxWidth()) {
            Text("Sign out", color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked, onChange)
    }
}
