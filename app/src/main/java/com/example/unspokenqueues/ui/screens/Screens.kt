package com.example.unspokenqueues.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.BackHandler
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.unspokenqueues.R
import com.example.unspokenqueues.data.EventRepository
import com.example.unspokenqueues.data.authFailureMessage
import com.example.unspokenqueues.data.joinFailureMessage
import com.example.unspokenqueues.model.Attendee
import com.example.unspokenqueues.model.CollectedCard
import com.example.unspokenqueues.model.CueStatus
import com.example.unspokenqueues.model.Event
import com.example.unspokenqueues.model.JOIN_CODE_MAX_LENGTH
import com.example.unspokenqueues.model.Profile
import com.example.unspokenqueues.model.SwapResult
import com.example.unspokenqueues.model.ThemeMode
import com.example.unspokenqueues.model.WatchConnection
import com.example.unspokenqueues.model.cardTokenFrom
import com.example.unspokenqueues.model.isValidJoinCode
import com.example.unspokenqueues.model.visibleAttendees
import com.example.unspokenqueues.ui.components.Avatar
import com.example.unspokenqueues.ui.components.Chip
import com.example.unspokenqueues.ui.components.Dot
import com.example.unspokenqueues.ui.components.ScreenTitle
import com.example.unspokenqueues.ui.components.FullScreenQr
import com.example.unspokenqueues.ui.components.QrScanner
import com.example.unspokenqueues.ui.components.SectionCard
import com.example.unspokenqueues.ui.components.SwapCard
import com.example.unspokenqueues.ui.components.WatchPill
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
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

// ---------- 1. Welcome / Sign in / Create account ----------

private enum class AuthStep { WELCOME, SIGN_IN, CREATE_ACCOUNT }

/**
 * What a signed-out user sees: a welcome screen that leads to the sign-in or the create-account
 * form. Neither callback reports success: once there is a session the caller stops showing this
 * flow altogether.
 */
@Composable
fun AuthFlow(
    onSignIn: suspend (email: String, password: String) -> Unit,
    onSignUp: suspend (email: String, password: String) -> Unit,
) {
    var step by rememberSaveable { mutableStateOf(AuthStep.WELCOME) }
    when (step) {
        AuthStep.WELCOME -> WelcomeScreen(
            onSignIn = { step = AuthStep.SIGN_IN },
            onCreateAccount = { step = AuthStep.CREATE_ACCOUNT },
        )
        AuthStep.SIGN_IN -> SignInScreen(onSignIn, onBack = { step = AuthStep.WELCOME })
        AuthStep.CREATE_ACCOUNT -> CreateAccountScreen(onSignUp, onBack = { step = AuthStep.WELCOME })
    }
    // From a form the system back button returns to Welcome; from Welcome it leaves the app.
    BackHandler(enabled = step != AuthStep.WELCOME) { step = AuthStep.WELCOME }
}

@Composable
private fun CueDots() {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        CueStatus.entries.forEach { Dot(it.color, 14) }
    }
}

@Composable
fun WelcomeScreen(onSignIn: () -> Unit, onCreateAccount: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        CueDots()
        Spacer(Modifier.height(20.dp))
        Text("Unspoken Cues", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold)
        Text(
            "Let people know how you're doing — without saying a word.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(32.dp))
        Button(onSignIn, Modifier.fillMaxWidth().height(52.dp)) { Text("Sign in") }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onCreateAccount, Modifier.fillMaxWidth().height(52.dp)) { Text("Create an account") }
    }
}

@Composable
fun SignInScreen(
    onSignIn: suspend (email: String, password: String) -> Unit,
    onBack: () -> Unit,
) {
    AuthForm(
        title = "Welcome back",
        subtitle = "Sign in with the email and password for your account.",
        submitLabel = "Sign in",
        confirmPassword = false,
        onSubmit = onSignIn,
        onBack = onBack,
    )
}

@Composable
fun CreateAccountScreen(
    onSignUp: suspend (email: String, password: String) -> Unit,
    onBack: () -> Unit,
) {
    AuthForm(
        title = "Create your account",
        subtitle = "You'll use this email and password to sign in.",
        submitLabel = "Create account",
        confirmPassword = true,
        onSubmit = onSignUp,
        onBack = onBack,
    )
}

// The form behind both auth screens. [confirmPassword] adds a second password field, which
// catches a typo in a password that has never been used to sign in yet.
@Composable
private fun AuthForm(
    title: String,
    subtitle: String,
    submitLabel: String,
    confirmPassword: Boolean,
    onSubmit: suspend (email: String, password: String) -> Unit,
    onBack: () -> Unit,
) {
    var email by rememberSaveable { mutableStateOf("") }
    // Passwords are deliberately not saveable: they shouldn't be written into saved state.
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun submit() {
        if (loading) return
        error = null
        if (email.isBlank() || password.isBlank()) {
            error = "Enter your email and password."
            return
        }
        if (confirmPassword && password != confirm) {
            error = "Those passwords don't match."
            return
        }
        loading = true
        scope.launch {
            try {
                onSubmit(email, password)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = authFailureMessage(e)
            } finally {
                loading = false
            }
        }
    }

    // Top-aligned and scrollable so the fields stay reachable with the keyboard up.
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
        TextButton(onBack, enabled = !loading) { Text("Back") }
        Spacer(Modifier.height(24.dp))
        CueDots()
        Spacer(Modifier.height(20.dp))
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(32.dp))
        OutlinedTextField(
            email, { email = it }, label = { Text("Email") }, singleLine = true,
            enabled = !loading,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            password, { password = it }, label = { Text("Password") }, singleLine = true,
            enabled = !loading, visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = if (confirmPassword) ImeAction.Next else ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            modifier = Modifier.fillMaxWidth(),
        )
        if (confirmPassword) {
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                confirm, { confirm = it }, label = { Text("Confirm password") }, singleLine = true,
                enabled = !loading, visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (error != null) {
            Spacer(Modifier.height(12.dp))
            Text(error!!, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = ::submit,
            enabled = !loading,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            if (loading) {
                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
            } else {
                Text(submitLabel)
            }
        }
    }
}

// ---------- 1b. Loading the account ----------

/**
 * Shown after sign-in until the account's profile has been read, which decides between
 * onboarding and the app. [failed] swaps the spinner for a way to retry or sign out.
 */
@Composable
fun AccountLoadingScreen(failed: Boolean, onRetry: () -> Unit, onSignOut: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (!failed) {
            CircularProgressIndicator()
        } else {
            Text(
                "Couldn't load your account",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "Check your connection and try again.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            Button(onRetry, Modifier.fillMaxWidth().height(52.dp)) { Text("Try again") }
            TextButton(onSignOut, Modifier.fillMaxWidth()) { Text("Sign out") }
        }
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

// ---------- 2. Events ----------

/**
 * The events the user hosts or has joined, plus the two ways into a new one: host it, or join
 * with the host's code (typed or scanned). [onOpen] receives an event the user tapped or just
 * joined.
 */
@Composable
fun EventsScreen(
    repo: EventRepository,
    userId: String,
    onHost: () -> Unit,
    onOpen: (Event) -> Unit,
) {
    // Null until the first load answers.
    var events by remember { mutableStateOf<List<Event>?>(null) }
    var loadFailed by remember { mutableStateOf(false) }
    var joinOpen by rememberSaveable { mutableStateOf(false) }
    var scanning by rememberSaveable { mutableStateOf(false) }
    var code by rememberSaveable { mutableStateOf("") }
    var joining by remember { mutableStateOf(false) }
    var joinError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(userId) {
        try {
            events = repo.myEvents(userId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            loadFailed = true
        }
    }

    fun join(text: String) {
        if (joining) return
        joinError = null
        joining = true
        scope.launch {
            try {
                onOpen(repo.joinByCode(text))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                joinError = joinFailureMessage((e as? PostgrestRestException)?.code)
            } finally {
                joining = false
            }
        }
    }

    ScreenColumn {
        ScreenTitle("Events", "Host an event, or join one with its code.")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onHost, Modifier.weight(1f).height(52.dp)) { Text("Host event") }
            OutlinedButton({ joinOpen = true }, Modifier.weight(1f).height(52.dp)) { Text("Join event") }
        }

        Text("My events", style = MaterialTheme.typography.titleMedium)
        val loaded = events
        when {
            loaded == null && loadFailed -> Text(
                "Couldn't load your events. Check your connection, then open this tab again.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            loaded == null -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            loaded.isEmpty() -> Text(
                "No events yet. Host one, or join with a code from the host.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            else -> loaded.forEach { event ->
                EventListItem(event, hosting = event.hostId == userId) { onOpen(event) }
            }
        }
    }

    // Hidden while the scanner is up so the two dialogs don't stack.
    if (joinOpen && !scanning) {
        JoinEventDialog(
            code = code,
            onCodeChange = {
                code = it.trim().uppercase().take(JOIN_CODE_MAX_LENGTH)
                joinError = null
            },
            joining = joining,
            error = joinError,
            onJoin = { join(code) },
            onScan = { scanning = true },
            onDismiss = {
                joinOpen = false
                code = ""
                joinError = null
            },
        )
    }
    if (scanning) {
        QrScanner(
            onCode = { scanned ->
                scanning = false
                // The camera reads any QR code, e.g. someone's card instead of the event's.
                if (isValidJoinCode(scanned)) {
                    code = scanned.trim().uppercase()
                    join(scanned)
                } else {
                    joinError = "That QR code isn't an event code."
                }
            },
            onDismiss = { scanning = false },
            hint = "Point the camera at the event's QR code",
        )
    }
}

@Composable
private fun EventListItem(event: Event, hosting: Boolean, onClick: () -> Unit) {
    SectionCard(Modifier.clip(RoundedCornerShape(20.dp)).clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text(event.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                if (event.details.isNotBlank()) {
                    Text(
                        event.details,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Chip(
                when {
                    !event.active -> "Ended"
                    hosting -> "Hosting"
                    else -> "Joined"
                },
            )
        }
    }
}

// The way into someone else's event: type the host's code, or scan the QR code they are showing.
@Composable
private fun JoinEventDialog(
    code: String,
    onCodeChange: (String) -> Unit,
    joining: Boolean,
    error: String?,
    onJoin: () -> Unit,
    onScan: () -> Unit,
    onDismiss: () -> Unit,
) {
    val canJoin = isValidJoinCode(code) && !joining
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Join an event") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Enter the code from the host, or scan the QR code they're showing.")
                OutlinedTextField(
                    code, onCodeChange, label = { Text("Event code") }, singleLine = true,
                    enabled = !joining,
                    isError = error != null,
                    supportingText = if (error != null) { { Text(error) } } else null,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { if (canJoin) onJoin() }),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedButton(onScan, Modifier.fillMaxWidth(), enabled = !joining) { Text("Scan QR code") }
            }
        },
        confirmButton = { TextButton(onJoin, enabled = canJoin) { Text(if (joining) "Joining…" else "Join") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}

// ---------- 2b. Host an event ----------

private const val EVENT_NAME_MAX_LENGTH = 60
private const val EVENT_DETAILS_MAX_LENGTH = 200

@Composable
fun CreateEventScreen(
    repo: EventRepository,
    userId: String,
    onCreated: (Event) -> Unit,
    onCancel: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var details by rememberSaveable { mutableStateOf("") }
    var creating by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun create() {
        if (creating) return
        creating = true
        error = null
        scope.launch {
            try {
                onCreated(repo.createEvent(userId, name, details))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = "Couldn't create the event. Check your connection and try again."
            } finally {
                creating = false
            }
        }
    }

    ScreenColumn {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onCancel) { Text("Cancel") }
            Spacer(Modifier.weight(1f))
            Text("Host an event", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            TextButton(
                onClick = ::create,
                enabled = name.isNotBlank() && !creating,
            ) { Text(if (creating) "Creating…" else "Create") }
        }

        SectionCard(title = "Event") {
            OutlinedTextField(
                name, { if (it.length <= EVENT_NAME_MAX_LENGTH) name = it }, label = { Text("Event name") },
                singleLine = true, enabled = !creating, modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                details, { if (it.length <= EVENT_DETAILS_MAX_LENGTH) details = it }, label = { Text("Details") },
                placeholder = { Text("e.g. Friday 7 PM, Room 204") },
                supportingText = { Text("${details.length}/$EVENT_DETAILS_MAX_LENGTH") },
                minLines = 2, maxLines = 4, enabled = !creating, modifier = Modifier.fillMaxWidth(),
            )
        }
        error?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        }
        Text(
            "You'll get a code and a QR code for people to join with. As the host you see every attendee's status.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ---------- 2c. Event detail ----------

/**
 * One event: its attendees, and for the host the join QR and the way to end it. Anyone can take
 * the event off their list: an attendee leaves it, the host deletes it for everyone. [status] is
 * the viewer's own cue, which colours the join QR's frame. [onEventChange] receives the event
 * once the host has ended it; [onRemoved] is called once the viewer has left or deleted it.
 */
@Composable
fun EventDetailScreen(
    repo: EventRepository,
    userId: String,
    status: CueStatus,
    event: Event,
    onEventChange: (Event) -> Unit,
    onRemoved: () -> Unit,
    onBack: () -> Unit,
) {
    val isHost = event.hostId == userId
    // Null until the first load answers.
    var attendees by remember { mutableStateOf<List<Attendee>?>(null) }
    var loadFailed by remember { mutableStateOf(false) }
    // Bumped to load the attendee list again.
    var reload by remember { mutableIntStateOf(0) }
    var showingQr by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }
    var confirmingEnd by remember { mutableStateOf(false) }
    var confirmingRemove by remember { mutableStateOf(false) }
    // True while ending, leaving or deleting is in flight; only one runs at a time.
    var working by remember { mutableStateOf(false) }
    var actionError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    // Compose's clipboard rather than the system service, so a test can swap in a fake one.
    val clipboard = LocalClipboard.current

    LaunchedEffect(event.id, reload) {
        try {
            attendees = repo.eventAttendees(event.id)
            loadFailed = false
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            loadFailed = true
        }
    }

    fun perform(failure: String, action: suspend () -> Unit) {
        if (working) return
        working = true
        actionError = null
        scope.launch {
            try {
                action()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                actionError = failure
            } finally {
                working = false
            }
        }
    }

    fun end() = perform("Couldn't end the event. Check your connection and try again.") {
        repo.endEvent(event.id)
        onEventChange(event.copy(active = false))
    }

    fun remove() = perform(
        if (isHost) "Couldn't delete the event. Check your connection and try again."
        else "Couldn't leave the event. Check your connection and try again.",
    ) {
        if (isHost) repo.deleteEvent(event.id) else repo.leaveEvent(event.id, userId)
        onRemoved()
    }

    ScreenColumn {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            TextButton(onBack, Modifier.align(Alignment.CenterStart)) { Text("Back") }
            Text("Event", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        }
        ScreenTitle(event.name, event.details.takeIf { it.isNotBlank() })

        if (!event.active) {
            Text(
                "This event has ended. Nobody else can join.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else if (isHost) {
            SectionCard(title = "Join code") {
                Text(event.joinCode, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("Event join code", event.joinCode)))
                                copied = true
                            }
                        },
                        modifier = Modifier.weight(1f).height(52.dp),
                    ) { Text(if (copied) "Copied ✓" else "Copy code") }
                    Button({ showingQr = true }, Modifier.weight(1f).height(52.dp)) { Text("Show join QR") }
                }
            }
        }

        // The rule lives in visibleAttendees: the host sees everyone, others only green and yellow.
        val visible = attendees?.let { visibleAttendees(userId, event.hostId, it) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (visible == null) "Attendees" else "Attendees · ${visible.size}",
                Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
            )
            TextButton({ reload++ }) { Text("Refresh") }
        }
        if (loadFailed) {
            Text(
                "Couldn't load attendees. Check your connection, then refresh.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        when {
            visible == null -> if (!loadFailed) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            visible.isEmpty() -> Text(
                if (isHost && event.active) "Nobody has joined yet. Show the join QR or share the code." else "Nobody to show yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            else -> SectionCard {
                visible.forEachIndexed { i, person ->
                    if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    AttendeeRow(person)
                }
            }
        }
        if (!isHost) {
            Text(
                "You see attendees who are green or yellow. The host sees everyone's status.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        actionError?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        }
        if (isHost && event.active) {
            OutlinedButton({ confirmingEnd = true }, Modifier.fillMaxWidth().height(52.dp), enabled = !working) {
                Text("End event")
            }
        }
        TextButton({ confirmingRemove = true }, Modifier.fillMaxWidth(), enabled = !working) {
            Text(if (isHost) "Delete event" else "Leave event", color = MaterialTheme.colorScheme.error)
        }
    }

    if (showingQr) {
        FullScreenQr(event.joinCode, event.name, status) {
            showingQr = false
            // Whoever just scanned the code should be on the list when the host looks back.
            reload++
        }
    }
    if (confirmingEnd) {
        AlertDialog(
            onDismissRequest = { confirmingEnd = false },
            title = { Text("End ${event.name}?") },
            text = { Text("Nobody else will be able to join. People who already joined can still open the event.") },
            confirmButton = {
                TextButton({
                    confirmingEnd = false
                    end()
                }) { Text("End event") }
            },
            dismissButton = { TextButton({ confirmingEnd = false }) { Text("Cancel") } },
        )
    }
    if (confirmingRemove) {
        AlertDialog(
            onDismissRequest = { confirmingRemove = false },
            title = { Text(if (isHost) "Delete ${event.name}?" else "Leave ${event.name}?") },
            text = {
                Text(
                    if (isHost) {
                        "The event is removed for you and for everyone who joined. This can't be undone."
                    } else {
                        "The event leaves your list and you come off its attendee list. " +
                            "You can join again with the code while the event is still running."
                    },
                )
            },
            confirmButton = {
                TextButton({
                    confirmingRemove = false
                    remove()
                }) { Text(if (isHost) "Delete event" else "Leave event", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton({ confirmingRemove = false }) { Text("Cancel") } },
        )
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
    var saving by remember { mutableStateOf(false) }
    var photoError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

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
            AvatarPicker(
                name = name,
                image = pickedPhoto ?: avatarUrl,
                enabled = !saving,
                onPicked = { pickedPhoto = it },
                onRemove = {
                    pickedPhoto = null
                    avatarUrl = ""
                },
                onError = { photoError = it },
            )
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
                bio, { if (it.length <= BIO_MAX_LENGTH) bio = it }, label = { Text("Bio") },
                supportingText = { Text("${bio.length}/$BIO_MAX_LENGTH") },
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

internal const val BIO_MAX_LENGTH = 120

/**
 * A profile photo with its photo menu underneath: choose from the gallery, take one with the
 * camera, or remove it. [image] is what to show now: a photo just picked, the stored URL, or
 * null/blank for none. A new photo is handed to [onPicked] as a local Uri; uploading it is the
 * caller's job. [onError] receives a problem to show the user, or null when a new attempt starts.
 */
@Composable
internal fun AvatarPicker(
    name: String,
    image: Any?,
    enabled: Boolean,
    onPicked: (Uri) -> Unit,
    onRemove: () -> Unit,
    onError: (String?) -> Unit,
) {
    val hasPhoto = image != null && image != ""
    // Saveable because Android may recreate the activity while the camera app is in front.
    var pendingCapture by rememberSaveable { mutableStateOf<Uri?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) onPicked(uri)
    }
    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        if (saved) pendingCapture?.let(onPicked)
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
        if (granted) launchCamera() else onError("Camera permission is needed to take a photo.")
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Avatar(name, 88, image)
        Box {
            TextButton({ menuOpen = true }, enabled = enabled) { Text(if (hasPhoto) "Change photo" else "Add photo") }
            DropdownMenu(menuOpen, { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Choose from gallery") },
                    onClick = {
                        menuOpen = false
                        onError(null)
                        pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                )
                DropdownMenuItem(
                    text = { Text("Take photo") },
                    onClick = {
                        menuOpen = false
                        onError(null)
                        // Launching the camera app throws unless the declared CAMERA permission is granted.
                        val granted = ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) ==
                            PackageManager.PERMISSION_GRANTED
                        if (granted) launchCamera() else requestCamera.launch(android.Manifest.permission.CAMERA)
                    },
                )
                if (hasPhoto) {
                    DropdownMenuItem(
                        text = { Text("Remove photo") },
                        onClick = {
                            menuOpen = false
                            onRemove()
                        },
                    )
                }
            }
        }
    }
}

/** [this] list with [text] added, trimmed. Blank text and entries already in the list change nothing. */
internal fun List<String>.withEntry(text: String): List<String> {
    val entry = text.trim()
    return if (entry.isEmpty() || entry in this) this else this + entry
}

@Composable
private fun EditableListCard(title: String, hint: String, items: List<String>, onChange: (List<String>) -> Unit) {
    var draft by remember { mutableStateOf("") }
    SectionCard(title = title) {
        EditableList(hint, items, onChange, draft) { draft = it }
    }
}

/**
 * A list of short entries shown as removable chips, with a field to add another. [draft] is the
 * text in that field; it is the caller's state so the caller can act on text that was typed but
 * never added.
 */
@Composable
internal fun EditableList(
    hint: String,
    items: List<String>,
    onChange: (List<String>) -> Unit,
    draft: String,
    onDraftChange: (String) -> Unit,
) {
    fun add() {
        onChange(items.withEntry(draft))
        onDraftChange("")
    }
    ChipsOrEmpty(items, "Nothing added yet", onRemove = { onChange(items - it) })
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            draft, onDraftChange, placeholder = { Text(hint) }, singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { add() }),
            modifier = Modifier.weight(1f),
        )
        Button(::add, enabled = draft.isNotBlank()) { Text("Add") }
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
    // The tab tour, replayed on demand over this screen. Watching it again changes nothing that
    // is saved, including whether the account has finished onboarding.
    var replayingTutorial by rememberSaveable { mutableStateOf(false) }
    if (replayingTutorial) {
        BackHandler { replayingTutorial = false }
        TabTutorialScreen(onFinish = { replayingTutorial = false })
        return
    }
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
            Column {
                Text("Tutorial", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "A quick tour of what each tab is for",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedButton({ replayingTutorial = true }, Modifier.fillMaxWidth()) { Text("Replay tutorial") }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            Text("About Unspoken Cues", style = MaterialTheme.typography.bodyLarge)
        }
        TextButton(onSignOut, Modifier.fillMaxWidth()) {
            Text("Sign out", color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
internal fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked, onChange)
    }
}
