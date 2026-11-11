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
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
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
import com.example.unspokenqueues.ui.components.OutlinedButton
import com.example.unspokenqueues.ui.components.PasswordField
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
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
import com.example.unspokenqueues.ui.components.AttendeeListSkeleton
import com.example.unspokenqueues.ui.components.Dot
import com.example.unspokenqueues.ui.components.Halo
import com.example.unspokenqueues.ui.components.EventListSkeleton
import com.example.unspokenqueues.ui.components.ScreenTitle
import com.example.unspokenqueues.ui.components.FullScreenQr
import com.example.unspokenqueues.ui.components.QrScanner
import com.example.unspokenqueues.ui.components.SectionCard
import com.example.unspokenqueues.ui.components.SwapCard
import com.example.unspokenqueues.ui.components.SwapCardBack
import com.example.unspokenqueues.ui.components.WatchPill
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import com.example.unspokenqueues.ui.theme.EaseOutExpo
import com.example.unspokenqueues.ui.theme.EaseOutQuart
import com.example.unspokenqueues.ui.theme.MotionLong
import com.example.unspokenqueues.ui.theme.MotionMedium
import com.example.unspokenqueues.ui.theme.MotionShort
import java.io.File
import kotlin.math.hypot

@Composable
internal fun ScreenColumn(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) { content() }
}

// ---------- 1. Welcome / Sign in / Create account ----------

private enum class AuthStep { WELCOME, SIGN_IN, CREATE_ACCOUNT, FORGOT_PASSWORD }

/**
 * What a signed-out user sees: a welcome screen that leads to the sign-in or the create-account
 * form. Neither callback reports success: once there is a session the caller stops showing this
 * flow altogether.
 */
@Composable
fun AuthFlow(
    onSignIn: suspend (email: String, password: String) -> Unit,
    onSignUp: suspend (email: String, password: String) -> Unit,
    onPasswordReset: suspend (email: String) -> Unit,
    startAtSignIn: Boolean = false,
) {
    var step by rememberSaveable { mutableStateOf(if (startAtSignIn) AuthStep.SIGN_IN else AuthStep.WELCOME) }
    fun back() {
        step = if (step == AuthStep.FORGOT_PASSWORD) AuthStep.SIGN_IN else AuthStep.WELCOME
    }
    when (step) {
        AuthStep.WELCOME -> WelcomeScreen(
            onSignIn = { step = AuthStep.SIGN_IN },
            onCreateAccount = { step = AuthStep.CREATE_ACCOUNT },
        )
        AuthStep.SIGN_IN -> SignInScreen(
            onSignIn, onBack = ::back,
            onForgotPassword = { step = AuthStep.FORGOT_PASSWORD },
        )
        AuthStep.CREATE_ACCOUNT -> CreateAccountScreen(onSignUp, onBack = { step = AuthStep.WELCOME })
        AuthStep.FORGOT_PASSWORD -> ForgotPasswordScreen(onPasswordReset, onBack = ::back)
    }
    // From a form the system back button returns to Welcome; from Welcome it leaves the app.
    BackHandler(enabled = step != AuthStep.WELCOME) { back() }
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
            "Let people know how you're doing, without saying a word.",
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
    onForgotPassword: () -> Unit,
) {
    AuthForm(
        title = "Welcome back",
        subtitle = "Sign in with the email and password for your account.",
        submitLabel = "Sign in",
        confirmPassword = false,
        onSubmit = onSignIn,
        onBack = onBack,
        onForgotPassword = onForgotPassword,
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
    onForgotPassword: (() -> Unit)? = null,
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
        PasswordField(
            password, { password = it }, "Password",
            enabled = !loading,
            imeAction = if (confirmPassword) ImeAction.Next else ImeAction.Done,
            keyboardActions = KeyboardActions(onDone = { submit() }),
            modifier = Modifier.fillMaxWidth(),
        )
        onForgotPassword?.let {
            TextButton(it, enabled = !loading) { Text("Forgot password?") }
        }
        if (confirmPassword) {
            Spacer(Modifier.height(12.dp))
            PasswordField(
                confirm, { confirm = it }, "Confirm password",
                enabled = !loading,
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
    ScreenColumn {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("My Cue", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            WatchPill(watch)
        }

        CueHero(status)

        Text("Change status", style = MaterialTheme.typography.titleMedium)
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CueStatus.entries.chunked(2).forEach { row ->
                Row(Modifier.height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { cue ->
                        CueOption(cue, cue == status, Modifier.weight(1f).fillMaxHeight()) { onStatusChange(cue) }
                    }
                }
            }
        }
        Text(
            "Updates sync to your watch automatically.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The current cue, large. On a change the new color spreads over the old one from the bottom-left
 * corner, under the text, while the words swap.
 */
@Composable
private fun CueHero(status: CueStatus) {
    // [under] stays painted while [over] spreads across it.
    var under by remember { mutableStateOf(status) }
    var over by remember { mutableStateOf(status) }
    val spread = remember { Animatable(1f) }
    LaunchedEffect(status) {
        if (status == over) return@LaunchedEffect
        under = over
        over = status
        spread.snapTo(0f)
        spread.animateTo(1f, tween(MotionLong + 150, easing = EaseOutExpo))
    }
    Box(
        Modifier
            .fillMaxWidth()
            .height(220.dp)
            .clip(RoundedCornerShape(28.dp))
            .drawBehind {
                drawRect(under.color)
                drawCircle(
                    over.color,
                    radius = hypot(size.width, size.height) * spread.value,
                    center = Offset(0f, size.height),
                )
            }
            .padding(24.dp),
        contentAlignment = Alignment.BottomStart,
    ) {
        AnimatedContent(
            targetState = status,
            transitionSpec = {
                (fadeIn(tween(MotionMedium, delayMillis = 90, easing = EaseOutQuart)) +
                    slideInVertically(tween(MotionMedium, delayMillis = 90, easing = EaseOutQuart)) { it / 6 }) togetherWith
                    fadeOut(tween(90))
            },
            contentAlignment = Alignment.BottomStart,
            label = "cueText",
        ) { shown ->
            // Full-strength on-color text: alpha on a saturated fill drops below AA contrast.
            Column {
                Text("CURRENTLY", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = shown.onColor)
                Text(shown.label, style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold, color = shown.onColor)
                Text(shown.meaning, style = MaterialTheme.typography.titleMedium, color = shown.onColor)
            }
        }
    }
}

@Composable
private fun CueOption(cue: CueStatus, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val motion = tween<Float>(MotionMedium, easing = EaseOutQuart)
    val scale by animateFloatAsState(if (selected) 1.03f else 1f, motion, label = "cueScale")
    val fill by animateColorAsState(
        if (selected) cue.color.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surface,
        tween(MotionMedium, easing = EaseOutQuart),
        label = "cueFill",
    )
    val edge by animateColorAsState(
        if (selected) cue.color else MaterialTheme.colorScheme.outline,
        tween(MotionMedium, easing = EaseOutQuart),
        label = "cueEdge",
    )
    val edgeWidth by animateDpAsState(if (selected) 2.dp else 1.dp, tween(MotionMedium, easing = EaseOutQuart), label = "cueEdgeWidth")
    Surface(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(20.dp))
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = fill,
        border = BorderStroke(edgeWidth, edge),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Dot(cue.color, 22)
            Text(cue.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(cue.meaning, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// A cue's dot with its meaning, so the status reads without telling colors apart.
@Composable
internal fun StatusLabel(status: CueStatus) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Dot(status.color, 8)
        Text(
            status.meaning,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
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
            Avatar(p.displayName, 72, p.avatarImage)
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
internal fun ChipsOrEmpty(items: List<String>, emptyText: String, onRemove: ((String) -> Unit)? = null) {
    if (items.isEmpty()) {
        Text(emptyText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { item ->
            if (onRemove == null) Chip(item) else key(item) { RemovableChip(item, onRemove) }
        }
    }
}

// A chip that shrinks away when tapped, and only then leaves the list.
@Composable
private fun RemovableChip(item: String, onRemove: (String) -> Unit) {
    val visibility = remember { MutableTransitionState(true) }
    val remove by rememberUpdatedState(onRemove)
    LaunchedEffect(visibility.isIdle, visibility.currentState) {
        if (visibility.isIdle && !visibility.currentState) remove(item)
    }
    AnimatedVisibility(
        visibility,
        exit = fadeOut(tween(MotionShort)) +
            scaleOut(tween(MotionMedium, easing = EaseOutQuart), targetScale = 0.6f) +
            shrinkHorizontally(tween(MotionMedium, easing = EaseOutQuart)),
    ) {
        Chip(
            "$item  ✕",
            Modifier
                .minimumInteractiveComponentSize()
                .clip(RoundedCornerShape(50))
                .clickable(onClickLabel = "Remove $item", enabled = visibility.targetState) { visibility.targetState = false },
        )
    }
}

// A copy button's label, which turns into "Copied ✓" once the copy has happened.
@Composable
internal fun CopyLabel(copied: Boolean, label: String) {
    AnimatedContent(
        targetState = copied,
        transitionSpec = {
            (fadeIn(tween(MotionMedium, easing = EaseOutQuart)) + slideInVertically(tween(MotionMedium, easing = EaseOutQuart)) { it / 2 }) togetherWith
                (fadeOut(tween(MotionShort)) + slideOutVertically(tween(MotionShort)) { -it / 2 })
        },
        label = "copied",
    ) { done -> Text(if (done) "Copied ✓" else label) }
}

// ---------- 3b. Edit profile ----------

@Composable
fun EditProfileScreen(
    initial: Profile,
    email: String?,
    status: CueStatus,
    onUploadAvatar: suspend (Uri) -> String,
    onUploadCardImage: suspend (Uri) -> String,
    onSave: (Profile) -> Unit,
    onCancel: () -> Unit,
) {
    var name by remember { mutableStateOf(initial.displayName) }
    var bio by remember { mutableStateOf(initial.bio) }
    var preferences by remember { mutableStateOf(initial.preferences) }
    var boundaries by remember { mutableStateOf(initial.boundaries) }
    var isPublic by remember { mutableStateOf(initial.isPublic) }
    var avatarUrl by remember { mutableStateOf(initial.avatarUrl) }
    // The local copy of the current photo, kept while the photo is unchanged.
    val keptLocal = initial.localAvatar.takeIf { avatarUrl.isNotBlank() && avatarUrl == initial.avatarUrl }
    // A photo chosen or taken on this screen. It is only uploaded when the user taps Save.
    // Saveable because Android may recreate the activity while the camera app is in front.
    var pickedPhoto by rememberSaveable { mutableStateOf<Uri?>(null) }
    // The same three for the picture on the S.W.A.P. card.
    var cardImageUrl by remember { mutableStateOf(initial.cardImageUrl) }
    val keptLocalCard = initial.localCardImage.takeIf { cardImageUrl.isNotBlank() && cardImageUrl == initial.cardImageUrl }
    var pickedCard by rememberSaveable { mutableStateOf<Uri?>(null) }
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
                val cardUrl = pickedCard?.let { onUploadCardImage(it) } ?: cardImageUrl
                onSave(
                    Profile(
                        name.trim(), bio.trim(), preferences, boundaries, isPublic, url, pickedPhoto ?: keptLocal,
                        cardImageUrl = cardUrl, localCardImage = pickedCard ?: keptLocalCard,
                    ),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                photoError = "Couldn't upload your pictures. Check your connection and try again."
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
                image = pickedPhoto ?: keptLocal ?: avatarUrl,
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

        SectionCard(title = "Card image") {
            Text(
                "Shown large on your S.W.A.P. card in other people's binders, ringed in your cue color. " +
                    "Without one, your profile photo is used.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                AvatarPicker(
                    name = name,
                    image = pickedCard ?: keptLocalCard ?: cardImageUrl,
                    enabled = !saving,
                    onPicked = { pickedCard = it },
                    onRemove = {
                        pickedCard = null
                        cardImageUrl = ""
                    },
                    onError = { photoError = it },
                    size = 112,
                    noun = "card image",
                    halo = status,
                )
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
    size: Int = 88,
    // What the picture is called in the menu: "Add photo", "Change card image", ...
    noun: String = "photo",
    // When set, the picture is ringed in this cue (with a soft glow), as it will be on the card.
    halo: CueStatus? = null,
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
        if (halo != null) Halo(halo, glow = true) { Avatar(name, size, image) } else Avatar(name, size, image)
        Box {
            TextButton({ menuOpen = true }, enabled = enabled) { Text(if (hasPhoto) "Change $noun" else "Add $noun") }
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
                        text = { Text("Remove $noun") },
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
        is SwapResult.Swapped -> SwapReveal(result.card, onViewBinder, onDismiss)
        is SwapResult.Failed -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Couldn't swap cards") },
            text = { Text(result.message) },
            confirmButton = { TextButton(onDismiss) { Text("OK") } },
        )
    }
}

// Dim backdrop behind the swap reveal; tinted rather than pure black.
private val RevealScrim = Color(0xE60E0F13)

/**
 * A just-swapped card arriving: it rises from the bottom face down, turns over to show who it is,
 * and then the ways out appear.
 */
@Composable
private fun SwapReveal(card: CollectedCard, onViewBinder: () -> Unit, onDismiss: () -> Unit) {
    val whose = card.name.takeIf { it.isNotBlank() }?.let { "$it's" } ?: "Their"
    val rise = remember { Animatable(1f) }
    val turn = remember { Animatable(180f) }
    val controls = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        rise.animateTo(0f, tween(MotionLong, easing = EaseOutExpo))
        turn.animateTo(0f, tween(MotionLong + 200, easing = EaseOutQuart))
        controls.animateTo(1f, tween(MotionMedium, easing = EaseOutQuart))
    }
    val showingBack by remember { derivedStateOf { turn.value > 90f } }
    Dialog(onDismiss, DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxSize().background(RevealScrim).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                "Cards swapped",
                Modifier.graphicsLayer { alpha = 1f - rise.value },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
            )
            Spacer(Modifier.height(24.dp))
            Box(
                Modifier.graphicsLayer {
                    translationY = rise.value * size.height * 2
                    rotationY = turn.value
                    cameraDistance = 14f * density
                },
            ) {
                SwapCard(card.profile, card.status, Modifier.graphicsLayer { alpha = if (showingBack) 0f else 1f })
                if (showingBack) {
                    // Turned again so the back doesn't read mirrored.
                    SwapCardBack(card.status, Modifier.matchParentSize().graphicsLayer { rotationY = 180f })
                }
            }
            Spacer(Modifier.height(24.dp))
            Column(
                Modifier.graphicsLayer {
                    alpha = controls.value
                    translationY = (1f - controls.value) * 12.dp.toPx()
                },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "$whose card is in your binder, and yours is in theirs.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.9f),
                    textAlign = TextAlign.Center,
                )
                Button(onViewBinder, Modifier.fillMaxWidth().height(52.dp)) { Text("View binder") }
                TextButton(onDismiss, Modifier.fillMaxWidth()) { Text("Done", color = Color.White) }
            }
        }
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
                ) { CopyLabel(copied, "Copy link") }
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

// Full-screen view of someone else's card (collected or at an event) with all their preferences and boundaries.
@Composable
internal fun PersonDetail(profile: Profile, status: CueStatus, onRemove: (() -> Unit)? = null, onClose: () -> Unit) {
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
                    Text("Pair your watch to receive phone updates", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                WatchPill(watch)
            }
            OutlinedButton(onReconnect, Modifier.fillMaxWidth()) {
                Text("Sync now")
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
