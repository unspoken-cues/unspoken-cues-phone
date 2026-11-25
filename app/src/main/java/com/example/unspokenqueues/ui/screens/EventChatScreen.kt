package com.example.unspokenqueues.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.example.unspokenqueues.R
import com.example.unspokenqueues.data.chat.ChatAccess
import com.example.unspokenqueues.data.chat.ChatMessage
import com.example.unspokenqueues.data.chat.ChatThread
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

/** The inbox is also reachable directly from the event, independently of its directory contents. */
@Composable
fun EventChatScreen(
    eventId: String,
    eventName: String,
    access: ChatAccess,
    onBack: () -> Unit,
    initialPeerId: String? = null,
    initialPeerName: String? = null,
) {
    key(eventId, access, initialPeerId) {
        var peerId by remember { mutableStateOf(initialPeerId) }
        var newRequest by remember { mutableStateOf(initialPeerId != null) }
        val draft = remember(peerId) { MessageDraft() }
        var threads by remember { mutableStateOf<List<ChatThread>?>(null) }
        var loadError by remember { mutableStateOf(false) }
        var refresh by remember { mutableIntStateOf(0) }
        val owner = LocalLifecycleOwner.current
        val thread = threads?.firstOrNull { it.peerId == peerId }
        LaunchedEffect(owner, refresh, peerId) {
            owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    try {
                        val fresh = access.threads(eventId)
                        if (fresh.any { it.peerId == peerId }) newRequest = false
                        if (!newRequest && fresh.none { it.peerId == peerId && it.state == "accepted" }) draft.clear()
                        threads = fresh
                        loadError = false
                    } catch (cancel: CancellationException) {
                        throw cancel
                    } catch (error: Exception) {
                        threads = null
                        loadError = true
                    }
                    delay(3_000)
                }
            }
        }
        fun back() {
            if (peerId == null) onBack() else {
                peerId = null
                newRequest = false
            }
        }
        BackHandler { back() }
        val title = when {
            peerId == null -> "Event inbox"
            thread != null -> thread.peerName.ifBlank { "Attendee" }
            newRequest -> initialPeerName.orEmpty().ifBlank { "Attendee" }
            else -> "Conversation"
        }
        Column(Modifier.fillMaxSize().imePadding().padding(horizontal = 16.dp)) {
            Row(Modifier.fillMaxWidth().heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton({ back() }) {
                    Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = if (peerId == null) "Back to event" else "Back to inbox")
                }
                Column(Modifier.weight(1f).padding(horizontal = 4.dp, vertical = 8.dp)) {
                    Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(eventName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            HorizontalDivider()
            when {
                loadError -> Column(Modifier.padding(vertical = 16.dp)) {
                    Text("The inbox is unavailable. Check your connection and event access. A replacement phone may need a new chat identity.")
                    TextButton({ refresh++ }) { Text("Try again") }
                }
                threads == null -> Text("Opening inbox…", Modifier.padding(vertical = 20.dp))
                peerId == null -> {
                    val current = threads.orEmpty()
                    if (current.isEmpty()) {
                        Text("Your inbox is empty. Open an attendee’s chat from the event to send a request.", Modifier.padding(vertical = 20.dp))
                    } else {
                        LazyColumn {
                            items(current, key = { it.id }) { row ->
                                ListItem(
                                    headlineContent = { Text(row.peerName.ifBlank { "Attendee" }) },
                                    supportingContent = { Text(row.statusLabel()) },
                                    modifier = Modifier.fillMaxWidth().clickable { peerId = row.peerId },
                                )
                                HorizontalDivider()
                            }
                        }
                    }
                }
                thread != null || newRequest -> key(peerId) {
                    Conversation(
                        eventId, peerId!!, title, thread, access, draft,
                        onRefresh = { refresh++ },
                        onBlocked = { threads = threads?.filterNot { it.peerId == peerId }; back(); refresh++ },
                    )
                }
                else -> Text("This conversation is unavailable.", Modifier.padding(vertical = 20.dp))
            }
        }
    }
}

private fun ChatThread.statusLabel(): String = when (state) {
    "accepted" -> "Encrypted conversation"
    "declined" -> "Request declined"
    "pending" -> if (incoming) "Chat request" else "Request sent"
    else -> "Conversation unavailable"
}

@Composable
private fun Conversation(
    eventId: String,
    peerId: String,
    name: String,
    thread: ChatThread?,
    access: ChatAccess,
    draft: MessageDraft,
    onRefresh: () -> Unit,
    onBlocked: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var actionError by remember { mutableStateOf<String?>(null) }
    var blockConfirm by remember { mutableStateOf(false) }
    var verifying by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var messages by remember(thread) { mutableStateOf<List<ChatMessage>?>(null) }
    var hasEarlier by remember(thread) { mutableStateOf(false) }
    var loadEarlier by remember(thread) { mutableStateOf(false) }
    var historyError by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    val owner = LocalLifecycleOwner.current
    val accepted = thread?.state == "accepted"
    LaunchedEffect(owner, thread, refresh) {
        if (thread == null || !accepted) return@LaunchedEffect
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                try {
                    val previous = messages.orEmpty()
                    val cursor = previous.firstOrNull()
                    val older = if (loadEarlier && cursor != null) access.messages(eventId, thread, cursor) else null
                    // Recheck the latest page last, including after older-page requests. An empty latest
                    // page also represents a revoked conversation and discards every displayed row.
                    val latest = access.messages(eventId, thread)
                    currentCoroutineContext().ensureActive()
                    if (draft.confirmDelivered(latest)) actionError = null
                    val known = if (older.isNullOrEmpty()) previous else older + previous
                    val overlap = if (latest.size == 100) known.indexOfFirst { it.id == latest.first().id } else -1
                    messages = if (overlap >= 0) known.take(overlap) + latest else latest
                    hasEarlier = when {
                        latest.size < 100 -> false
                        overlap < 0 -> true // A whole new page arrived since the last poll.
                        older != null -> older.size == 100
                        else -> hasEarlier
                    }
                    loadEarlier = false
                    historyError = false
                } catch (cancel: CancellationException) {
                    throw cancel
                } catch (error: Exception) {
                    messages = null
                    hasEarlier = false
                    loadEarlier = false
                    historyError = true
                }
                delay(3_000)
            }
        }
    }
    fun perform(failure: String, action: suspend () -> Unit) {
        if (busy) return
        busy = true
        actionError = null
        scope.launch {
            try {
                action()
                onRefresh()
                refresh++
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Exception) {
                actionError = failure
            } finally {
                busy = false
            }
        }
    }
    if (thread != null) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(thread.statusLabel(), Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
            Box {
                IconButton({ menu = true }) {
                    Icon(painterResource(R.drawable.ic_more_vert), contentDescription = "Conversation options")
                }
                DropdownMenu(menu, { menu = false }) {
                    if (accepted) DropdownMenuItem(
                        text = { Text("Verify device keys") },
                        onClick = { menu = false; verifying = true },
                    )
                    DropdownMenuItem(text = { Text("Block attendee") }, onClick = { menu = false; blockConfirm = true })
                }
            }
        }
    }
    actionError?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 8.dp)) }
    when {
        thread == null -> Column(Modifier.padding(vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Send a request to $name? You can exchange messages once they accept.")
            Button({ perform("Couldn’t send the request. Check your connection and event access.") { access.request(eventId, peerId) } }, enabled = !busy) {
                Text("Request chat")
            }
        }
        thread.state == "pending" && thread.incoming -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("$name would like to chat with you at this event.")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button({ perform("Couldn’t accept this request. Try again.") { access.respond(thread.id, true) } }, enabled = !busy) { Text("Accept") }
                OutlinedButton({ perform("Couldn’t decline this request. Try again.") { access.respond(thread.id, false) } }, enabled = !busy) { Text("Decline") }
            }
        }
        thread.state == "pending" -> Text("Waiting for $name to accept.")
        accepted -> {
            if (historyError) {
                Text("Messages are unavailable. Check your connection and device keys.", color = MaterialTheme.colorScheme.error)
                TextButton({ refresh++; onRefresh() }) { Text("Try again") }
            }
            MessageComposer(
                messages, draft, busy, hasEarlier, loadEarlier,
                onEarlier = { loadEarlier = true; refresh++ },
                enabled = !historyError && messages != null && thread.peerKey != null,
                onSend = { id, text, sent ->
                    perform("Couldn’t send. Check your connection and device keys, then retry.") {
                        try {
                            access.send(eventId, thread, id, text)
                            sent()
                        } catch (cancel: CancellationException) {
                            throw cancel
                        } catch (error: Exception) {
                            // Polling may already have seen this exact authenticated outgoing row.
                            if (draft.isPending(id)) throw error
                        }
                    }
                },
            )
        }
    }
    if (verifying && thread != null) {
        DeviceKeys(name, access, thread) { verifying = false }
    }
    if (blockConfirm) AlertDialog(
        onDismissRequest = { blockConfirm = false },
        title = { Text("Block $name?") },
        text = { Text("This closes your chats with this attendee and removes you from each other’s binders.") },
        confirmButton = {
            TextButton({
                blockConfirm = false
                perform("Couldn’t block this attendee. Try again.") { access.block(peerId); onBlocked() }
            }, enabled = !busy) { Text("Block", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton({ blockConfirm = false }) { Text("Cancel") } },
    )
}

// Lives only in memory for one account, event and peer. A transient inbox error may unmount the
// composer after the server committed a send, so keep its retry ID above the loading/error views.
private class MessageDraft {
    var text by mutableStateOf("")
        private set
    private var pendingId: String? = null

    fun edit(value: String) {
        if (value != text) {
            text = value
            pendingId = null
        }
    }

    fun requestId(): String = pendingId ?: UUID.randomUUID().toString().also { pendingId = it }
    fun isPending(id: String): Boolean = pendingId == id

    fun confirmDelivered(latest: List<ChatMessage>): Boolean {
        val id = pendingId ?: return false
        if (latest.none { it.id == id && it.mine && it.readable && it.text == text }) return false
        clear()
        return true
    }

    fun clear() { text = ""; pendingId = null }
}

@Composable
private fun MessageComposer(
    messages: List<ChatMessage>?,
    draft: MessageDraft,
    busy: Boolean,
    hasEarlier: Boolean,
    loadingEarlier: Boolean,
    onEarlier: () -> Unit,
    enabled: Boolean,
    onSend: (String, String, () -> Unit) -> Unit,
) {
    val draftBytes = remember(draft.text) { draft.text.toByteArray().size }
    val list = rememberLazyListState()
    // Reverse layout keeps the newest message at the bottom, without moving a reader in history.
    val newestFirst = remember(messages) { messages.orEmpty().asReversed() }
    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            reverseLayout = true, state = list,
            contentPadding = PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.Bottom),
        ) {
            items(newestFirst, key = { it.id }) { line ->
                Box(Modifier.fillMaxWidth(), contentAlignment = if (line.mine) Alignment.CenterEnd else Alignment.CenterStart) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (line.mine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.widthIn(max = 320.dp),
                    ) {
                        Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                            Text(if (line.mine) "You" else "Received", style = MaterialTheme.typography.labelSmall)
                            if (line.readable) {
                                Text(line.text, style = MaterialTheme.typography.bodyLarge)
                            } else {
                                Text("Message could not be verified", style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
            if (hasEarlier) item(key = "older-page") {
                TextButton(onEarlier, enabled = !loadingEarlier, modifier = Modifier.fillMaxWidth()) {
                    Text(if (loadingEarlier) "Loading earlier messages…" else "Load earlier messages")
                }
            }
            if (messages != null && messages.isEmpty()) item { Text("Say hello when you’re ready.", style = MaterialTheme.typography.bodyMedium) }
        }
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = draft.text,
                onValueChange = draft::edit,
                enabled = !busy,
                modifier = Modifier.weight(1f),
                label = { Text("Message") }, maxLines = 4,
                isError = draftBytes > 4096,
                supportingText = if (draftBytes > 4096) ({ Text("Use up to 4,096 bytes of text") }) else null,
            )
            Button(
                {
                    onSend(draft.requestId(), draft.text, draft::clear)
                },
                enabled = !busy && enabled && draft.text.isNotBlank() && draftBytes <= 4096,
            ) { Text("Send") }
        }
    }
}

@Composable
private fun DeviceKeys(name: String, access: ChatAccess, thread: ChatThread, onDismiss: () -> Unit) {
    var keys by remember(access, thread) { mutableStateOf<Pair<String, String>?>(null) }
    var error by remember { mutableStateOf(false) }
    LaunchedEffect(access, thread) {
        try {
            keys = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val peer = requireNotNull(access.peerFingerprint(thread))
                access.myFingerprint() to peer
            }
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (failure: Exception) {
            error = true
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Verify device keys") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Compare these codes in person or on a trusted call. Each person’s code should match on both phones.")
                val codes = keys
                if (codes != null) {
                    Text("Your device", fontWeight = FontWeight.SemiBold)
                    Text(codes.first, fontFamily = FontFamily.Monospace)
                    Text("$name’s device", fontWeight = FontWeight.SemiBold)
                    Text(codes.second, fontFamily = FontFamily.Monospace)
                } else Text(if (error) "Device keys could not be verified." else "Reading keys…")
            }
        },
        confirmButton = { TextButton(onDismiss) { Text("Done") } },
    )
}
