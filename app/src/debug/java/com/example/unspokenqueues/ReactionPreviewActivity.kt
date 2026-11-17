package com.example.unspokenqueues

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.unspokenqueues.data.AnnouncementRepository
import com.example.unspokenqueues.model.Announcement
import com.example.unspokenqueues.data.EventRepository
import com.example.unspokenqueues.debug.PreviewSafetyRepository
import com.example.unspokenqueues.data.ReactionRepository
import com.example.unspokenqueues.model.Attendee
import com.example.unspokenqueues.model.CueStatus
import com.example.unspokenqueues.model.Event
import com.example.unspokenqueues.model.Profile
import com.example.unspokenqueues.model.Reaction
import com.example.unspokenqueues.model.ReactionState
import com.example.unspokenqueues.model.Rsvp
import com.example.unspokenqueues.model.WaveRecipient
import com.example.unspokenqueues.ui.screens.EventDetailScreen
import com.example.unspokenqueues.ui.theme.UnspokenQueuesTheme
import kotlinx.coroutines.delay
import java.time.Instant

/** Debug-only, in-memory preview. No Supabase client or account is created. */
class ReactionPreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            UnspokenQueuesTheme {
                val now = remember { Instant.now() }
                var event by remember {
                    mutableStateOf(Event("sample-event", "host", "Friday Mixer", "A relaxed evening of games and conversation.", "SAMPLE", true,
                        startsAt = now.minusSeconds(1800), endsAt = now.plusSeconds(7200), location = "Community lounge", hostName = "Harper", goingCount = 3))
                }
                val safety = remember { PreviewSafetyRepository { "me" } }
                val events = remember {
                    object : EventRepository() {
                        override suspend fun eventAttendees(eventId: String) = listOf(
                            Attendee("me", Profile("You", "", emptyList(), emptyList(), true), CueStatus.GREEN),
                            Attendee("jordan", Profile("Jordan", "", emptyList(), emptyList(), true), CueStatus.YELLOW),
                        ).filter { safety.visible(it.userId) }
                        override suspend fun myRsvps(userId: String) = mapOf("sample-event" to Rsvp.GOING)
                        override suspend fun event(eventId: String): Event? = null
                        override suspend fun rsvp(eventId: String, userId: String, answer: Rsvp) = Unit
                        override suspend fun leaveEvent(eventId: String, userId: String) = Unit
                    }
                }
                val announcements = remember {
                    object : AnnouncementRepository() {
                        override suspend fun announcements(eventId: String) = listOf(Announcement("sample-update", eventId, "Welcome! Quiet seating is available near the entrance.", now.minusSeconds(600)))
                    }
                }
                val reactions = remember {
                    object : ReactionRepository() {
                        var opted = false
                        val sentTo = mutableSetOf<String>()
                        fun sample() = safety.visibleWaves(ReactionState(opted, opted,
                            if (opted) listOf(WaveRecipient("jordan", "Jordan", "jordan" in sentTo), WaveRecipient("host", "Harper · Host", "host" in sentTo)) else emptyList(),
                            if (opted) listOf(Reaction("jordan", "Jordan", now.minusSeconds(300))) else emptyList()))
                        override suspend fun state(eventId: String): ReactionState { delay(250); return sample() }
                        override suspend fun setReceiveWaves(eventId: String, receiveWaves: Boolean): ReactionState {
                            delay(500); opted = receiveWaves; return sample()
                        }
                        override suspend fun send(eventId: String, recipientId: String): Instant {
                            delay(600); sentTo += recipientId; return Instant.now()
                        }
                    }
                }
                Scaffold { padding ->
                    Column(Modifier.fillMaxSize().padding(padding)) {
                        Text("Sample preview · Waves use local sample data", Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Box(Modifier.weight(1f)) {
                            EventDetailScreen(events, safety, "me", CueStatus.GREEN, event,
                                onEventChange = { event = it }, onRemoved = {}, onBack = { finish() },
                                announcementRepo = announcements, reactionRepository = reactions, profileVisibleForWaves = true)
                        }
                    }
                }
            }
        }
    }
}
