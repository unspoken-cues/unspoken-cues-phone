package com.example.unspokenqueues

import com.example.unspokenqueues.ui.theme.cueAmbience
import androidx.compose.material3.NavigationBarItemDefaults
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.unspokenqueues.data.AuthRepository
import com.example.unspokenqueues.data.isPasswordRecoveryLink
import com.example.unspokenqueues.data.AvatarRepository
import com.example.unspokenqueues.data.EventCoverRepository
import com.example.unspokenqueues.data.EventRepository
import com.example.unspokenqueues.data.ProfileRepository
import com.example.unspokenqueues.data.SwapRepository
import com.example.unspokenqueues.data.swapFailureMessage
import com.example.unspokenqueues.data.toCueStatus
import com.example.unspokenqueues.data.toProfile
import com.example.unspokenqueues.model.AppPhase
import com.example.unspokenqueues.model.CollectedCard
import com.example.unspokenqueues.model.CueStatus
import com.example.unspokenqueues.model.Event
import com.example.unspokenqueues.model.MockData
import com.example.unspokenqueues.model.Profile
import com.example.unspokenqueues.model.SwapResult
import com.example.unspokenqueues.model.ThemeMode
import com.example.unspokenqueues.model.WatchConnection
import com.example.unspokenqueues.model.appPhase
import com.example.unspokenqueues.model.cardLink
import com.example.unspokenqueues.model.cardTokenFrom
import com.example.unspokenqueues.ui.screens.AccountLoadingScreen
import com.example.unspokenqueues.ui.screens.AuthFlow
import com.example.unspokenqueues.ui.screens.NewPasswordScreen
import com.example.unspokenqueues.ui.screens.BinderScreen
import com.example.unspokenqueues.ui.screens.ConfirmLinkSwapDialog
import com.example.unspokenqueues.ui.screens.CreateEventScreen
import com.example.unspokenqueues.ui.screens.CueScreen
import com.example.unspokenqueues.ui.screens.EditProfileScreen
import com.example.unspokenqueues.ui.screens.EventDetailScreen
import com.example.unspokenqueues.ui.screens.EventsScreen
import com.example.unspokenqueues.ui.screens.ProfileScreen
import com.example.unspokenqueues.ui.screens.ProfileSetupWizard
import com.example.unspokenqueues.ui.screens.QrScreen
import com.example.unspokenqueues.ui.screens.SettingsScreen
import com.example.unspokenqueues.ui.screens.SwapResultDialog
import com.example.unspokenqueues.ui.screens.TabTutorialScreen
import com.example.unspokenqueues.ui.theme.EaseOutQuart
import com.example.unspokenqueues.ui.theme.MotionMedium
import com.example.unspokenqueues.ui.theme.MotionShort
import com.example.unspokenqueues.ui.theme.UnspokenQueuesTheme
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.Instant

class MainActivity : ComponentActivity() {
    // A card link that opened the app and hasn't been acted on yet.
    private var openedLink by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // A recreated activity (rotation, process restore) still carries the intent it was first
        // launched with; reading it again would ask about the same link twice.
        if (savedInstanceState == null) openedLink = intent?.dataString
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        setContent {
            var themeMode by remember {
                mutableStateOf(
                    ThemeMode.entries.find { it.name == prefs.getString("theme_mode", null) } ?: ThemeMode.SYSTEM,
                )
            }
            val dark = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            // Keep status/nav bar icons readable when the app theme differs from the system theme.
            DisposableEffect(dark) {
                val style = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose {}
            }
            UnspokenQueuesTheme(darkTheme = dark) {
                UnspokenCuesApp(
                    themeMode = themeMode,
                    onThemeModeChange = {
                        themeMode = it
                        prefs.edit().putString("theme_mode", it.name).apply()
                    },
                    openedLink = openedLink,
                    onOpenedLinkHandled = { openedLink = null },
                )
            }
        }
    }

    // A card link tapped while the app is already running arrives here instead of in onCreate.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.dataString?.let { openedLink = it }
    }
}

enum class Tab(val label: String, @DrawableRes val icon: Int) {
    CUE("Cue", R.drawable.ic_cue),
    EVENTS("Events", R.drawable.ic_events),
    QR("QR", R.drawable.ic_qr),
    BINDER("Binder", R.drawable.ic_binder),
    PROFILE("Profile", R.drawable.ic_profile),
}

// Keeps the open event across rotation and process restore; an Event can't go in a Bundle as is.
private val EventSaver = listSaver<Event?, Any>(
    save = { e ->
        if (e == null) {
            emptyList()
        } else {
            listOf(
                e.id, e.hostId, e.name, e.details, e.joinCode, e.active,
                e.startsAt?.toEpochMilli() ?: -1L, e.endsAt?.toEpochMilli() ?: -1L,
                e.location, e.coverUrl, e.tags.joinToString(","), e.isPublic, e.hostName, e.goingCount,
            )
        }
    },
    restore = { v ->
        if (v.isEmpty()) {
            null
        } else {
            Event(
                v[0] as String, v[1] as String, v[2] as String, v[3] as String, v[4] as String, v[5] as Boolean,
                startsAt = (v[6] as Long).takeIf { it >= 0 }?.let(Instant::ofEpochMilli),
                endsAt = (v[7] as Long).takeIf { it >= 0 }?.let(Instant::ofEpochMilli),
                location = v[8] as String,
                coverUrl = v[9] as String,
                tags = (v[10] as String).split(",").filter { it.isNotEmpty() },
                isPublic = v[11] as Boolean,
                hostName = v[12] as String,
                goingCount = v[13] as Int,
            )
        }
    },
)

@Composable
fun UnspokenCuesApp(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    onThemeModeChange: (ThemeMode) -> Unit = {},
    openedLink: String? = null,
    onOpenedLinkHandled: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val authRepo = remember { AuthRepository() }
    val profileRepo = remember { ProfileRepository() }
    val swapRepo = remember { SwapRepository() }
    val eventRepo = remember { EventRepository() }
    val eventCoverRepo = remember { EventCoverRepository() }
    val avatarRepo = remember { AvatarRepository() }
    val context = LocalContext.current
    val contentResolver = context.contentResolver
    var recoveringPassword by rememberSaveable { mutableStateOf(false) }
    var recoveryReady by rememberSaveable { mutableStateOf(false) }
    var recoveryAttempt by rememberSaveable { mutableIntStateOf(0) }
    var recoveryError by remember { mutableStateOf<String?>(null) }
    var returnToSignIn by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(openedLink) {
        if (!isPasswordRecoveryLink(openedLink)) return@LaunchedEffect
        recoveringPassword = true
        recoveryAttempt++
        recoveryReady = false
        recoveryError = null
        try {
            authRepo.recoverPassword(openedLink!!)
            recoveryReady = true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            recoveryError = if (e is java.io.IOException) {
                "Couldn't check this reset link. Check your connection and request a new link."
            } else {
                "This reset link is invalid or has expired. Go back to sign in and request a new link."
            }
        } finally {
            onOpenedLinkHandled()
        }
    }
    // Remembers on this device which accounts have finished onboarding, so on later launches they
    // go straight in without waiting for the server, and still get in when offline.
    val prefs = remember { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }
    fun onboardedKey(uid: String) = "onboarded_$uid"

    var signedIn by rememberSaveable { mutableStateOf(authRepo.currentUser() != null) }
    // Kept as state for the event screens, which need it while they are on screen. Asking the
    // auth client each time would give null whenever the session is briefly re-initializing.
    var userId by rememberSaveable { mutableStateOf(authRepo.currentUserId()) }
    // The user the session has been confirmed for. Unlike userId this is not restored when the
    // app is recreated, so the profile is only read once the session is usable again; a read made
    // before that would come back empty and look like a brand-new account.
    var sessionUserId by remember { mutableStateOf<String?>(null) }
    // Whether this account has finished onboarding; null until that has been read. See appPhase.
    var onboardingComplete by rememberSaveable { mutableStateOf<Boolean?>(null) }
    // During onboarding: the profile wizard is done and the tab tutorial is what's left.
    var profileSetupDone by rememberSaveable { mutableStateOf(false) }
    var profileLoadFailed by remember { mutableStateOf(false) }
    // Bumped by "Try again" to read the profile once more.
    var profileLoadAttempt by remember { mutableIntStateOf(0) }
    var tab by rememberSaveable { mutableStateOf(Tab.CUE) }
    var status by rememberSaveable { mutableStateOf(CueStatus.GREEN) }
    var watch by remember { mutableStateOf(WatchConnection.DISCONNECTED) }
    var watchRetry by remember { mutableIntStateOf(0) }
    var watchSessionKnown by remember { mutableStateOf(false) }
    var watchProfileUserId by remember { mutableStateOf<String?>(null) }
    var watchPublishedUserId by remember { mutableStateOf(prefs.getString("watch_owner", null)) }
    var profile by remember { mutableStateOf(MockData.profile) }
    var editingProfile by rememberSaveable { mutableStateOf(false) }
    var showingSettings by rememberSaveable { mutableStateOf(false) }
    var creatingEvent by rememberSaveable { mutableStateOf(false) }
    // The event whose detail screen is open, if any.
    var openEvent by rememberSaveable(stateSaver = EventSaver) { mutableStateOf<Event?>(null) }
    var collection by remember { mutableStateOf<List<CollectedCard>>(emptyList()) }
    // The secret token behind this user's QR code and share link; null until it has loaded.
    var cardToken by rememberSaveable { mutableStateOf<String?>(null) }
    var cardTokenFailed by remember { mutableStateOf(false) }
    var swapping by remember { mutableStateOf(false) }
    var swapResult by remember { mutableStateOf<SwapResult?>(null) }
    // A card link that opened the app and is waiting for the user to agree to the swap.
    var linkToConfirm by rememberSaveable { mutableStateOf<String?>(null) }

    // Keep the signed-in flag in sync with the persisted Supabase session (auto-refresh, sign-out).
    LaunchedEffect(Unit) {
        authRepo.sessionStatus.collect { s ->
            // Only a definite answer changes the flag. The status goes back to Initializing every
            // time the app is backgrounded (e.g. while the camera is open); treating that as signed
            // out would flash the sign-in screen and throw away whatever screen the user was on.
            when (s) {
                is SessionStatus.Authenticated -> {
                    watchSessionKnown = true
                    val authenticatedUserId = authRepo.currentUserId()
                    if (userId != authenticatedUserId) {
                        watchProfileUserId = null
                        onboardingComplete = null
                        profileSetupDone = false
                        profileLoadFailed = false
                        cardToken = null
                        collection = emptyList()
                        creatingEvent = false
                        openEvent = null
                        editingProfile = false
                        showingSettings = false
                    }
                    signedIn = true
                    userId = authenticatedUserId
                    sessionUserId = userId
                }
                is SessionStatus.NotAuthenticated -> {
                    watchSessionKnown = true
                    watchProfileUserId = null
                    signedIn = false
                    userId = null
                    sessionUserId = null
                    // Whoever signs in next starts from "not known yet", not from this account's answer.
                    onboardingComplete = null
                    profileSetupDone = false
                    profileLoadFailed = false
                    // The next account to sign in must not land in this one's event.
                    creatingEvent = false
                    openEvent = null
                }
                else -> Unit
            }
        }
    }

    // Load the profile + status from the database once the session is confirmed, and with them
    // whether this account still has onboarding ahead of it.
    LaunchedEffect(sessionUserId, profileLoadAttempt) {
        val uid = sessionUserId ?: return@LaunchedEffect
        val onboardedHere = prefs.getBoolean(onboardedKey(uid), false)
        if (onboardedHere) onboardingComplete = true
        profileLoadFailed = false
        try {
            val row = profileRepo.loadProfile(uid)
            if (row != null) {
                profile = row.toProfile()
                status = row.toCueStatus()
            } else {
                // No row yet (e.g. older account): start blank and let the profile wizard create it.
                profile = Profile("", "", emptyList(), emptyList(), isPublic = true)
            }
            watchProfileUserId = uid
            when {
                row?.onboardingComplete == true -> {
                    prefs.edit().putBoolean(onboardedKey(uid), true).apply()
                    onboardingComplete = true
                }
                // Finished on this device, but the server never heard (it was offline): tell it now.
                onboardedHere -> profileRepo.markOnboardingComplete(uid)
                else -> onboardingComplete = false
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Only matters while the answer is still unknown; AccountLoadingScreen offers a retry.
            profileLoadFailed = true
        }
    }

    // Reload the binder on sign-in and each time it is opened, so a card disappears once the
    // other person has removed the swap.
    LaunchedEffect(signedIn, userId, tab) {
        if (!signedIn) {
            collection = emptyList()
        } else if (tab == Tab.BINDER || tab == Tab.CUE) {
            val uid = authRepo.currentUserId() ?: return@LaunchedEffect
            runCatching { swapRepo.loadBinder(uid) }.onSuccess { collection = it }
        }
    }

    // Load this user's card token on sign-in. If that fails (offline), every tab change tries
    // again until it works.
    LaunchedEffect(signedIn, userId, tab) {
        if (!signedIn) {
            cardToken = null
            cardTokenFailed = false
        } else if (cardToken == null) {
            runCatching { swapRepo.myCardToken() }
                .onSuccess { cardToken = it }
                .onFailure { cardTokenFailed = true }
        }
    }

    // Never publish the mock/previous account's profile while authentication or loading is pending.
    val companionSnapshot = when {
        !watchSessionKnown -> null
        !signedIn -> com.example.unspokenqueues.model.watchSnapshot(false, status, "", null, false)
        watchProfileUserId != sessionUserId || sessionUserId == null || onboardingComplete != true ->
            com.example.unspokenqueues.model.watchSnapshotWhileLoading(sessionUserId, watchPublishedUserId)
        else -> com.example.unspokenqueues.model.watchSnapshot(true, status, profile.displayName, cardToken, profile.isPublic)
    }
    com.example.unspokenqueues.data.WatchSync(companionSnapshot, sessionUserId, watchRetry,
        onConnection = { watch = it },
        onPublished = { owner ->
            if (watchPublishedUserId != owner) {
                prefs.edit().putString("watch_owner", owner).apply()
                watchPublishedUserId = owner
            }
        })

    // Swaps cards with whoever owns the card in [text]: a scanned QR code or a pasted link.
    fun collectCard(text: String) {
        val token = cardTokenFrom(text)
        when {
            token == null -> swapResult = SwapResult.Failed("That isn't an Unspoken Cues card code.")
            token == cardToken -> swapResult = SwapResult.Failed(swapFailureMessage(SwapRepository.OWN_CARD))
            !swapping -> {
                swapping = true
                scope.launch {
                    swapResult = try {
                        val card = swapRepo.swapByToken(token)
                        collection = (collection.filter { it.userId != card.userId } + card).sortedBy { it.name.lowercase() }
                        SwapResult.Swapped(card)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        SwapResult.Failed(swapFailureMessage((e as? PostgrestRestException)?.code))
                    } finally {
                        swapping = false
                    }
                }
            }
        }
    }

    val phase = appPhase(signedIn, onboardingComplete, profileSetupDone)

    fun signOut() {
        scope.launch { runCatching { authRepo.signOut() } }
        showingSettings = false
        tab = Tab.CUE
    }

    // A card link opened the app. Wait until the user is signed in and through onboarding (there
    // is no card of theirs to swap before that), then ask before swapping: unlike a scan, a link
    // can be followed by accident, and swapping shares this user's card too.
    LaunchedEffect(openedLink, phase) {
        if (openedLink != null && !isPasswordRecoveryLink(openedLink) && phase == AppPhase.MAIN) {
            linkToConfirm = openedLink
            onOpenedLinkHandled()
        }
    }
    linkToConfirm?.let { link ->
        ConfirmLinkSwapDialog(
            onConfirm = {
                linkToConfirm = null
                collectCard(link)
            },
            onDismiss = { linkToConfirm = null },
        )
    }
    if (swapping) {
        Dialog(onDismissRequest = {}) {
            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
                CircularProgressIndicator(Modifier.padding(24.dp).size(32.dp))
            }
        }
    }
    swapResult?.let { result ->
        SwapResultDialog(
            result = result,
            onViewBinder = {
                swapResult = null
                editingProfile = false
                showingSettings = false
                creatingEvent = false
                openEvent = null
                tab = Tab.BINDER
            },
            onDismiss = { swapResult = null },
        )
    }

    // A recovery session is authenticated, but must show password entry before onboarding/tabs.
    if (recoveringPassword || isPasswordRecoveryLink(openedLink)) {
        fun cancelRecovery() {
            scope.launch {
                if (recoveryReady) authRepo.cancelPasswordRecovery()
                recoveryReady = false
                recoveringPassword = false
                returnToSignIn = true
            }
        }
        Scaffold { padding ->
            Box(Modifier.padding(padding)) {
                key(recoveryAttempt) {
                    NewPasswordScreen(
                        ready = recoveryReady,
                        processing = isPasswordRecoveryLink(openedLink),
                        linkError = recoveryError,
                        onUpdatePassword = { authRepo.updatePassword(it) },
                        onCancel = ::cancelRecovery,
                        onContinue = {
                            recoveringPassword = false
                            recoveryReady = false
                        },
                    )
                }
            }
        }
        return
    }

    if (phase == AppPhase.SIGNED_OUT) {
        Scaffold { padding ->
            Box(Modifier.padding(padding)) {
                AuthFlow(
                    onSignIn = { email, password -> authRepo.signIn(email, password) },
                    onSignUp = { email, password -> authRepo.signUp(email, password) },
                    onPasswordReset = { authRepo.requestPasswordReset(it) },
                    startAtSignIn = returnToSignIn,
                )
            }
        }
        return
    }

    if (phase == AppPhase.LOADING) {
        Scaffold { padding ->
            Box(Modifier.padding(padding)) {
                AccountLoadingScreen(
                    failed = profileLoadFailed,
                    onRetry = { profileLoadAttempt++ },
                    onSignOut = ::signOut,
                )
            }
        }
        return
    }

    // Onboarding, for an account that hasn't finished it: set up the profile, then tour the tabs.
    if (phase == AppPhase.PROFILE_SETUP) {
        Scaffold { padding ->
            Box(Modifier.padding(padding)) {
                ProfileSetupWizard(
                    initial = profile,
                    onUploadAvatar = { uri ->
                        val uid = authRepo.currentUserId() ?: error("Not signed in")
                        avatarRepo.upload(contentResolver, uid, uri)
                    },
                    onFinish = { updated ->
                        // Saved before moving on; if this throws the wizard reports it and stays.
                        val uid = authRepo.currentUserId() ?: error("Not signed in")
                        profileRepo.upsertProfile(uid, updated, status)
                        profile = updated
                        profileSetupDone = true
                    },
                )
            }
        }
        return
    }

    if (phase == AppPhase.TUTORIAL) {
        Scaffold { padding ->
            Box(Modifier.padding(padding)) {
                // Skipping counts as finishing: either way onboarding is over and the tabs open.
                TabTutorialScreen(
                    onFinish = {
                        onboardingComplete = true
                        profileSetupDone = false
                        tab = Tab.CUE
                        val uid = userId
                        if (uid != null) {
                            prefs.edit().putBoolean(onboardedKey(uid), true).apply()
                            // If this doesn't reach the server, the next profile load sends it again.
                            scope.launch { runCatching { profileRepo.markOnboardingComplete(uid) } }
                        }
                    },
                )
            }
        }
        return
    }

    // Hosting an event and an event's detail are reached from the Events tab and cover it.
    val uid = userId
    val overlay = when {
        editingProfile -> Overlay.EDIT_PROFILE
        showingSettings -> Overlay.SETTINGS
        creatingEvent && uid != null -> Overlay.CREATE_EVENT
        openEvent != null && uid != null -> Overlay.EVENT_DETAIL
        else -> Overlay.NONE
    }
    // The detail screen keeps showing its event while it slides away after openEvent is cleared.
    var lastOpenEvent by remember { mutableStateOf<Event?>(null) }
    SideEffect { openEvent?.let { lastOpenEvent = it } }

    BackHandler(enabled = overlay != Overlay.NONE) {
        when (overlay) {
            Overlay.EDIT_PROFILE -> editingProfile = false
            Overlay.SETTINGS -> showingSettings = false
            Overlay.CREATE_EVENT -> creatingEvent = false
            Overlay.EVENT_DETAIL -> openEvent = null
            Overlay.NONE -> Unit
        }
    }

    AnimatedContent(
        targetState = overlay,
        transitionSpec = {
            // Going deeper slides in from the right; going back reverses it.
            val forward = targetState.depth > initialState.depth
            val direction = if (forward) 1 else -1
            (slideInHorizontally(tween(MotionMedium, easing = EaseOutQuart)) { direction * it / 4 } +
                fadeIn(tween(MotionMedium, easing = EaseOutQuart))) togetherWith
                (slideOutHorizontally(tween(MotionMedium, easing = EaseOutQuart)) { -direction * it / 8 } +
                    fadeOut(tween(MotionShort)))
        },
        label = "overlay",
    ) { shown ->
        when (shown) {
            Overlay.EDIT_PROFILE -> Scaffold { padding ->
                Box(Modifier.padding(padding)) {
                    EditProfileScreen(
                        initial = profile,
                        email = authRepo.currentUser()?.email,
                        status = status,
                        onUploadAvatar = { uri ->
                            val id = authRepo.currentUserId() ?: error("Not signed in")
                            avatarRepo.upload(contentResolver, id, uri)
                        },
                        onUploadCardImage = { uri ->
                            val id = authRepo.currentUserId() ?: error("Not signed in")
                            avatarRepo.uploadCardImage(contentResolver, id, uri)
                        },
                        onSave = { updated ->
                            val cardImageChanged = updated.cardImageUrl != profile.cardImageUrl
                            profile = updated
                            editingProfile = false
                            val id = authRepo.currentUserId()
                            if (id != null) {
                                scope.launch {
                                    runCatching { profileRepo.upsertProfile(id, updated, status) }
                                    // Its own call: the card image has its own column, and a failure here
                                    // must not undo the rest of the save.
                                    if (cardImageChanged) runCatching { profileRepo.updateCardImage(id, updated.cardImageUrl) }
                                }
                            }
                        },
                        onCancel = { editingProfile = false },
                    )
                }
            }
            // Settings is reached from the cog on the Profile screen rather than its own tab.
            Overlay.SETTINGS -> Scaffold { padding ->
                Box(Modifier.padding(padding)) {
                    SettingsScreen(
                        watch = watch,
                        themeMode = themeMode,
                        onThemeModeChange = onThemeModeChange,
                        onReconnect = {
                            watchRetry++
                        },
                        onSignOut = ::signOut,
                        onBack = { showingSettings = false },
                    )
                }
            }
            Overlay.CREATE_EVENT -> Scaffold { padding ->
                Box(Modifier.padding(padding)) {
                    if (uid != null) {
                        CreateEventScreen(
                            repo = eventRepo,
                            coverRepo = eventCoverRepo,
                            userId = uid,
                            hostName = profile.displayName,
                            onCreated = {
                                creatingEvent = false
                                openEvent = it
                            },
                            onCancel = { creatingEvent = false },
                        )
                    }
                }
            }
            Overlay.EVENT_DETAIL -> Scaffold { padding ->
                Box(Modifier.padding(padding)) {
                    val event = openEvent ?: lastOpenEvent
                    if (event != null && uid != null) {
                        EventDetailScreen(
                            repo = eventRepo,
                            userId = uid,
                            status = status,
                            event = event,
                            onEventChange = { openEvent = it },
                            onRemoved = { openEvent = null },
                            onBack = { openEvent = null },
                        )
                    }
                }
            }
            Overlay.NONE -> TabsScaffold(
                tab = tab,
                status = status,
                onTabChange = { tab = it },
            ) { shownTab ->
                when (shownTab) {
                    Tab.CUE -> CueScreen(status, watch) { newStatus ->
                        status = newStatus
                        val id = authRepo.currentUserId()
                        if (id != null) scope.launch { runCatching { profileRepo.updateStatus(id, newStatus) } }
                    }
                    Tab.EVENTS -> if (uid != null) {
                        EventsScreen(
                            repo = eventRepo,
                            userId = uid,
                            onHost = { creatingEvent = true },
                            onOpen = { openEvent = it },
                        )
                    }
                    Tab.QR -> QrScreen(
                        status = status,
                        profile = profile,
                        cardLink = cardToken?.let(::cardLink),
                        cardLinkFailed = cardTokenFailed,
                        onCollect = ::collectCard,
                    )
                    Tab.BINDER -> BinderScreen(
                        status = status,
                        profile = profile,
                        collection = collection,
                        onRemove = { card ->
                            val id = authRepo.currentUserId()
                            if (id != null) {
                                val before = collection
                                collection = collection - card
                                scope.launch {
                                    // Put the card back if the server didn't delete the swap.
                                    runCatching { swapRepo.removeSwap(id, card.userId) }.onFailure { collection = before }
                                }
                            }
                        },
                    )
                    Tab.PROFILE -> ProfileScreen(
                        profile = profile,
                        onEdit = { editingProfile = true },
                        onVisibilityChange = { isPublic ->
                            profile = profile.copy(isPublic = isPublic)
                            val id = authRepo.currentUserId()
                            if (id != null) scope.launch { runCatching { profileRepo.updateVisibility(id, isPublic) } }
                        },
                        onOpenSettings = { showingSettings = true },
                    )
                }
            }
        }
    }
}

// What covers the tabs, if anything. [depth] decides which way a change slides.
private enum class Overlay(val depth: Int) {
    NONE(0),
    EDIT_PROFILE(1),
    SETTINGS(1),
    CREATE_EVENT(1),
    EVENT_DETAIL(2),
}

// The tab bar and the current tab, which fades in when the tab changes.
@Composable
private fun TabsScaffold(tab: Tab, status: CueStatus, onTabChange: (Tab) -> Unit, content: @Composable (Tab) -> Unit) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = t == tab,
                        onClick = { onTabChange(t) },
                        icon = { Icon(painterResource(t.icon), contentDescription = t.label) },
                        label = { Text(t.label) },
                        // Ink pill under the chosen tab: the one place the bar is dark.
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                            selectedTextColor = MaterialTheme.colorScheme.onSurface,
                            indicatorColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        AnimatedContent(
            targetState = tab,
            modifier = Modifier.padding(padding).cueAmbience(status.color),
            transitionSpec = {
                fadeIn(tween(MotionMedium, delayMillis = 60, easing = EaseOutQuart)) togetherWith fadeOut(tween(MotionShort - 60))
            },
            label = "tab",
        ) { shownTab ->
            Box(Modifier.fillMaxSize()) { content(shownTab) }
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun AppPreview() {
    UnspokenQueuesTheme { UnspokenCuesApp() }
}
