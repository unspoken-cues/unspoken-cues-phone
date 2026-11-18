package com.example.unspokenqueues.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.unspokenqueues.data.AnnouncementRepository
import com.example.unspokenqueues.data.EventRepository
import com.example.unspokenqueues.data.ReactionRepository
import com.example.unspokenqueues.data.SafetyRepository
import com.example.unspokenqueues.model.Announcement
import com.example.unspokenqueues.model.Attendee
import com.example.unspokenqueues.model.CueStatus
import com.example.unspokenqueues.model.Event
import com.example.unspokenqueues.model.EventProfile
import com.example.unspokenqueues.model.Profile
import com.example.unspokenqueues.model.ReactionState
import com.example.unspokenqueues.model.Rsvp
import com.example.unspokenqueues.model.WaveRecipient
import com.example.unspokenqueues.ui.theme.UnspokenQueuesTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** An [EventRepository] holding one event profile in memory, for a member who joined "e1". */
private class FakeEventProfileRepository(var stored: EventProfile = EventProfile()) : EventRepository() {
    val saves = mutableListOf<EventProfile>()
    var failSave = false

    override suspend fun myEventProfile(eventId: String) = stored

    override suspend fun saveEventProfile(eventId: String, profile: EventProfile): EventProfile {
        if (failSave) error("Offline")
        saves += profile
        stored = profile
        return profile
    }

    override suspend fun myRsvps(userId: String) = mapOf("e1" to Rsvp.GOING)

    override suspend fun event(eventId: String): Event? = null

    // Like the server, the list always holds the viewer's own (cued-in) row; waves wait for it.
    override suspend fun eventAttendees(eventId: String) =
        listOf(Attendee("member", Profile("Alex Rivera", "", emptyList(), emptyList(), true), CueStatus.GREEN))
}

@RunWith(AndroidJUnit4::class)
class EventProfileTest {
    @get:Rule val compose = createComposeRule()

    private val event = Event("e1", "host", "Resort Week", "", "RSRT22", true)
    private val main = Profile("Alex Rivera", "Designer.", emptyList(), emptyList(), true)

    // Waves the server says are available, so only local cue eligibility decides what shows.
    private val openWaves = object : ReactionRepository() {
        override suspend fun state(eventId: String) =
            ReactionState(true, true, listOf(WaveRecipient("friend", "Jordan", false)), emptyList())
    }

    private fun show(repo: EventRepository, status: CueStatus = CueStatus.GREEN) {
        compose.setContent {
            UnspokenQueuesTheme {
                EventDetailScreen(
                    repo, SafetyRepository(), "member", status, event, {}, {}, {},
                    announcementRepo = object : AnnouncementRepository() {
                        override suspend fun announcements(eventId: String) = emptyList<Announcement>()
                    },
                    reactionRepository = openWaves,
                    profileVisibleForWaves = true,
                    mainProfile = main,
                )
            }
        }
    }

    private fun waitForText(text: String) =
        compose.waitUntil(5_000) { compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }

    private fun openEditor() {
        waitForText("Edit profile for this event")
        compose.onNodeWithText("Edit profile for this event").performScrollTo().performClick()
    }

    @Test
    fun noOverride_saysMainProfile_andEditorShowsTheFallbacks() {
        show(FakeEventProfileRepository())
        waitForText("You appear with your main profile")
        openEditor()

        compose.onNodeWithText("Blank uses Alex Rivera").assertIsDisplayed()
        compose.onNodeWithText("blank uses your main bio", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Main profile (Green)").assertIsDisplayed()
    }

    @Test
    fun savingAnOverride_sendsTrimmedFields_andUpdatesTheSummary() {
        val repo = FakeEventProfileRepository()
        show(repo)
        openEditor()

        compose.onNodeWithText("Name at this event").performTextInput("  Sam at the pool ")
        compose.onNodeWithText("Yellow · Proceed with care").performScrollTo().performClick()
        compose.onNodeWithText("Save").performClick()

        waitForText("Here you appear as Sam at the pool with a Yellow cue")
        assertEquals(listOf(EventProfile("Sam at the pool", null, CueStatus.YELLOW)), repo.saves)
        compose.onNodeWithText("Name at this event").assertDoesNotExist()
    }

    @Test
    fun useMainProfile_clearsTheOverride() {
        val repo = FakeEventProfileRepository(EventProfile("Sam", "At the pool", CueStatus.RED))
        show(repo)
        waitForText("Here you appear as Sam with a Red cue")
        openEditor()

        compose.onNodeWithText("Use main profile").performScrollTo().performClick()

        waitForText("You appear with your main profile")
        assertEquals(listOf(EventProfile()), repo.saves)
    }

    @Test
    fun failedSave_keepsTheEditorOpenWithAnError() {
        val repo = FakeEventProfileRepository().apply { failSave = true }
        show(repo)
        openEditor()

        compose.onNodeWithText("Save").performClick()

        waitForText("Couldn't save your event profile")
        compose.onNodeWithText("Name at this event").assertIsDisplayed()
    }

    @Test
    fun waves_useTheEventCue_notTheGlobalOne() {
        // Red at home, Green at this event: waves are available here.
        show(FakeEventProfileRepository(EventProfile(status = CueStatus.GREEN)), status = CueStatus.RED)
        compose.waitUntil(5_000) {
            compose.onAllNodesWithContentDescription("Wave to Jordan").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("Wave to Jordan").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun waves_respectAnEventPurpleCue_overAGlobalGreen() {
        show(FakeEventProfileRepository(EventProfile(status = CueStatus.PURPLE)), status = CueStatus.GREEN)
        waitForText("Waves need an active event")
        compose.onNodeWithContentDescription("Wave to Jordan").assertDoesNotExist()
    }
}
