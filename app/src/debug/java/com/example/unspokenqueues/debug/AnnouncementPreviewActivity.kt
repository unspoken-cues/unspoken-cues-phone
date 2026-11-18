package com.example.unspokenqueues.debug

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.unspokenqueues.data.ReactionRepository
import com.example.unspokenqueues.model.ReactionState
import com.example.unspokenqueues.model.Reaction
import com.example.unspokenqueues.model.WaveRecipient
import com.example.unspokenqueues.data.AnnouncementRepository
import com.example.unspokenqueues.data.EventRepository
import com.example.unspokenqueues.model.Announcement
import com.example.unspokenqueues.model.Attendee
import com.example.unspokenqueues.model.CueStatus
import com.example.unspokenqueues.model.Event
import com.example.unspokenqueues.model.EventProfile
import com.example.unspokenqueues.model.Profile
import com.example.unspokenqueues.model.Rsvp
import com.example.unspokenqueues.model.announcementFeed
import com.example.unspokenqueues.model.validAnnouncement
import com.example.unspokenqueues.ui.screens.EventDetailScreen
import com.example.unspokenqueues.ui.theme.UnspokenQueuesTheme
import java.time.Instant
import java.util.UUID

/** Local interactive sample for reviewing the feature before a database deployment. */
class AnnouncementPreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            UnspokenQueuesTheme {
                var userId by remember { mutableStateOf("sample-host") }
                val safety = remember { PreviewSafetyRepository { userId } }
                val sample = remember { AnnouncementSample(safety) }
                var removed by remember { mutableStateOf(false) }
                val waves = remember(userId) {
                    object : ReactionRepository() {
                        var opted = false
                        val sent = mutableSetOf<String>()
                        fun snapshot() = safety.visibleWaves(ReactionState(opted, opted,
                            if (opted) listOf(WaveRecipient("sample-friend", "Jordan", "sample-friend" in sent)) else emptyList(),
                            if (opted) listOf(Reaction("sample-friend", "Jordan", Instant.now().minusSeconds(300))) else emptyList()))
                        override suspend fun state(eventId: String) = snapshot()
                        override suspend fun setReceiveWaves(eventId: String, receiveWaves: Boolean): ReactionState { opted = receiveWaves; return snapshot() }
                        override suspend fun send(eventId: String, recipientId: String): Instant { sent += recipientId; return Instant.now() }
                    }
                }
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Column(Modifier.padding(top = 30.dp, bottom = 20.dp)) {
                        Text("Sample event · changes stay in this preview", Modifier.padding(horizontal = 20.dp),
                            style = MaterialTheme.typography.labelMedium)
                        Row(Modifier.padding(horizontal = 12.dp)) {
                            TextButton({ userId = "sample-host" }) { Text(if (userId == "sample-host") "Host view ✓" else "View as host") }
                            TextButton({ userId = "sample-member" }) { Text(if (userId == "sample-member") "Member view ✓" else "View as member") }
                        }
                        if (removed) {
                            Text("Sample event removed. Reopen this preview to reset it.", Modifier.padding(20.dp))
                            Button({ finish() }, Modifier.padding(20.dp)) { Text("Close preview") }
                        } else {
                            Column(Modifier.weight(1f)) {
                                EventDetailScreen(
                                    sample.events, safety, userId, CueStatus.GREEN, sample.event,
                                    onEventChange = { sample.event = it },
                                    onRemoved = { removed = true },
                                    onBack = { finish() },
                                    announcementRepo = sample.announcements,
                                    reactionRepository = waves, profileVisibleForWaves = true,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private class AnnouncementSample(private val safety: PreviewSafetyRepository) {
    private val now = Instant.now()
    var event by mutableStateOf(Event(
        "sample-event", "sample-host", "Friday Night Mixer", "A relaxed evening to meet new people. Check Announcements below for the latest from your host.",
        "DEMO42", true, startsAt = now.minusSeconds(1800), endsAt = now.plusSeconds(7200),
        location = "The Common Room", hostName = "Alex", goingCount = 2,
    ))
    private var answer = Rsvp.GOING
    private val rows = mutableListOf(
        Announcement("sample-newer", event.id, "We’ve moved upstairs. Take the lift beside the entrance; I’ll meet you there.", now.minusSeconds(300)),
        Announcement("sample-older", event.id, "Welcome! Water and quiet seating are available near the back of the room.", now.minusSeconds(1200)),
    )
    private val requests = mutableMapOf<String, Announcement>()
    val events = object : EventRepository() {
        override suspend fun eventAttendees(eventId: String): List<Attendee> = listOf(
            Attendee("sample-member", Profile("Sam", "", emptyList(), emptyList(), true), CueStatus.GREEN),
            Attendee("sample-friend", Profile("Jordan", "", emptyList(), emptyList(), true), CueStatus.YELLOW),
        ).filter { safety.visible(it.userId) }
        override suspend fun myRsvps(userId: String): Map<String, Rsvp> =
            if (userId == "sample-member") mapOf(event.id to answer) else emptyMap()
        override suspend fun event(eventId: String): Event = this@AnnouncementSample.event
        override suspend fun rsvp(eventId: String, userId: String, answer: Rsvp) { this@AnnouncementSample.answer = answer }
        override suspend fun blockedAttendees(eventId: String) = emptyList<String>()
        override suspend fun endEvent(eventId: String) { event = event.copy(active = false) }
        override suspend fun deleteEvent(eventId: String) { rows.clear() }
        override suspend fun leaveEvent(eventId: String, userId: String) = Unit
        override suspend fun setCuedIn(eventId: String, cuedIn: Boolean) = Unit
        override suspend fun myEventProfile(eventId: String) = EventProfile()
        override suspend fun saveEventProfile(eventId: String, profile: EventProfile) = profile
    }
    val announcements = object : AnnouncementRepository() {
        override suspend fun announcements(eventId: String): List<Announcement> = announcementFeed(rows)
        override suspend fun post(eventId: String, body: String, requestId: String): Announcement {
            requests[requestId]?.let { return it }
            check(event.active && Instant.now().isBefore(event.endsAt))
            require(validAnnouncement(body))
            return Announcement(UUID.randomUUID().toString(), eventId, body.trim(), Instant.now()).also {
                rows += it
                requests[requestId] = it
            }
        }
    }
}
