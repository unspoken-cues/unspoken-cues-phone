package com.example.unspokenqueues.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.unspokenqueues.R
import com.example.unspokenqueues.model.Attendee
import com.example.unspokenqueues.model.CollectedCard
import com.example.unspokenqueues.model.CueStatus
import com.example.unspokenqueues.model.Event
import com.example.unspokenqueues.model.Profile
import com.example.unspokenqueues.model.SwapResult
import com.example.unspokenqueues.model.ThemeMode
import com.example.unspokenqueues.model.WatchConnection
import com.example.unspokenqueues.model.cardTokenFrom
import com.example.unspokenqueues.ui.components.Avatar
import com.example.unspokenqueues.ui.components.Chip
import com.example.unspokenqueues.ui.components.Dot
import com.example.unspokenqueues.ui.components.ScreenTitle
import com.example.unspokenqueues.ui.components.FullScreenQr
import com.example.unspokenqueues.ui.components.QrScanner
import com.example.unspokenqueues.ui.components.SectionCard
import com.example.unspokenqueues.ui.components.SwapCard
import com.example.unspokenqueues.ui.components.WatchPill
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.io.File

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
fun SignInScreen(
    onSignIn: suspend (email: String, password: String) -> Unit,
    onSignUp: suspend (email: String, password: String) -> Unit,
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun submit(signUp: Boolean) {
        if (loading) return
        error = null
        if (email.isBlank() || password.isBlank()) {
            error = "Enter your email and password."
            return
        }
        loading = true
        scope.launch {
            try {
                if (signUp) onSignUp(email, password) else onSignIn(email, password)
            } catch (e: Exception) {
                error = e.message ?: "Something went wrong. Please try again."
            } finally {
                loading = false
            }
        }
    }

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
        OutlinedTextField(
            email, { email = it }, label = { Text("Email") }, singleLine = true,
            enabled = !loading, modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            password, { password = it }, label = { Text("Password") }, singleLine = true,
            enabled = !loading, visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        if (error != null) {
            Spacer(Modifier.height(12.dp))
            Text(error!!, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = { submit(signUp = false) },
            enabled = !loading,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            if (loading) {
                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
            } else {
                Text("Sign in")
            }
        }
        TextButton(
            onClick = { submit(signUp = true) },
            enabled = !loading,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Create an account") }
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
private fun EventAttendees(event: Event, attendees: List<Attendee>) {
    Column(Modifier.padding(top = 8.dp)) {
        Text("At this event", style = MaterialTheme.typography.titleMedium)
        Text(
            "${event.name} · ${event.details}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    SectionCard(title = "${attendees.size} registered") {
        attendees.forEachIndexed { i, person ->
            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            AttendeeRow(person)
        }
    }
}

@Composable
private fun AttendeeRow(person: Attendee) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.border(2.dp, person.status.color, CircleShape).padding(3.dp)) { Avatar(person.name, 40, person.profile.avatarUrl) }
        Text(person.name, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Dot(person.status.color, 12)
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

// ---------- 2. Events ----------

// Placeholder: the events experience is built out in a later sprint.
@Composable
fun EventsScreen() {
    ScreenColumn {
        ScreenTitle("Events", "Find and join events near you.")
        SectionCard {
            Text("Coming soon", style = MaterialTheme.typography.titleMedium)
            Text(
                "Events you join will show up here.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ---------- 3. Profile ----------

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileScreen(
    profile: Profile,
    onEdit: () -> Unit,
    onVisibilityChange: (Boolean) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val p = profile
    ScreenColumn {
        Row(verticalAlignment = Alignment.Top) {
            Box(Modifier.weight(1f)) { ScreenTitle("Profile") }
            IconButton(onOpenSettings) {
                Icon(painterResource(R.drawable.ic_settings), contentDescription = "Settings")
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Avatar(p.displayName, 72, p.avatarUrl)
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
fun EditProfileScreen(
    initial: Profile,
    email: String?,
    onUploadAvatar: suspend (Uri) -> String,
    onSave: (Profile) -> Unit,
    onCancel: () -> Unit,
) {
    var name by remember { mutableStateOf(initial.displayName) }
    var bio by remember { mutableStateOf(initial.bio) }
    var preferences by remember { mutableStateOf(initial.preferences) }
    var boundaries by remember { mutableStateOf(initial.boundaries) }
    var isPublic by remember { mutableStateOf(initial.isPublic) }
    var avatarUrl by remember { mutableStateOf(initial.avatarUrl) }
    // A photo chosen or taken on this screen. It is only uploaded when the user taps Save.
    // Saveable because Android may recreate the activity while the camera app is in front.
    var pickedPhoto by rememberSaveable { mutableStateOf<Uri?>(null) }
    var pendingCapture by rememberSaveable { mutableStateOf<Uri?>(null) }
    var photoMenuOpen by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var photoError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) pickedPhoto = uri
    }
    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        if (saved) pickedPhoto = pendingCapture
    }
    fun launchCamera() {
        // The camera app writes the photo into our cache through a FileProvider URI. A new file
        // per capture keeps the preview from showing a previously cached shot.
        val file = File(context.cacheDir, "captures/avatar_${System.currentTimeMillis()}.jpg")
        file.parentFile?.mkdirs()
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        pendingCapture = uri
        takePhoto.launch(uri)
    }
    val requestCamera = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launchCamera() else photoError = "Camera permission is needed to take a photo."
    }

    fun save() {
        if (saving) return
        saving = true
        photoError = null
        scope.launch {
            try {
                val url = pickedPhoto?.let { onUploadAvatar(it) } ?: avatarUrl
                onSave(Profile(name.trim(), bio.trim(), preferences, boundaries, isPublic, url))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                photoError = "Couldn't upload your photo. Check your connection and try again."
            } finally {
                saving = false
            }
        }
    }

    ScreenColumn {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onCancel) { Text("Cancel") }
            Spacer(Modifier.weight(1f))
            Text("Edit profile", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            TextButton(
                onClick = ::save,
                enabled = name.isNotBlank() && !saving,
            ) { Text(if (saving) "Saving…" else "Save") }
        }

        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Avatar(name, 88, pickedPhoto ?: avatarUrl)
            Box {
                TextButton({ photoMenuOpen = true }, enabled = !saving) { Text("Change photo") }
                DropdownMenu(photoMenuOpen, { photoMenuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Choose from gallery") },
                        onClick = {
                            photoMenuOpen = false
                            photoError = null
                            pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Take photo") },
                        onClick = {
                            photoMenuOpen = false
                            photoError = null
                            // Launching the camera app throws unless the declared CAMERA permission is granted.
                            val granted = ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) ==
                                PackageManager.PERMISSION_GRANTED
                            if (granted) launchCamera() else requestCamera.launch(android.Manifest.permission.CAMERA)
                        },
                    )
                    if (pickedPhoto != null || avatarUrl.isNotBlank()) {
                        DropdownMenuItem(
                            text = { Text("Remove photo") },
                            onClick = {
                                photoMenuOpen = false
                                pickedPhoto = null
                                avatarUrl = ""
                            },
                        )
                    }
                }
            }
            photoError?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }

        SectionCard(title = "Identity") {
            if (email != null) {
                // The sign-in email is shown for reference only; it can't be changed here.
                OutlinedTextField(
                    email, {}, label = { Text("Email") }, singleLine = true, readOnly = true, enabled = false,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
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

/**
 * The user's own card with its QR code, plus the ways to swap: share the link, scan someone's
 * code, or type in a link they sent. [cardLink] is null until the user's code has loaded;
 * [cardLinkFailed] says loading it went wrong. [onCollect] receives whatever was scanned or
 * pasted and is expected to report the outcome itself.
 */
@Composable
fun QrScreen(
    status: CueStatus,
    profile: Profile,
    cardLink: String?,
    cardLinkFailed: Boolean,
    onCollect: (String) -> Unit,
) {
    var qrExpanded by remember { mutableStateOf(false) }
    var sharing by remember { mutableStateOf(false) }
    var scanning by rememberSaveable { mutableStateOf(false) }
    var enteringLink by rememberSaveable { mutableStateOf(false) }
    ScreenColumn {
        ScreenTitle("My Card", "Scan each other's code or send your link to swap cards.")
        SwapCard(profile, status, qrId = cardLink, onQrClick = { qrExpanded = true })
        if (cardLink == null && cardLinkFailed) {
            Text(
                "Couldn't load your code. Check your connection, then open this tab again.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button({ sharing = true }, Modifier.weight(1f).height(52.dp), enabled = cardLink != null) { Text("Share") }
            OutlinedButton({ scanning = true }, Modifier.weight(1f).height(52.dp)) { Text("Scan a code") }
        }
        TextButton({ enteringLink = true }, Modifier.fillMaxWidth()) { Text("Enter a link instead") }
    }
    if (cardLink != null) {
        if (qrExpanded) {
            FullScreenQr(cardLink, profile.displayName, status) { qrExpanded = false }
        }
        if (sharing) {
            ShareCardSheet(profile.displayName, cardLink) { sharing = false }
        }
    }
    if (scanning) {
        QrScanner(
            onCode = {
                scanning = false
                onCollect(it)
            },
            onDismiss = { scanning = false },
            hint = "Point the camera at someone's card code",
        )
    }
    if (enteringLink) {
        EnterLinkDialog(
            onSubmit = {
                enteringLink = false
                onCollect(it)
            },
            onDismiss = { enteringLink = false },
        )
    }
}

// For a card link that arrived as text (a message, an email) rather than as a QR code.
@Composable
private fun EnterLinkDialog(onSubmit: (String) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    val valid = cardTokenFrom(text) != null
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Enter a card link") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Paste the link someone sent you. Pasting their whole message works too.")
                OutlinedTextField(
                    text, { text = it }, label = { Text("Card link") },
                    isError = text.isNotBlank() && !valid,
                    supportingText = if (text.isNotBlank() && !valid) { { Text("That doesn't contain a card link.") } } else null,
                    maxLines = 4, modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton({ onSubmit(text) }, enabled = valid) { Text("Swap cards") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}

/** Asks before swapping when a card link opened the app, since nobody tapped "swap" in the app itself. */
@Composable
fun ConfirmLinkSwapDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Swap cards?") },
        text = { Text("You opened a card link. Swapping adds their card to your binder and puts your card in theirs.") },
        confirmButton = { TextButton(onConfirm) { Text("Swap cards") } },
        dismissButton = { TextButton(onDismiss) { Text("Not now") } },
    )
}

/** Tells the user how collecting a card ended. */
@Composable
fun SwapResultDialog(result: SwapResult, onViewBinder: () -> Unit, onDismiss: () -> Unit) {
    when (result) {
        is SwapResult.Swapped -> {
            val whose = result.card.name.takeIf { it.isNotBlank() }?.let { "$it's" } ?: "Their"
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("Cards swapped") },
                text = { Text("$whose card is in your binder, and yours is in theirs.") },
                confirmButton = { TextButton(onViewBinder) { Text("View binder") } },
                dismissButton = { TextButton(onDismiss) { Text("Done") } },
            )
        }
        is SwapResult.Failed -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Couldn't swap cards") },
            text = { Text(result.message) },
            confirmButton = { TextButton(onDismiss) { Text("OK") } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShareCardSheet(name: String, link: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Share your card", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(
                "Anyone with this link can swap cards with you. If tapping it doesn't open the app, " +
                    "they can paste it under My Card → Enter a link instead.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Surface(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Text(
                    link,
                    Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = {
                        val clipboard = context.getSystemService(ClipboardManager::class.java)
                        clipboard.setPrimaryClip(ClipData.newPlainText("Swap card link", link))
                        copied = true
                    },
                    modifier = Modifier.weight(1f).height(52.dp),
                ) { Text(if (copied) "Copied ✓" else "Copy link") }
                Button(
                    onClick = {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(
                                Intent.EXTRA_TEXT,
                                "Swap cards with me on Unspoken Cues. Open this link on your phone, or paste it " +
                                    "into the app under My Card → Enter a link instead: $link",
                            )
                        }
                        context.startActivity(Intent.createChooser(send, "Send $name's card"))
                    },
                    modifier = Modifier.weight(1f).height(52.dp),
                ) { Text("Send…") }
            }
        }
    }
}

// ---------- 5. S.W.A.P. / Binder ----------

@Composable
fun BinderScreen(
    status: CueStatus,
    profile: Profile,
    collection: List<CollectedCard>,
    onRemove: (CollectedCard) -> Unit,
) {
    var opened by remember { mutableStateOf<CollectedCard?>(null) }
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
        ScreenTitle("Binder", "Cards you've collected through S.W.A.P.")
        Surface(
            Modifier.fillMaxWidth().height(160.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
            Row(Modifier.padding(20.dp)) {
                Box(Modifier.width(6.dp).fillMaxHeightBar(status.color))
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.SpaceBetween) {
                    Text("MY CARD", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Column {
                        Text(profile.displayName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                        Text(status.meaning, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        Text("Collection · ${collection.size}", style = MaterialTheme.typography.titleMedium)
        if (collection.isEmpty()) {
            Text(
                "No cards yet. Swap with someone to add their card here.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        collection.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { card -> CollectedTile(card, Modifier.weight(1f)) { opened = card } }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

// Full-screen view of someone else's card (collected or at an event) with all their preferences and boundaries.
@Composable
private fun PersonDetail(profile: Profile, status: CueStatus, onRemove: (() -> Unit)? = null, onClose: () -> Unit) {
    var confirmingRemove by remember { mutableStateOf(false) }
    if (confirmingRemove && onRemove != null) {
        AlertDialog(
            onDismissRequest = { confirmingRemove = false },
            title = { Text("Remove ${profile.displayName}?") },
            text = { Text("Their card leaves your binder, and your card is removed from theirs. You'll need to swap again to reconnect.") },
            confirmButton = {
                TextButton(onRemove) { Text("Remove", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton({ confirmingRemove = false }) { Text("Cancel") } },
        )
    }
    Dialog(onClose, DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(profile.displayName, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.weight(1f))
                    TextButton(onClose) { Text("Close") }
                }
                SwapCard(profile, status)
                SectionCard(title = "Preferences") { ChipsOrEmpty(profile.preferences, "No preferences shared") }
                SectionCard(title = "Boundaries") { ChipsOrEmpty(profile.boundaries, "No boundaries shared") }
                if (onRemove != null) {
                    TextButton({ confirmingRemove = true }, Modifier.fillMaxWidth()) {
                        Text("Remove from binder", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

private fun Modifier.fillMaxHeightBar(color: Color) =
    this.height(120.dp).background(color, RoundedCornerShape(3.dp))

@Composable
private fun CollectedTile(card: CollectedCard, modifier: Modifier, onClick: () -> Unit) {
    SectionCard(modifier.aspectRatio(0.8f).clip(RoundedCornerShape(20.dp)).clickable(onClick = onClick)) {
        Dot(card.status.color, 16)
        Spacer(Modifier.weight(1f))
        Text(card.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    }
}

// ---------- 6. Settings / Pairing ----------

@Composable
fun SettingsScreen(
    watch: WatchConnection,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    onReconnect: () -> Unit,
    onSignOut: () -> Unit,
    onBack: () -> Unit,
) {
    var demoMode by remember { mutableStateOf(false) }
    ScreenColumn {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            TextButton(onBack, Modifier.align(Alignment.CenterStart)) { Text("Back") }
            Text("Settings", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        }
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
        SectionCard(title = "Appearance") {
            Text("Theme", style = MaterialTheme.typography.bodyLarge)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                ThemeMode.entries.forEachIndexed { i, mode ->
                    SegmentedButton(
                        selected = mode == themeMode,
                        onClick = { onThemeModeChange(mode) },
                        shape = SegmentedButtonDefaults.itemShape(i, ThemeMode.entries.size),
                    ) { Text(mode.label) }
                }
            }
            Text(
                "System follows your phone's setting.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
