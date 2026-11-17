package com.example.unspokenqueues.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.example.unspokenqueues.data.AnnouncementRepository
import com.example.unspokenqueues.model.Announcement
import com.example.unspokenqueues.model.Event
import com.example.unspokenqueues.model.MAX_ANNOUNCEMENT_LENGTH
import com.example.unspokenqueues.model.announcementFeed
import com.example.unspokenqueues.model.announcementLength
import com.example.unspokenqueues.model.canPostAnnouncement
import com.example.unspokenqueues.model.effectiveEnd
import com.example.unspokenqueues.model.formatAnnouncementTime
import com.example.unspokenqueues.model.validAnnouncement
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.util.UUID

/** Called only for the known host or a loaded membership; SQL remains the authority. */
@Composable
fun EventAnnouncements(event: Event, userId: String, repository: AnnouncementRepository) {
    // Changing event or account disposes the old scope, including pending loads/posts.
    key(event.id, userId) { AnnouncementContent(event, userId, repository) }
}

@Composable
private fun AnnouncementContent(event: Event, userId: String, repository: AnnouncementRepository) {
    var rows by remember { mutableStateOf<List<Announcement>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }
    var draft by rememberSaveable { mutableStateOf("") }
    var requestId by rememberSaveable { mutableStateOf(UUID.randomUUID().toString()) }
    var posting by remember { mutableStateOf(false) }
    var postError by remember { mutableStateOf(false) }
    var posted by remember { mutableStateOf(false) }
    var now by remember(event) { mutableStateOf(Instant.now()) }
    val scope = rememberCoroutineScope()
    val canPost = event.canPostAnnouncement(userId, now)

    LaunchedEffect(event.active, event.effectiveEnd()) {
        now = Instant.now()
        event.effectiveEnd()?.let { end ->
            val wait = Duration.between(now, end).toMillis()
            if (wait > 0) {
                delay(wait)
                now = Instant.now()
            }
        }
    }
    LaunchedEffect(repository, reload) {
        loading = true
        loadError = false
        try {
            rows = announcementFeed(repository.announcements(event.id))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // A revoked membership must not leave previously loaded private text on screen.
            rows = emptyList()
            loadError = true
        } finally {
            loading = false
        }
    }

    fun post() {
        if (posting || loading || !event.canPostAnnouncement(userId, Instant.now()) || !validAnnouncement(draft)) return
        posting = true
        postError = false
        posted = false
        scope.launch {
            try {
                val saved = repository.post(event.id, draft, requestId)
                rows = announcementFeed(listOf(saved) + rows)
                draft = ""
                requestId = UUID.randomUUID().toString()
                posted = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                postError = true
            } finally {
                posting = false
            }
        }
    }

    SectionCard(title = "Announcements") {
        TextButton(
            { reload++ },
            Modifier.heightIn(min = 48.dp),
            enabled = !loading && !posting,
        ) { Text("Refresh announcements") }
        when {
            loading -> Text("Loading announcements…", style = MaterialTheme.typography.bodyMedium)
            loadError -> {
                Text(
                    "Couldn't load announcements. Check your connection and event access, then retry.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
                TextButton({ reload++ }, Modifier.heightIn(min = 48.dp)) { Text("Retry announcements") }
            }
            rows.isEmpty() -> Text(
                if (event.hostId == userId && canPost) "No announcements yet. Share an update with people who joined."
                else "No announcements yet. Updates from the host will appear here.",
                style = MaterialTheme.typography.bodyMedium,
            )
            else -> rows.forEachIndexed { index, row ->
                if (index > 0) HorizontalDivider()
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Host · ${formatAnnouncementTime(row.createdAt)}", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(row.body, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        if (canPost) {
            OutlinedTextField(
                value = draft,
                onValueChange = {
                    draft = it
                    requestId = UUID.randomUUID().toString()
                    postError = false
                    posted = false
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Announcement") },
                supportingText = { Text("${announcementLength(draft.trim())} / $MAX_ANNOUNCEMENT_LENGTH characters") },
                isError = announcementLength(draft.trim()) > MAX_ANNOUNCEMENT_LENGTH,
                enabled = !posting,
                minLines = 3,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
            Button(
                ::post,
                Modifier.fillMaxWidth().heightIn(min = 48.dp),
                enabled = !posting && !loading && validAnnouncement(draft),
            ) { Text(if (posting) "Posting announcement…" else "Post announcement") }
        } else if (event.hostId == userId) {
            Text("Announcement history stays available after the event ends.", style = MaterialTheme.typography.bodySmall)
        }
        if (postError) {
            Text(
                "Couldn't post the announcement. Your draft is saved. Check your connection and that the event is still active, then try again.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        if (posted) Text("Announcement posted.", style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        if (rows.size == 50) Text("Showing the latest 50 announcements.", style = MaterialTheme.typography.bodySmall)
    }
}
