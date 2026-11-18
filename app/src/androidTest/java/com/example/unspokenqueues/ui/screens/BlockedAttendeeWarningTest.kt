package com.example.unspokenqueues.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
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
import com.example.unspokenqueues.model.ReactionState
import com.example.unspokenqueues.model.Rsvp
import com.example.unspokenqueues.ui.theme.UnspokenQueuesTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private val picnic = Event("e2", "someone", "Team Picnic", "", "PCNC34", true)

/** Answers from memory with [blocked] as the people the viewer blocked; null makes the check fail. */
private class FakeJoinRepository(
    private val blocked: List<String>?,
    private val rsvps: Map<String, Rsvp> = emptyMap(),
    private val discover: List<Event> = emptyList(),
) : EventRepository() {
    val checks = mutableListOf<String>()
    val answers = mutableListOf<Rsvp>()

    private fun check(key: String): List<String> {
        checks += key
        return blocked ?: error("Offline")
    }

    override suspend fun blockedAttendees(eventId: String) = check(eventId)
    override suspend fun blockedAttendeesByCode(code: String) = check(code)
    override suspend fun rsvp(eventId: String, userId: String, answer: Rsvp) {
        answers += answer
    }
    override suspend fun joinByCode(code: String) = picnic
    override suspend fun myRsvps(userId: String) = rsvps
    override suspend fun myEvents(userId: String) = emptyList<Event>()
    override suspend fun discoverEvents() = discover
    override suspend fun event(eventId: String): Event? = null
    override suspend fun eventAttendees(eventId: String) = emptyList<Attendee>()
    override suspend fun myEventProfile(eventId: String) = EventProfile()
}

@RunWith(AndroidJUnit4::class)
class BlockedAttendeeWarningTest {

    @get:Rule
    val compose = createComposeRule()

    private val opened = mutableListOf<Event>()

    private fun showDetail(repo: EventRepository) {
        compose.setContent {
            UnspokenQueuesTheme {
                EventDetailScreen(
                    repo, SafetyRepository(), "newcomer", CueStatus.GREEN, picnic, {}, {}, {},
                    announcementRepo = object : AnnouncementRepository() {
                        override suspend fun announcements(eventId: String) = emptyList<Announcement>()
                    },
                    reactionRepository = object : ReactionRepository() {
                        override suspend fun state(eventId: String) = ReactionState(false, false, emptyList(), emptyList())
                    },
                )
            }
        }
    }

    private fun showList(repo: EventRepository) {
        compose.setContent {
            UnspokenQueuesTheme {
                EventsScreen(repo, "newcomer", onHost = {}, onOpen = { opened += it })
            }
        }
    }

    private fun waitFor(text: String) =
        compose.waitUntil(timeoutMillis = 5_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    @Test
    fun firstRsvp_warnsByName_andCancelDoesNotRsvp() {
        val repo = FakeJoinRepository(listOf("Jordan"))
        showDetail(repo)

        compose.onNodeWithText("Going").performScrollTo().performClick()
        waitFor("Jordan, who you blocked, is going to this event.")
        compose.onNodeWithText("Someone you blocked is going").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Join anyway").assertDoesNotExist()
        assertEquals(emptyList<Rsvp>(), repo.answers)
    }

    @Test
    fun firstRsvp_joinAnyway_rsvpsAsChosen() {
        val repo = FakeJoinRepository(listOf("Jordan", "Sam"))
        showDetail(repo)

        compose.onNodeWithText("Maybe").performScrollTo().performClick()
        waitFor("Jordan and Sam, who you blocked, are going to this event.")
        compose.onNodeWithText("Join anyway").performClick()
        compose.waitForIdle()

        assertEquals(listOf(Rsvp.MAYBE), repo.answers)
        assertEquals(listOf(picnic.id), repo.checks)
    }

    @Test
    fun nobodyBlocked_rsvpsWithoutAWarning() {
        val repo = FakeJoinRepository(emptyList())
        showDetail(repo)

        compose.onNodeWithText("Going").performScrollTo().performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Join anyway").assertDoesNotExist()
        assertEquals(listOf(Rsvp.GOING), repo.answers)
    }

    @Test
    fun checkFails_rsvpGoesAhead() {
        val repo = FakeJoinRepository(blocked = null)
        showDetail(repo)

        compose.onNodeWithText("Going").performScrollTo().performClick()
        compose.waitForIdle()

        assertEquals(listOf(Rsvp.GOING), repo.answers)
    }

    @Test
    fun memberChangingRsvp_isNotChecked() {
        val repo = FakeJoinRepository(listOf("Jordan"), rsvps = mapOf(picnic.id to Rsvp.GOING))
        showDetail(repo)
        waitFor("You're going. The host can see you on the list.")

        compose.onNodeWithText("Maybe").performScrollTo().performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Join anyway").assertDoesNotExist()
        assertEquals(listOf(Rsvp.MAYBE), repo.answers)
        assertEquals(emptyList<String>(), repo.checks)
    }

    @Test
    fun discoverRsvp_warns_thenJoinAnywayRsvps() {
        val repo = FakeJoinRepository(listOf("Jordan"), discover = listOf(picnic))
        showList(repo)

        compose.onNodeWithText("Discover").performClick()
        waitFor("RSVP")
        compose.onNodeWithText("RSVP").performClick()
        waitFor("Jordan, who you blocked, is going to this event.")
        compose.onNodeWithText("Join anyway").performClick()
        compose.waitForIdle()

        assertEquals(listOf(Rsvp.GOING), repo.answers)
    }

    @Test
    fun joinByCode_warns_cancelStays_joinAnywayJoins() {
        val repo = FakeJoinRepository(listOf("Jordan"))
        showList(repo)

        compose.onNodeWithText("Join with code").performClick()
        compose.onNodeWithText("Event code").performTextInput("pcnc34")
        compose.onNodeWithText("Join").performClick()
        waitFor("Jordan, who you blocked, is going to this event.")
        compose.onNodeWithText("Cancel").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Join an event").assertIsDisplayed()
        assertEquals(emptyList<Event>(), opened)

        compose.onNodeWithText("Join").performClick()
        waitFor("Join anyway")
        compose.onNodeWithText("Join anyway").performClick()
        compose.waitForIdle()

        assertEquals(listOf(picnic), opened)
        assertEquals(listOf("PCNC34", "PCNC34"), repo.checks)
    }
}
