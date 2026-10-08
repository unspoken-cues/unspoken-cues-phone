package com.example.unspokenqueues

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.DrawableRes
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.unspokenqueues.data.AuthRepository
import com.example.unspokenqueues.data.AvatarRepository
import com.example.unspokenqueues.data.ProfileRepository
import com.example.unspokenqueues.data.SwapRepository
import com.example.unspokenqueues.data.swapFailureMessage
import com.example.unspokenqueues.data.toCueStatus
import com.example.unspokenqueues.data.toProfile
import com.example.unspokenqueues.model.CollectedCard
import com.example.unspokenqueues.model.CueStatus
import com.example.unspokenqueues.model.MockData
import com.example.unspokenqueues.model.SwapResult
import com.example.unspokenqueues.model.ThemeMode
import com.example.unspokenqueues.model.WatchConnection
import com.example.unspokenqueues.model.cardLink
import com.example.unspokenqueues.model.cardTokenFrom
import com.example.unspokenqueues.ui.screens.BinderScreen
import com.example.unspokenqueues.ui.screens.ConfirmLinkSwapDialog
import com.example.unspokenqueues.ui.screens.CueScreen
import com.example.unspokenqueues.ui.screens.EditProfileScreen
import com.example.unspokenqueues.ui.screens.EventsScreen
import com.example.unspokenqueues.ui.screens.ProfileScreen
import com.example.unspokenqueues.ui.screens.QrScreen
import com.example.unspokenqueues.ui.screens.SettingsScreen
import com.example.unspokenqueues.ui.screens.SignInScreen
import com.example.unspokenqueues.ui.screens.SwapResultDialog
import com.example.unspokenqueues.ui.theme.UnspokenQueuesTheme
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

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
    val avatarRepo = remember { AvatarRepository() }
    val contentResolver = LocalContext.current.contentResolver

    var signedIn by rememberSaveable { mutableStateOf(authRepo.currentUser() != null) }
    var tab by rememberSaveable { mutableStateOf(Tab.CUE) }
    var status by rememberSaveable { mutableStateOf(CueStatus.GREEN) }
    var watch by rememberSaveable { mutableStateOf(WatchConnection.CONNECTED) }
    var profile by remember { mutableStateOf(MockData.profile) }
    var editingProfile by rememberSaveable { mutableStateOf(false) }
    var showingSettings by rememberSaveable { mutableStateOf(false) }
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
                is SessionStatus.Authenticated -> signedIn = true
                is SessionStatus.NotAuthenticated -> signedIn = false
                else -> Unit
            }
        }
    }

    // Load the profile + status from the database whenever we become signed in.
    LaunchedEffect(signedIn) {
        if (signedIn) {
            val uid = authRepo.currentUserId() ?: return@LaunchedEffect
            runCatching {
                val row = profileRepo.loadProfile(uid)
                if (row != null) {
                    profile = row.toProfile()
                    status = row.toCueStatus()
                } else {
                    // No row yet (e.g. older account): seed one from current local state.
                    profileRepo.upsertProfile(uid, profile, status)
                }
            }
        }
    }

    // Reload the binder on sign-in and each time it is opened, so a card disappears once the
    // other person has removed the swap.
    LaunchedEffect(signedIn, tab) {
        if (!signedIn) {
            collection = emptyList()
        } else if (tab == Tab.BINDER || tab == Tab.CUE) {
            val uid = authRepo.currentUserId() ?: return@LaunchedEffect
            runCatching { swapRepo.loadBinder(uid) }.onSuccess { collection = it }
        }
    }

    // Load this user's card token on sign-in. If that fails (offline), every tab change tries
    // again until it works.
    LaunchedEffect(signedIn, tab) {
        if (!signedIn) {
            cardToken = null
            cardTokenFailed = false
        } else if (cardToken == null) {
            runCatching { swapRepo.myCardToken() }
                .onSuccess { cardToken = it }
                .onFailure { cardTokenFailed = true }
        }
    }

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

    // A card link opened the app. Wait for sign-in, then ask before swapping: unlike a scan, a
    // link can be followed by accident, and swapping shares this user's card too.
    LaunchedEffect(openedLink, signedIn) {
        if (openedLink != null && signedIn) {
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
                tab = Tab.BINDER
            },
            onDismiss = { swapResult = null },
        )
    }

    if (!signedIn) {
        Scaffold { padding ->
            Box(Modifier.padding(padding)) {
                SignInScreen(
                    onSignIn = { email, password -> authRepo.signIn(email, password) },
                    onSignUp = { email, password -> authRepo.signUp(email, password) },
                )
            }
        }
        return
    }

    if (editingProfile) {
        BackHandler { editingProfile = false }
        Scaffold { padding ->
            Box(Modifier.padding(padding)) {
                EditProfileScreen(
                    initial = profile,
                    email = authRepo.currentUser()?.email,
                    onUploadAvatar = { uri ->
                        val uid = authRepo.currentUserId() ?: error("Not signed in")
                        avatarRepo.upload(contentResolver, uid, uri)
                    },
                    onSave = { updated ->
                        profile = updated
                        editingProfile = false
                        val uid = authRepo.currentUserId()
                        if (uid != null) scope.launch { runCatching { profileRepo.upsertProfile(uid, updated, status) } }
                    },
                    onCancel = { editingProfile = false },
                )
            }
        }
        return
    }

    // Settings is reached from the cog on the Profile screen rather than its own tab.
    if (showingSettings) {
        BackHandler { showingSettings = false }
        Scaffold { padding ->
            Box(Modifier.padding(padding)) {
                SettingsScreen(
                    watch = watch,
                    themeMode = themeMode,
                    onThemeModeChange = onThemeModeChange,
                    onReconnect = {
                        watch = if (watch == WatchConnection.CONNECTED) WatchConnection.DISCONNECTED else WatchConnection.CONNECTED
                    },
                    onSignOut = {
                        scope.launch { runCatching { authRepo.signOut() } }
                        showingSettings = false
                        tab = Tab.CUE
                    },
                    onBack = { showingSettings = false },
                )
            }
        }
        return
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = t == tab,
                        onClick = { tab = t },
                        icon = { Icon(painterResource(t.icon), contentDescription = t.label) },
                        label = { Text(t.label) },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (tab) {
                Tab.CUE -> CueScreen(status, watch) { newStatus ->
                    status = newStatus
                    val uid = authRepo.currentUserId()
                    if (uid != null) scope.launch { runCatching { profileRepo.updateStatus(uid, newStatus) } }
                }
                Tab.EVENTS -> EventsScreen()
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
                        val uid = authRepo.currentUserId()
                        if (uid != null) {
                            val before = collection
                            collection = collection - card
                            scope.launch {
                                // Put the card back if the server didn't delete the swap.
                                runCatching { swapRepo.removeSwap(uid, card.userId) }.onFailure { collection = before }
                            }
                        }
                    },
                )
                Tab.PROFILE -> ProfileScreen(
                    profile = profile,
                    onEdit = { editingProfile = true },
                    onVisibilityChange = { isPublic ->
                        profile = profile.copy(isPublic = isPublic)
                        val uid = authRepo.currentUserId()
                        if (uid != null) scope.launch { runCatching { profileRepo.updateVisibility(uid, isPublic) } }
                    },
                    onOpenSettings = { showingSettings = true },
                )
            }
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun AppPreview() {
    UnspokenQueuesTheme { UnspokenCuesApp() }
}
