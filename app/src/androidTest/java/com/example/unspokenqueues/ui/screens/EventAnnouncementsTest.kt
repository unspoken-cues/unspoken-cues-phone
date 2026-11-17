package com.example.unspokenqueues.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.unspokenqueues.data.AnnouncementRepository
import com.example.unspokenqueues.data.EventRepository
import com.example.unspokenqueues.model.Announcement
import com.example.unspokenqueues.model.Attendee
import com.example.unspokenqueues.model.CueStatus
import com.example.unspokenqueues.model.Event
import com.example.unspokenqueues.model.Rsvp
import com.example.unspokenqueues.ui.components.EventAnnouncements
import com.example.unspokenqueues.ui.theme.UnspokenQueuesTheme
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant

private class FakeAnnouncements : AnnouncementRepository() {
    var feed = emptyList<Announcement>()
    var failLoads = 0
    var failPosts = 0
    var loadGate: CompletableDeferred<Unit>? = null
    var postGate: CompletableDeferred<Unit>? = null
    var loads = 0
    val posts = mutableListOf<Triple<String, String, String>>()
    override suspend fun announcements(eventId: String): List<Announcement> {
        loads++
        loadGate?.await()
        if (failLoads-- > 0) error("Do not show raw backend details")
        return feed
    }
    override suspend fun post(eventId: String, body: String, requestId: String): Announcement {
        posts += Triple(eventId, body.trim(), requestId)
        postGate?.await()
        if (failPosts-- > 0) error("Do not show raw backend details")
        return Announcement("persisted-id", eventId, body.trim(), Instant.parse("2026-11-17T18:00:00Z"))
    }
}

@RunWith(AndroidJUnit4::class)
class EventAnnouncementsTest {
    @get:Rule val compose = createComposeRule()
    private val event = Event("e1", "host", "Design Mixer", "", "MIXER2", true)
    private fun show(repo: FakeAnnouncements, user: String = "host", shownEvent: Event = event) {
        compose.setContent {
            UnspokenQueuesTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    EventAnnouncements(shownEvent, user, repo)
                }
            }
        }
    }

    @Test fun emptyFeedAndInvalidDraftDoNotPost() {
        val repo = FakeAnnouncements()
        show(repo)
        compose.onNodeWithText("No announcements yet.", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Post announcement").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Announcement", substring = false).performTextInput(" \t\n\u00a0")
        compose.onNodeWithText("Post announcement").assertIsNotEnabled()
        assertTrue(repo.posts.isEmpty())
    }

    @Test fun overLimitShowsCounterAndDisablesPost() {
        val repo = FakeAnnouncements()
        show(repo)
        compose.onNodeWithText("Announcement", substring = false).performTextInput("a".repeat(1001))
        compose.onNodeWithText("1001 / 1000 characters").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Post announcement").performScrollTo().assertIsNotEnabled()
        assertTrue(repo.posts.isEmpty())
    }

    @Test fun successfulPostUsesPersistedRowAndClearsDraft() {
        val repo = FakeAnnouncements()
        show(repo)
        compose.onNodeWithText("Announcement", substring = false).performTextInput("  Meet upstairs  ")
        compose.onNodeWithText("Post announcement").performScrollTo().performClick()
        compose.onNodeWithText("Announcement posted.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Meet upstairs").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("0 / 1000 characters").performScrollTo().assertIsDisplayed()
        assertEquals("Meet upstairs", repo.posts.single().second)
    }

    @Test fun failedPostPreservesDraftAndRetryReusesRequestId() {
        val repo = FakeAnnouncements().apply { failPosts = 1 }
        show(repo)
        compose.onNodeWithText("Announcement", substring = false).performTextInput("Meet upstairs")
        compose.onNodeWithText("Post announcement").performScrollTo().performClick()
        compose.onNodeWithText("Couldn't post the announcement.", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Announcement posted.").assertDoesNotExist()
        compose.onNodeWithText("Meet upstairs").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Post announcement").performScrollTo().performClick()
        compose.onNodeWithText("Announcement posted.").performScrollTo().assertIsDisplayed()
        assertEquals(2, repo.posts.size)
        assertEquals(repo.posts[0].third, repo.posts[1].third)
    }

    @Test fun editedFailedDraftGetsNewRequestId() {
        val repo = FakeAnnouncements().apply { failPosts = 1 }
        show(repo)
        compose.onNodeWithText("Announcement", substring = false).performTextInput("Meet upstairs")
        compose.onNodeWithText("Post announcement").performScrollTo().performClick()
        compose.onNodeWithText("Announcement", substring = false).performScrollTo().performTextInput(" at seven")
        compose.onNodeWithText("Post announcement").performScrollTo().performClick()
        compose.waitForIdle()
        assertNotEquals(repo.posts[0].third, repo.posts[1].third)
    }

    @Test fun pendingPostDisablesComposerAndRefreshWithoutFalseSuccess() {
        val gate = CompletableDeferred<Unit>()
        val repo = FakeAnnouncements().apply { postGate = gate }
        show(repo)
        compose.onNodeWithText("Announcement", substring = false).performTextInput("Meet upstairs")
        compose.onNodeWithText("Post announcement").performScrollTo().performClick()
        compose.onNodeWithText("Posting announcement…").assertIsNotEnabled()
        compose.onNodeWithText("Announcement", substring = false).assertIsNotEnabled()
        compose.onNodeWithText("Refresh announcements").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Announcement posted.").assertDoesNotExist()
        assertEquals(1, repo.posts.size)
        compose.runOnIdle { gate.complete(Unit) }
        compose.onNodeWithText("Announcement posted.").performScrollTo().assertIsDisplayed()
    }

    @Test fun loadShowsProgressThenRetryableError() {
        val gate = CompletableDeferred<Unit>()
        val repo = FakeAnnouncements().apply { loadGate = gate; failLoads = 1 }
        show(repo)
        compose.onNodeWithText("Loading announcements…").assertIsDisplayed()
        compose.onNodeWithText("Refresh announcements").assertIsNotEnabled()
        compose.runOnIdle { gate.complete(Unit) }
        compose.onNodeWithText("Couldn't load announcements.", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Retry announcements").performClick()
        compose.onNodeWithText("No announcements yet.", substring = true).assertIsDisplayed()
        assertEquals(2, repo.loads)
    }

    @Test fun memberReadsHostUpdatesWithoutComposer_andCapturesPreview() {
        val repo = FakeAnnouncements().apply {
            feed = listOf(Announcement("a", "e1", "We’ve moved upstairs. Take the lift beside the entrance; the host will meet you there.", Instant.parse("2026-11-17T18:00:00Z")))
        }
        show(repo, user = "member")
        compose.onNodeWithText("We’ve moved upstairs.", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Host ·", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Post announcement").assertDoesNotExist()
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        File(instrumentation.targetContext.cacheDir, "announcement-populated.png").outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }

    @Test fun endedAndPastHostsRetainHistoryWithoutComposer() {
        show(FakeAnnouncements(), shownEvent = event.copy(endsAt = Instant.EPOCH))
        compose.onNodeWithText("Announcement history stays available", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Post announcement").assertDoesNotExist()
        compose.onNodeWithText("Refresh announcements").assertIsEnabled()
    }

    @Test fun changingAccountAndEventClearsDraftAndFeed() {
        val shown = mutableStateOf(event)
        val user = mutableStateOf("host")
        val repo = FakeAnnouncements()
        compose.setContent {
            UnspokenQueuesTheme { EventAnnouncements(shown.value, user.value, repo) }
        }
        compose.onNodeWithText("Announcement", substring = false).performTextInput("Private draft")
        compose.runOnIdle { shown.value = event.copy(id = "e2"); user.value = "other" }
        compose.onNodeWithText("Private draft").assertDoesNotExist()
        compose.onNodeWithText("Post announcement").assertDoesNotExist()
        compose.runOnIdle { shown.value = event; user.value = "host" }
        compose.onNodeWithText("Private draft").assertDoesNotExist()
    }

    @Test fun publicOutsiderCannotSeeSection() {
        val announcements = FakeAnnouncements()
        val events = object : EventRepository() {
            override suspend fun eventAttendees(eventId: String): List<Attendee> = emptyList()
            override suspend fun myRsvps(userId: String): Map<String, Rsvp> = emptyMap()
            override suspend fun event(eventId: String): Event = event
        }
        compose.setContent {
            UnspokenQueuesTheme {
                EventDetailScreen(events, "outsider", CueStatus.GREEN, event.copy(isPublic = true), {}, {}, {}, announcements)
            }
        }
        compose.onNodeWithText("Announcements").assertDoesNotExist()
        assertEquals(0, announcements.loads)
    }
}
