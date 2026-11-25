package com.example.unspokenqueues.debug

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.unspokenqueues.data.chat.ChatRepository
import com.example.unspokenqueues.ui.screens.EventChatScreen
import com.example.unspokenqueues.ui.theme.UnspokenQueuesTheme
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.withContext
import java.io.File

/** Exercises the same SQL, repository, device keys and chat screens as the signed-in app. */
class ChatLabActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val tokens = File(noBackupFilesDir, "event-chat-lab-tokens")
            .takeIf { it.isFile }?.readLines().orEmpty()
        setContent {
            UnspokenQueuesTheme {
                Surface(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
                    if (tokens.size == 2) Lab(tokens)
                    else Text("Set up the local event chat lab from the project first.", Modifier.padding(24.dp))
                }
            }
        }
    }

    @Composable
    private fun Lab(tokens: List<String>) {
        var actor by rememberSaveable { mutableIntStateOf(-1) }
        var screen by rememberSaveable { mutableStateOf("home") }
        val names = listOf("Alex", "Blair")
        if (actor < 0 || screen == "home") {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Event chat lab", style = MaterialTheme.typography.headlineMedium)
                Text("Both sample attendees are registered for the Community Meetup. Choose a phone identity to test requests and messages.")
                names.forEachIndexed { index, name ->
                    Button(onClick = { actor = index; screen = "attendee" }) { Text("Use as $name") }
                }
            }
            return
        }
        val peer = 1 - actor
        val userId = IDS[actor]
        val rest = remember(userId) {
            createSupabaseClient(LOCAL_API, tokens[actor]) {
                install(Postgrest) { customUrl = LOCAL_API; jwtToken = tokens[actor] }
            }
        }
        LaunchedEffect(rest) {
            try { awaitCancellation() } finally { withContext(NonCancellable) { rest.close() } }
        }
        val repo = remember(rest) { ChatRepository(applicationContext, userId, { rest }, { userId }) }
        key(userId) {
            if (screen == "attendee") {
                androidx.activity.compose.BackHandler { screen = "home" }
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Community Meetup", style = MaterialTheme.typography.headlineMedium)
                    Text("Signed in as " + names[actor])
                    Button(onClick = { screen = "inbox" }) { Text("Open event inbox") }
                    OutlinedButton(onClick = { screen = "peer" }) { Text("Chat with " + names[peer]) }
                    TextButton(onClick = { screen = "home" }) { Text("Switch attendee") }
                }
            } else {
                EventChatScreen(
                    eventId = EVENT, eventName = "Community Meetup", access = repo,
                    onBack = { screen = "attendee" },
                    initialPeerId = if (screen == "peer") IDS[peer] else null,
                    initialPeerName = if (screen == "peer") names[peer] else null,
                )
            }
        }
    }

    private companion object {
        const val LOCAL_API = "http://127.0.0.1:18764"
        const val EVENT = "60000000-0000-4000-8000-000000000010"
        val IDS = listOf("60000000-0000-4000-8000-000000000001", "60000000-0000-4000-8000-000000000002")
    }
}
