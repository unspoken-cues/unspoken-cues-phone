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
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.unspokenqueues.data.EventRepository
import com.example.unspokenqueues.model.Attendee
import com.example.unspokenqueues.model.CueStatus
import com.example.unspokenqueues.model.Event
import com.example.unspokenqueues.model.Profile
import com.example.unspokenqueues.ui.theme.UnspokenQueuesTheme
import org.junit.Assert.assertEquals
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

    override suspend fun createEvent(hostId: String, name: String, details: String): Event =
        Event("new", hostId, name.trim(), details.trim(), "NEW234", true).also { created += it }

    override suspend fun myEvents(userId: String): List<Event> = events

    override suspend fun eventAttendees(eventId: String): List<Attendee> = attendees

    override suspend fun joinByCode(code: String): Event =
        events.firstOrNull { it.active && it.joinCode.equals(code.trim(), ignoreCase = true) }
            ?: error("No active event with that code")

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

    private fun showList(repo: EventRepository, userId: String = "host") {
        compose.setContent {
            UnspokenQueuesTheme {
                EventsScreen(repo, userId, onHost = { clicks += "host" }, onOpen = { opened += it })
            }
        }
    }

    private fun showDetail(repo: EventRepository, userId: String, event: Event = mixer) {
        compose.setContent {
            UnspokenQueuesTheme {
                CompositionLocalProvider(LocalClipboard provides clipboard) {
                    EventDetailScreen(
                        repo, userId, CueStatus.GREEN, event,
                        onEventChange = { opened += it },
                        onRemoved = { clicks += "removed" },
                        onBack = { clicks += "back" },
                    )
                }
            }
        }
    }

    // ---------- My events ----------

    @Test
    fun list_showsHostAndJoinButtons() {
        showList(FakeEventRepository())

        compose.onNodeWithText("Host event").assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithText("Join event").assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithText("No events yet", substring = true).assertIsDisplayed()
    }

    @Test
    fun list_showsMyEvents_markedByMyPartInThem() {
        showList(FakeEventRepository(listOf(mixer, picnic, oldParty)))

        compose.onNodeWithText("Design Mixer").assertIsDisplayed()
        compose.onNodeWithText("Friday 7 PM").assertIsDisplayed()
        compose.onNodeWithText("Hosting").assertIsDisplayed()
        compose.onNodeWithText("Joined").assertIsDisplayed()
        compose.onNodeWithText("Ended").assertIsDisplayed()
        compose.onNodeWithText("No events yet", substring = true).assertDoesNotExist()
    }

    @Test
    fun hostEvent_andTappingAnEvent_areHandedToTheCaller() {
        showList(FakeEventRepository(listOf(mixer, picnic)))

        compose.onNodeWithText("Host event").performClick()
        compose.onNodeWithText("Team Picnic").performClick()

        assertEquals(listOf("host"), clicks)
        assertEquals(listOf(picnic), opened)
    }

    // ---------- Join ----------

    @Test
    fun typedCode_joinsTheEvent_andOpensIt() {
        showList(FakeEventRepository(listOf(picnic)), userId = "newcomer")

        compose.onNodeWithText("Join event").performClick()
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

        compose.onNodeWithText("Join event").performClick()
        compose.onNodeWithText("Event code").performTextInput("NOPE22")
        compose.onNodeWithText("Join").performClick()

        compose.onNodeWithText("Couldn't join the event", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Join an event").assertIsDisplayed()
        assertEquals(emptyList<Event>(), opened)
    }

    @Test
    fun endedEvent_cannotBeJoined() {
        showList(FakeEventRepository(listOf(oldParty)), userId = "newcomer")

        compose.onNodeWithText("Join event").performClick()
        compose.onNodeWithText("Event code").performTextInput("PARTY5")
        compose.onNodeWithText("Join").performClick()

        compose.onNodeWithText("Couldn't join the event", substring = true).assertIsDisplayed()
        assertEquals(emptyList<Event>(), opened)
    }

    // ---------- Event detail ----------

    @Test
    fun detail_forAnAttendee_hidesRedAndPurple() {
        showDetail(FakeEventRepository(attendees = everyone), userId = "green")

        compose.onNodeWithText("Design Mixer").assertIsDisplayed()
        compose.onNodeWithText("Gia Green").assertIsDisplayed()
        compose.onNodeWithText("Yan Yellow").assertIsDisplayed()
        compose.onNodeWithText("Rae Red").assertDoesNotExist()
        compose.onNodeWithText("Pat Purple").assertDoesNotExist()
        compose.onNodeWithText("Attendees · 2").assertIsDisplayed()
    }

    @Test
    fun detail_forAnAttendeeWhoIsRed_stillShowsThemselves() {
        showDetail(FakeEventRepository(attendees = everyone), userId = "red")

        compose.onNodeWithText("Rae Red").assertIsDisplayed()
        compose.onNodeWithText("Pat Purple").assertDoesNotExist()
        compose.onNodeWithText("Attendees · 3").assertIsDisplayed()
    }

    @Test
    fun detail_forTheHost_showsEveryone() {
        showDetail(FakeEventRepository(attendees = everyone), userId = "host")

        compose.onNodeWithText("Attendees · 4").assertIsDisplayed()
        listOf("Gia Green", "Yan Yellow", "Rae Red", "Pat Purple").forEach {
            compose.onNodeWithText(it).performScrollTo().assertIsDisplayed()
        }
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
                CreateEventScreen(repo, "host", onCreated = { opened += it }, onCancel = { clicks += "cancel" })
            }
        }

        compose.onNodeWithText("Create").assertIsNotEnabled()
        compose.onNodeWithText("Event name").performTextInput("Board Games")
        compose.onNodeWithText("Details").performTextInput("Sunday 2 PM")
        compose.onNodeWithText("Create").assertIsEnabled().performClick()
        compose.waitForIdle()

        assertEquals(listOf(Event("new", "host", "Board Games", "Sunday 2 PM", "NEW234", true)), repo.created)
        assertEquals(repo.created, opened)
    }
}
