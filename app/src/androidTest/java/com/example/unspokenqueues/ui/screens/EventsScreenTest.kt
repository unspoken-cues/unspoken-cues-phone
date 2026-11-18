package com.example.unspokenqueues.ui.screens

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.NativeClipboard
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.unspokenqueues.data.EventCoverRepository
import com.example.unspokenqueues.data.EventDraft
import com.example.unspokenqueues.data.EventRepository
import com.example.unspokenqueues.data.ReactionRepository
import com.example.unspokenqueues.model.Reaction
import com.example.unspokenqueues.model.ReactionState
import com.example.unspokenqueues.model.WaveRecipient
import com.example.unspokenqueues.data.SafetyRepository
import com.example.unspokenqueues.data.AnnouncementRepository
import com.example.unspokenqueues.model.Announcement
import com.example.unspokenqueues.model.Attendee
import com.example.unspokenqueues.model.CueStatus
import com.example.unspokenqueues.model.Event
import com.example.unspokenqueues.model.EventProfile
import com.example.unspokenqueues.model.Profile
import com.example.unspokenqueues.model.Rsvp
import com.example.unspokenqueues.ui.theme.UnspokenQueuesTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** An [EventRepository] that answers from memory, so these tests never reach the server. */
private class FakeEventRepository(
    private val events: List<Event> = emptyList(),
    private val attendees: List<Attendee> = emptyList(),
    private val failRemoval: Boolean = false,
) : EventRepository() {
    val created = mutableListOf<Event>()
    val ended = mutableListOf<String>()
    val left = mutableListOf<Pair<String, String>>()
    val deleted = mutableListOf<String>()
    val cued = mutableListOf<Pair<String, Boolean>>()

    override suspend fun setCuedIn(eventId: String, cuedIn: Boolean) {
        cued += eventId to cuedIn
    }

    override suspend fun createEvent(hostId: String, draft: EventDraft): Event =
        Event(
            "new", hostId, draft.name.trim(), draft.details.trim(), "NEW234", true,
            startsAt = draft.startsAt, endsAt = draft.endsAt, hostName = draft.hostName,
        ).also { created += it }

    override suspend fun myEvents(userId: String): List<Event> = events

    // Everyone in these tests who does not host an event answers "going", as joining by code does.
    override suspend fun myRsvps(userId: String): Map<String, Rsvp> =
        events.filter { it.hostId != userId }.associate { it.id to Rsvp.GOING }

    override suspend fun discoverEvents(): List<Event> = emptyList()

    override suspend fun event(eventId: String): Event? = events.firstOrNull { it.id == eventId }

    override suspend fun eventAttendees(eventId: String): List<Attendee> = attendees

    // Never reach the server for the viewer's event profile.
    override suspend fun myEventProfile(eventId: String) = EventProfile()

    override suspend fun joinByCode(code: String): Event =
        events.firstOrNull { it.active && it.joinCode.equals(code.trim(), ignoreCase = true) }
            ?: error("No active event with that code")

    // Nobody here is blocked; BlockedAttendeeWarningTest covers the warning.
    override suspend fun blockedAttendees(eventId: String) = emptyList<String>()

    override suspend fun blockedAttendeesByCode(code: String) = emptyList<String>()

    override suspend fun endEvent(eventId: String) {
        ended += eventId
    }

    override suspend fun leaveEvent(eventId: String, userId: String) {
        if (failRemoval) error("Nothing was removed")
        left += eventId to userId
    }

    override suspend fun deleteEvent(eventId: String) {
        if (failRemoval) error("Nothing was removed")
        deleted += eventId
    }
}

/**
 * Stands in for the device clipboard. An emulator shares its real clipboard with the computer it
 * runs on, so copying for real would overwrite whatever the developer had copied.
 */
private class FakeClipboard : Clipboard {
    var entry: ClipEntry? = null

    override suspend fun getClipEntry(): ClipEntry? = entry

    override suspend fun setClipEntry(clipEntry: ClipEntry?) {
        entry = clipEntry
    }

    override val nativeClipboard: NativeClipboard get() = error("The fake has no system clipboard")
}

@RunWith(AndroidJUnit4::class)
class EventsScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val mixer = Event("e1", "host", "Design Mixer", "Friday 7 PM", "MIXER2", true)
    private val picnic = Event("e2", "someone", "Team Picnic", "", "PCNC34", true)
    private val oldParty = Event("e3", "host", "Launch Party", "", "PARTY5", false)

    private fun attendee(id: String, name: String, status: CueStatus) =
        Attendee(id, Profile(name, "", emptyList(), emptyList(), true), status)

    private val everyone = listOf(
        attendee("green", "Gia Green", CueStatus.GREEN),
        attendee("yellow", "Yan Yellow", CueStatus.YELLOW),
        attendee("red", "Rae Red", CueStatus.RED),
        attendee("purple", "Pat Purple", CueStatus.PURPLE),
    )

    private val opened = mutableListOf<Event>()
    private val clicks = mutableListOf<String>()
    private val clipboard = FakeClipboard()

    // Find the FAB's clickable surface, rather than its independently animated text child.
    private fun hostEventButton() = compose.onNode(
        hasClickAction() and hasAnyDescendant(hasText("Host event")),
        useUnmergedTree = true,
    )

    private fun showList(repo: EventRepository, userId: String = "host") {
        compose.setContent {
            UnspokenQueuesTheme {
                EventsScreen(repo, userId, onHost = { clicks += "host" }, onOpen = { opened += it })
            }
        }
        compose.waitUntil(timeoutMillis = 5_000) { hostEventButton().isDisplayed() }
    }

    private fun showDetail(
        repo: EventRepository,
        userId: String,
        event: Event = mixer,
        safety: SafetyRepository = SafetyRepository(),
        waves: ReactionRepository = object : ReactionRepository() {
            override suspend fun state(eventId: String) = ReactionState(false, false, emptyList(), emptyList())
        },
    ) {
        compose.setContent {
            UnspokenQueuesTheme {
                CompositionLocalProvider(LocalClipboard provides clipboard) {
                    EventDetailScreen(
                        repo, safety, userId, CueStatus.GREEN, event,
                        onEventChange = { opened += it },
                        onRemoved = { clicks += "removed" },
                        onBack = { clicks += "back" },
                        profileVisibleForWaves = true,
                        reactionRepository = waves,
                        announcementRepo = object : AnnouncementRepository() {
                            override suspend fun announcements(eventId: String): List<Announcement> = emptyList()
                        },
                    )
                }
            }
        }
    }

    // ---------- My events ----------

    @Test
    fun list_showsHostAndJoinButtons() {
        showList(FakeEventRepository())

        hostEventButton().assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithText("Join with code").assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithText("Nothing coming up", substring = true).assertIsDisplayed()
    }

    @Test
    fun list_showsMyEvents_markedByMyPartInThem() {
        showList(FakeEventRepository(listOf(mixer, picnic, oldParty)))

        compose.onNodeWithText("Design Mixer").assertIsDisplayed()
        compose.onNodeWithText("Hosting").assertIsDisplayed()
        compose.onNodeWithText("Going").assertIsDisplayed()
        // The ended one has moved to the Past tab.
        compose.onNodeWithText("Launch Party").assertDoesNotExist()
        compose.onNodeWithText("Past").performClick()
        compose.onNodeWithText("Launch Party").assertIsDisplayed()
        compose.onNodeWithText("Nothing coming up", substring = true).assertDoesNotExist()
    }

    @Test
    fun hostEvent_andTappingAnEvent_areHandedToTheCaller() {
        showList(FakeEventRepository(listOf(mixer, picnic)))

        hostEventButton().performClick()
        compose.onNodeWithText("Team Picnic").performClick()

        assertEquals(listOf("host"), clicks)
        assertEquals(listOf(picnic), opened)
    }

    // ---------- Join ----------

    @Test
    fun typedCode_joinsTheEvent_andOpensIt() {
        showList(FakeEventRepository(listOf(picnic)), userId = "newcomer")

        compose.onNodeWithText("Join with code").performClick()
        compose.onNodeWithText("Scan QR code").assertIsDisplayed()
        compose.onNodeWithText("Join").assertIsNotEnabled()
        compose.onNodeWithText("Event code").performTextInput("pcnc34")
        compose.onNodeWithText("Join").assertIsEnabled().performClick()
        compose.waitForIdle()

        assertEquals(listOf(picnic), opened)
    }

    @Test
    fun codeNoEventHas_saysSo_andStaysOnTheDialog() {
        showList(FakeEventRepository(listOf(picnic)), userId = "newcomer")

        compose.onNodeWithText("Join with code").performClick()
        compose.onNodeWithText("Event code").performTextInput("NOPE22")
        compose.onNodeWithText("Join").performClick()

        compose.onNodeWithText("Couldn't join the event", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Join an event").assertIsDisplayed()
        assertEquals(emptyList<Event>(), opened)
    }

    @Test
    fun endedEvent_cannotBeJoined() {
        showList(FakeEventRepository(listOf(oldParty)), userId = "newcomer")

        compose.onNodeWithText("Join with code").performClick()
        compose.onNodeWithText("Event code").performTextInput("PARTY5")
        compose.onNodeWithText("Join").performClick()

        compose.onNodeWithText("Couldn't join the event", substring = true).assertIsDisplayed()
        assertEquals(emptyList<Event>(), opened)
    }

    // ---------- Event detail ----------

    @Test
    fun detail_forAnAttendee_showsWhoTheServerSends() {
        // event_attendees() leaves out red and purple attendees for anyone but the host.
        showDetail(FakeEventRepository(attendees = everyone.take(2)), userId = "green")

        compose.onNodeWithText("Design Mixer").assertIsDisplayed()
        compose.onNodeWithText("Gia Green").assertIsDisplayed()
        compose.onNodeWithText("Yan Yellow").assertIsDisplayed()
        compose.onNodeWithText("Attendees · 2").assertIsDisplayed()
    }

    @Test
    fun blockingAnAttendeeRemovesTheirWavesWithoutManualRefresh() {
        val person = everyone.first()
        var blockedId: String? = null
        val safety = object : SafetyRepository() {
            override suspend fun block(otherUserId: String) { blockedId = otherUserId }
        }
        val waves = object : ReactionRepository() {
            override suspend fun state(eventId: String): ReactionState {
                if (blockedId == person.userId) return ReactionState(true, true, emptyList(), emptyList())
                return ReactionState(
                    true, true,
                    listOf(WaveRecipient(person.userId, person.name, false)),
                    listOf(Reaction(person.userId, person.name, java.time.Instant.now())),
                )
            }
        }
        showDetail(FakeEventRepository(attendees = listOf(person)), "host", safety = safety, waves = waves)
        val target = compose.onNodeWithContentDescription("Wave to ${person.name}")
        val history = compose.onNodeWithText("${person.name} waved")
        target.performScrollTo().assertIsDisplayed()
        history.performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("More options for ${person.name}").performScrollTo().performClick()
        compose.onNodeWithText("Block").performClick()
        compose.onNodeWithText("Block").performClick()
        compose.runOnIdle { assertEquals(person.userId, blockedId) }
        target.assertDoesNotExist()
        history.assertDoesNotExist()
    }

    @Test
    fun detail_forTheHost_showsEveryone() {
        showDetail(FakeEventRepository(attendees = everyone), userId = "host")

        compose.onNodeWithText("Attendees · 4").assertIsDisplayed()
        listOf("Gia Green", "Yan Yellow", "Rae Red", "Pat Purple").forEach {
            compose.onNodeWithText(it).performScrollTo().assertIsDisplayed()
        }
    }

    // ---------- Cue In / Cue Out ----------

    @Test
    fun member_canCueOut_andBackIn_whichHidesAndRestoresWaves() {
        // The event makes "green" a member (myRsvps answers going), which is what shows waves.
        val repo = FakeEventRepository(events = listOf(mixer), attendees = everyone.take(2))
        showDetail(repo, userId = "green")
        val waves = compose.onNodeWithText("Refresh waves")
        waves.performScrollTo().assertIsDisplayed()

        compose.onNodeWithContentDescription("Cue Out: hide me from this event's attendee list")
            .performScrollTo().performClick()
        compose.waitForIdle()

        assertEquals(listOf("e1" to false), repo.cued)
        compose.onNodeWithText("You're cued out", substring = true).performScrollTo().assertIsDisplayed()
        // Their own row says so; only they and the host ever see it.
        compose.onNodeWithText("Cued out").performScrollTo().assertIsDisplayed()
        waves.assertDoesNotExist()

        compose.onNodeWithContentDescription("Cue In: show me on this event's attendee list")
            .performScrollTo().performClick()
        compose.waitForIdle()

        assertEquals(listOf("e1" to false, "e1" to true), repo.cued)
        compose.onNodeWithText("Cued out").assertDoesNotExist()
        compose.onNodeWithText("Refresh waves").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun host_seesCuedOutMembersMarked_andHasNoSwitch() {
        val cuedOut = everyone.first().copy(cuedIn = false)
        showDetail(FakeEventRepository(attendees = listOf(cuedOut) + everyone.drop(1)), userId = "host")

        compose.onNodeWithText("Gia Green").assertIsDisplayed()
        compose.onNodeWithText("Cued out").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Cue Out").assertDoesNotExist()
        compose.onNodeWithText("Cue In").assertDoesNotExist()
    }

    @Test
    fun hostControls_areOnlyForTheHost() {
        showDetail(FakeEventRepository(attendees = everyone), userId = "green")

        compose.onNodeWithText("Show join QR").assertDoesNotExist()
        compose.onNodeWithText("Copy code").assertDoesNotExist()
        compose.onNodeWithText("MIXER2").assertDoesNotExist()
        compose.onNodeWithText("End event").assertDoesNotExist()
    }

    @Test
    fun host_canCopyTheJoinCode() {
        showDetail(FakeEventRepository(attendees = everyone), userId = "host")

        compose.onNodeWithText("Copy code").performClick()

        compose.onNodeWithText("Copied ✓").assertIsDisplayed()
        assertEquals("MIXER2", clipboard.entry?.clipData?.getItemAt(0)?.text?.toString())
    }

    @Test
    fun host_canShowTheJoinQr() {
        showDetail(FakeEventRepository(attendees = everyone), userId = "host")

        compose.onNodeWithText("MIXER2").assertIsDisplayed()
        compose.onNodeWithText("Show join QR").performClick()

        compose.onNodeWithText("Tap anywhere to close").assertIsDisplayed()
    }

    @Test
    fun host_canEndTheEvent_afterConfirming() {
        val repo = FakeEventRepository(attendees = everyone)
        showDetail(repo, userId = "host")

        compose.onNodeWithText("End event").performScrollTo().performClick()
        compose.onNodeWithText("End Design Mixer?").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(emptyList<String>(), repo.ended)

        compose.onNodeWithText("End event").performScrollTo().performClick()
        // The screen's own "End event" button is still there underneath the dialog.
        compose.onNode(hasText("End event") and hasAnyAncestor(isDialog())).performClick()
        compose.waitForIdle()

        assertEquals(listOf("e1"), repo.ended)
        assertEquals(listOf(mixer.copy(active = false)), opened)
    }

    @Test
    fun endedEvent_offersNoHostControls() {
        showDetail(FakeEventRepository(attendees = everyone), userId = "host", event = oldParty)

        compose.onNodeWithText("This event has ended", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Show join QR").assertDoesNotExist()
        compose.onNodeWithText("End event").assertDoesNotExist()
    }

    // ---------- Removing an event ----------

    @Test
    fun attendee_canLeaveTheEvent_afterConfirming() {
        val repo = FakeEventRepository(attendees = everyone)
        showDetail(repo, userId = "green")

        compose.onNodeWithText("Delete event").assertDoesNotExist()
        compose.onNodeWithText("Leave event").performScrollTo().performClick()
        compose.onNodeWithText("Leave Design Mixer?").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(emptyList<Pair<String, String>>(), repo.left)

        compose.onNodeWithText("Leave event").performScrollTo().performClick()
        compose.onNode(hasText("Leave event") and hasAnyAncestor(isDialog())).performClick()
        compose.waitForIdle()

        assertEquals(listOf("e1" to "green"), repo.left)
        assertEquals(emptyList<String>(), repo.deleted)
        assertEquals(listOf("removed"), clicks)
    }

    @Test
    fun host_canDeleteTheEvent_afterConfirming() {
        val repo = FakeEventRepository(attendees = everyone)
        showDetail(repo, userId = "host")

        compose.onNodeWithText("Leave event").assertDoesNotExist()
        compose.onNodeWithText("Delete event").performScrollTo().performClick()
        compose.onNodeWithText("Delete Design Mixer?").assertIsDisplayed()
        compose.onNode(hasText("Delete event") and hasAnyAncestor(isDialog())).performClick()
        compose.waitForIdle()

        assertEquals(listOf("e1"), repo.deleted)
        assertEquals(emptyList<Pair<String, String>>(), repo.left)
        assertEquals(listOf("removed"), clicks)
    }

    @Test
    fun endedEvent_canStillBeDeletedByItsHost() {
        val repo = FakeEventRepository(attendees = everyone)
        showDetail(repo, userId = "host", event = oldParty)

        compose.onNodeWithText("Delete event").performScrollTo().performClick()
        compose.onNode(hasText("Delete event") and hasAnyAncestor(isDialog())).performClick()
        compose.waitForIdle()

        assertEquals(listOf("e3"), repo.deleted)
    }

    @Test
    fun whenRemovingFails_itSaysSo_andStaysOnTheEvent() {
        showDetail(FakeEventRepository(attendees = everyone, failRemoval = true), userId = "green")

        compose.onNodeWithText("Leave event").performScrollTo().performClick()
        compose.onNode(hasText("Leave event") and hasAnyAncestor(isDialog())).performClick()

        compose.onNodeWithText("Couldn't leave the event", substring = true).performScrollTo().assertIsDisplayed()
        assertEquals(emptyList<String>(), clicks)
    }

    // ---------- Host an event ----------

    @Test
    fun create_needsAName_thenHandsOverTheNewEvent() {
        val repo = FakeEventRepository()
        compose.setContent {
            UnspokenQueuesTheme {
                CreateEventScreen(
                    repo, EventCoverRepository(), "host", "Hosty McHost",
                    onCreated = { opened += it }, onCancel = { clicks += "cancel" },
                )
            }
        }

        compose.onNodeWithText("Create").assertIsNotEnabled()
        compose.onNodeWithText("Event name").performTextInput("Board Games")
        compose.onNodeWithText("About").performTextInput("Sunday 2 PM")
        compose.onNodeWithText("Create").assertIsEnabled().performClick()
        compose.waitForIdle()

        assertEquals(listOf("Board Games"), repo.created.map { it.name })
        assertEquals(listOf("Sunday 2 PM"), repo.created.map { it.details })
        assertEquals(listOf("Hosty McHost"), repo.created.map { it.hostName })
        // A new event always has a start and an end, and the end is after the start.
        assertTrue(repo.created.single().let { it.startsAt != null && it.endsAt != null && it.endsAt!! > it.startsAt!! })
        assertEquals(repo.created, opened)
    }
}
