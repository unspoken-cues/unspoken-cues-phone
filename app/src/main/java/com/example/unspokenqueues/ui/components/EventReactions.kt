package com.example.unspokenqueues.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.unspokenqueues.data.ReactionRepository
import com.example.unspokenqueues.model.CueStatus
import com.example.unspokenqueues.model.Event
import com.example.unspokenqueues.model.ReactionState
import com.example.unspokenqueues.model.effectiveEnd
import com.example.unspokenqueues.model.withLocalEligibility
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Kept separate and injectable so event, RSVP and attendee behavior remain independent. */
@Composable
fun EventReactionsSection(
    repository: ReactionRepository,
    event: Event,
    userId: String,
    status: CueStatus,
    profileVisibleForWaves: Boolean = false,
) {
    if (userId.isBlank()) {
        SectionCard(title = "Waves") { Text("Sign in to use waves.") }
        return
    }
    // Disposal cancels pending work and drops every private result when event/account/cue changes.
    key(repository, event.id, userId, status, profileVisibleForWaves, event.active, event.effectiveEnd()) {
        val end = event.effectiveEnd()
        var expired by remember { mutableStateOf(end?.let { !Instant.now().isBefore(it) } ?: false) }
        LaunchedEffect(end) {
            if (end != null) {
                delay(Duration.between(Instant.now(), end).toMillis().coerceAtLeast(0))
                expired = true
            }
        }
        val localCueAllows = profileVisibleForWaves && (status == CueStatus.GREEN || status == CueStatus.YELLOW)
        EventReactionsContent(repository, event.id, userId, localCueAllows && event.active && !expired) {
            localCueAllows && event.active && (end == null || Instant.now().isBefore(end))
        }
    }
}

@Composable
private fun EventReactionsContent(
    repository: ReactionRepository,
    eventId: String,
    userId: String,
    locallyEligible: Boolean,
    canSendNow: () -> Boolean,
) {
    var state by remember { mutableStateOf<ReactionState?>(null) }
    var loading by remember { mutableStateOf(true) }
    var working by remember { mutableStateOf(false) }
    var sendingTo by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var retry by remember { mutableStateOf<(() -> Unit)?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val timestampFormat = remember {
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withZone(ZoneId.systemDefault())
    }

    fun refresh() {
        if (loading || working) return
        // Never retain a stale private inbox when a fresh read fails.
        state = null
        loading = true
        error = null
        retry = null
        reload++
    }

    LaunchedEffect(reload) {
        try {
            state = repository.state(eventId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            state = null
            error = "Couldn't load waves. Join this event if needed, then refresh and try again."
        } finally {
            loading = false
        }
    }

    fun saveConsent(enabled: Boolean) {
        if (working || loading || state == null) return
        working = true
        error = null
        retry = null
        // Hide the inbox immediately while an opt-out is pending. Success still requires an ack.
        if (!enabled) state = state?.copy(canWave = false, recipients = emptyList(), inbox = emptyList())
        scope.launch {
            try {
                state = repository.setReceiveWaves(eventId, enabled)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // The outcome may be unknown; fail closed until a fresh read or a retry succeeds.
                state = null
                error = "Couldn't save Receive waves. Your setting is unconfirmed."
                retry = {
                    if (!working && !loading) {
                        // Restore only the setting control, never previous private history.
                        state = ReactionState(!enabled, false, emptyList(), emptyList())
                        saveConsent(enabled)
                    }
                }
            } finally {
                working = false
            }
        }
    }

    fun send(recipientId: String) {
        if (working || loading || !canSendNow() || state?.canWave != true || recipientId == userId ||
            state?.recipients?.none { it.userId == recipientId && !it.sent } != false
        ) return
        working = true
        sendingTo = recipientId
        error = null
        retry = null
        scope.launch {
            try {
                repository.send(eventId, recipientId)
                // Only the acknowledged sender receipt marks a button sent.
                state = state?.let { current ->
                    current.copy(recipients = current.recipients.map {
                        if (it.userId == recipientId) it.copy(sent = true) else it
                    })
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // A denial can mean a participant became private. Drop old names/history and
                // recheck before offering a retry; never retain a stale inbox on refresh failure.
                state = null
                error = "Couldn't send the wave. Consent or visibility may have changed. Refresh waves or retry."
                try {
                    state = repository.state(eventId)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // Refresh waves remains available once this operation completes.
                }
                if (state?.canWave == true && state?.recipients?.any { it.userId == recipientId && !it.sent } == true) {
                    retry = { send(recipientId) }
                }
            } finally {
                working = false
                sendingTo = null
            }
        }
    }

    SectionCard(title = "Waves") {
        Text(
            "A quiet hello, without chat or a S.W.A.P. connection. No notifications or vibration.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (loading) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(24.dp))
                Text("Loading waves…")
            }
        }
        val current = state?.withLocalEligibility(locallyEligible)
        if (current != null) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Receive waves", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                Switch(
                    checked = current.receiveWaves,
                    onCheckedChange = ::saveConsent,
                    enabled = !working && !loading,
                    modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "Receive waves" },
                )
            }
            Text(
                "Off by default for each event. Turning this on lets other opted-in participants wave when both of you share a public profile and a visible Green or Yellow cue. Turning it off hides your inbox and stops incoming and outgoing waves.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (working && sendingTo == null) Text("Saving Receive waves…", style = MaterialTheme.typography.bodySmall)
            when {
                !current.receiveWaves -> Text("Waves are off for this event.")
                !current.canWave -> Text("Waves need an active event, Private Mode off, a public profile and a visible Green or Yellow cue.")
                else -> {
                    HorizontalDivider()
                    Text("Wave to a participant", style = MaterialTheme.typography.titleSmall)
                    val recipients = current.recipients.filter { it.userId != userId }
                    if (recipients.isEmpty()) Text("No one is available for a wave right now.", style = MaterialTheme.typography.bodySmall)
                    recipients.forEach { person ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(person.displayName, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            OutlinedButton(
                                onClick = { send(person.userId) },
                                enabled = !working && !loading && !person.sent,
                                modifier = Modifier.heightIn(min = 48.dp).semantics {
                                    contentDescription = if (person.sent) "Wave sent to ${person.displayName}" else "Wave to ${person.displayName}"
                                },
                            ) { Text(if (person.sent) "Sent" else if (sendingTo == person.userId) "Sending…" else "Wave") }
                        }
                    }
                    HorizontalDivider()
                    Text("Waves for you", style = MaterialTheme.typography.titleSmall)
                    Text("Only you can read this inbox. Refresh to check for new waves.", style = MaterialTheme.typography.bodySmall)
                    if (current.inbox.isEmpty()) Text("No waves for you yet.", style = MaterialTheme.typography.bodySmall)
                    current.inbox.forEach { wave ->
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("${wave.senderName} waved", style = MaterialTheme.typography.bodyMedium)
                            Text(timestampFormat.format(wave.createdAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
        retry?.takeIf { state == null || current?.canWave == true }?.let { action ->
            TextButton(action, enabled = !working && !loading, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(if (state == null) "Retry setting" else "Retry wave")
            }
        }
        TextButton(::refresh, enabled = !working && !loading, modifier = Modifier.heightIn(min = 48.dp)) { Text("Refresh waves") }
    }
}
