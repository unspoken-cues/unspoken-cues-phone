package com.example.unspokenqueues

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import com.example.unspokenqueues.model.CueStatus
import com.example.unspokenqueues.model.MockData
import com.example.unspokenqueues.model.WatchConnection
import com.example.unspokenqueues.ui.screens.BinderScreen
import com.example.unspokenqueues.ui.screens.CueScreen
import com.example.unspokenqueues.ui.screens.EditProfileScreen
import com.example.unspokenqueues.ui.screens.ProfileScreen
import com.example.unspokenqueues.ui.screens.QrScreen
import com.example.unspokenqueues.ui.screens.SettingsScreen
import com.example.unspokenqueues.ui.screens.SignInScreen
import com.example.unspokenqueues.ui.theme.UnspokenQueuesTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            UnspokenQueuesTheme { UnspokenCuesApp() }
        }
    }
}

enum class Tab(val label: String, @DrawableRes val icon: Int) {
    CUE("Cue", R.drawable.ic_cue),
    PROFILE("Profile", R.drawable.ic_profile),
    QR("QR", R.drawable.ic_qr),
    BINDER("Binder", R.drawable.ic_binder),
    SETTINGS("Settings", R.drawable.ic_settings),
}

@Composable
fun UnspokenCuesApp() {
    var signedIn by rememberSaveable { mutableStateOf(false) }
    var tab by rememberSaveable { mutableStateOf(Tab.CUE) }
    var status by rememberSaveable { mutableStateOf(CueStatus.GREEN) }
    var watch by rememberSaveable { mutableStateOf(WatchConnection.CONNECTED) }
    // Not saveable yet: resets on rotation / process death until local storage is added.
    var profile by remember { mutableStateOf(MockData.profile) }
    var editingProfile by rememberSaveable { mutableStateOf(false) }

    if (!signedIn) {
        Scaffold { padding ->
            Box(Modifier.padding(padding)) { SignInScreen(onSignIn = { signedIn = true }) }
        }
        return
    }

    if (editingProfile) {
        BackHandler { editingProfile = false }
        Scaffold { padding ->
            Box(Modifier.padding(padding)) {
                EditProfileScreen(
                    initial = profile,
                    onSave = { profile = it; editingProfile = false },
                    onCancel = { editingProfile = false },
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
                Tab.CUE -> CueScreen(status, watch) { status = it }
                Tab.PROFILE -> ProfileScreen(
                    profile = profile,
                    onEdit = { editingProfile = true },
                    onVisibilityChange = { profile = profile.copy(isPublic = it) },
                )
                Tab.QR -> QrScreen(status, profile)
                Tab.BINDER -> BinderScreen(status, profile)
                Tab.SETTINGS -> SettingsScreen(
                    watch = watch,
                    onReconnect = {
                        watch = if (watch == WatchConnection.CONNECTED) WatchConnection.DISCONNECTED else WatchConnection.CONNECTED
                    },
                    onSignOut = { signedIn = false; tab = Tab.CUE },
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
