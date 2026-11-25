package com.example.unspokenqueues.ui.screens

import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.unspokenqueues.data.chat.ChatAccess
import com.example.unspokenqueues.data.chat.ChatMessage
import com.example.unspokenqueues.data.chat.ChatThread
import com.example.unspokenqueues.ui.theme.UnspokenQueuesTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

internal class TestInbox(initial: ChatThread? = null) : ChatAccess {
    var thread = initial
    var failReceipt = false
    var hideFirstReceipt = false
    val attempts = mutableListOf<String>()
    val history = mutableListOf<ChatMessage>()
    override suspend fun eligiblePeers(eventId: String) = setOf("blair")
    override suspend fun threads(eventId: String) = listOfNotNull(thread)
    override suspend fun request(eventId: String, peerId: String) {
        thread = ChatThread("conversation", peerId, "Blair", "pending", incoming = false, peerKey = null)
    }
    override suspend fun respond(threadId: String, accept: Boolean) {
        thread = thread!!.copy(state = if (accept) "accepted" else "declined", peerKey = "key")
    }
    var revokeDuringOlderRead = false
    val cursors = mutableListOf<String>()
    override suspend fun messages(eventId: String, thread: ChatThread, before: ChatMessage?): List<ChatMessage> {
        if (before == null) {
            return if (hideFirstReceipt && attempts.size == 1) emptyList() else history.takeLast(100)
        }
        cursors += before.id
        val older = history.takeWhile { it.id != before.id }.takeLast(100)
        if (revokeDuringOlderRead) {
            history.clear()
            this.thread = null
        }
        return older
    }
    override suspend fun send(eventId: String, thread: ChatThread, messageId: String, text: String) {
        attempts += messageId
        if (history.none { it.id == messageId }) history += ChatMessage(messageId, true, text, "")
        if (failReceipt) {
            failReceipt = false
            error("Delivery receipt lost")
        }
    }
    override suspend fun block(peerId: String) { thread = null }
    override fun myFingerprint() = "own-code"
    override fun peerFingerprint(thread: ChatThread) = "peer-code"
}

@RunWith(AndroidJUnit4::class)
class EventChatTest {
    @get:Rule val ui = createComposeRule()
    private val pending = ChatThread("conversation", "blair", "Blair", "pending", incoming = true, peerKey = "key")

    private fun show(access: TestInbox, fromDirectory: Boolean = false) {
        ui.setContent {
            UnspokenQueuesTheme {
                EventChatScreen("event", "Community Meetup", access, {},
                    if (fromDirectory) "blair" else null, if (fromDirectory) "Blair" else null)
            }
        }
    }

    @Test fun requestRequiresTapAndSenderWaitsForAcceptance() {
        val access = TestInbox()
        show(access, true)
        ui.onNodeWithText("Request chat").performClick()
        ui.onNodeWithText("Request sent").assertIsDisplayed()
        ui.onNodeWithText("Accept").assertDoesNotExist()
        ui.onNodeWithText("Send").assertDoesNotExist()
        ui.onNodeWithContentDescription("Back to inbox").performClick()
        ui.onNodeWithText("Blair").assertIsDisplayed()
        assertEquals("pending", access.thread?.state)
    }

    @Test fun incomingRequestCanBeDeclinedFromInbox() {
        val access = TestInbox(pending)
        show(access)
        ui.onNodeWithText("Blair").performClick()
        ui.onNodeWithText("Decline").performClick()
        ui.onNodeWithText("Request declined").assertIsDisplayed()
        ui.onNodeWithText("Send").assertDoesNotExist()
    }

    @Test fun acceptRetryWithSameIdVerifyAndBlock() {
        val access = TestInbox(pending).apply { failReceipt = true; hideFirstReceipt = true }
        show(access)
        ui.onNodeWithText("Blair").performClick()
        ui.onNodeWithText("Accept").performClick()
        ui.onNodeWithText("Send").assertIsNotEnabled()
        ui.onNodeWithText("Message").performTextInput("Meet by the stairs")
        ui.onNodeWithText("Send").performClick()
        ui.onNodeWithText("Couldn’t send.", substring = true).assertIsDisplayed()
        ui.onNodeWithText("Send").assertIsEnabled().performSemanticsAction(SemanticsActions.OnClick)
        ui.waitUntil(5_000) { ui.onAllNodesWithText("You").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithText("Meet by the stairs").assertIsDisplayed()
        assertEquals(2, access.attempts.size)
        assertEquals(1, access.attempts.toSet().size)
        ui.onNodeWithContentDescription("Conversation options").performClick()
        ui.onNodeWithText("Verify device keys").performClick()
        ui.waitUntil(5_000) { ui.onAllNodesWithText("peer-code").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithText("own-code").assertIsDisplayed()
        ui.onNodeWithText("Done").performClick()
        ui.onNodeWithContentDescription("Conversation options").performClick()
        ui.onNodeWithText("Block attendee").performClick()
        ui.onNodeWithText("Block").performClick()
        ui.onNodeWithText("Your inbox is empty.", substring = true).assertIsDisplayed()
        ui.onNodeWithText("Meet by the stairs").assertDoesNotExist()
    }

    @Test fun pollingConfirmsAnAmbiguousDeliveryWithoutResending() {
        val access = TestInbox(pending.copy(state = "accepted")).apply { failReceipt = true }
        show(access, true)
        ui.onNodeWithText("Message").performTextInput("Found the table")
        ui.onNodeWithText("Send").assertIsEnabled().performSemanticsAction(SemanticsActions.OnClick)
        ui.waitUntil(8_000) { ui.onAllNodesWithText("You").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithText("Found the table").assertIsDisplayed()
        ui.onNodeWithText("Couldn’t send.", substring = true).assertDoesNotExist()
        ui.onNodeWithText("Send").assertIsNotEnabled()
        ui.runOnIdle { assertEquals(1, access.attempts.size) }
    }

    @Test fun readsOlderMessagesOnDemandAndHidesUnverifiedText() {
        val access = TestInbox(pending.copy(state = "accepted")).apply {
            repeat(102) { n -> history += ChatMessage(n.toString(), false, "Message $n", n.toString(), readable = n != 101) }
        }
        show(access, true)
        ui.onNodeWithText("Message could not be verified").assertIsDisplayed()
        ui.onNodeWithText("Message 101").assertDoesNotExist()
        val list = ui.onNode(hasScrollToIndexAction())
        list.performScrollToIndex(100)
        ui.onNodeWithText("Load earlier messages").performClick()
        ui.runOnIdle { assertEquals(listOf("2"), access.cursors) }
        list.performScrollToIndex(101)
        ui.onNodeWithText("Message 0").assertIsDisplayed()
        ui.onNodeWithText("Load earlier messages").assertDoesNotExist()
    }

    @Test fun revokedAccessDuringOlderReadDiscardsAllCachedText() {
        val access = TestInbox(pending.copy(state = "accepted")).apply {
            repeat(101) { n -> history += ChatMessage(n.toString(), false, "Private $n", "") }
            revokeDuringOlderRead = true
        }
        show(access, true)
        ui.onNodeWithText("Private 100").assertIsDisplayed()
        ui.onNode(hasScrollToIndexAction()).performScrollToIndex(100)
        ui.onNodeWithText("Load earlier messages").performClick()
        ui.runOnIdle { assertEquals(listOf("1"), access.cursors) }
        ui.onNodeWithText("Private ", substring = true).assertDoesNotExist()
        ui.onNodeWithText("Load earlier messages").assertDoesNotExist()
    }

    @Test fun switchAccountsHidesPreviousHistoryAndDraft() {
        val current = mutableStateOf<ChatAccess>(TestInbox(pending.copy(state = "accepted")).apply {
            history += ChatMessage("message", false, "First account only", "")
        })
        ui.setContent {
            UnspokenQueuesTheme {
                key(current.value) { EventChatScreen("event", "Community Meetup", current.value, {}, "blair", "Blair") }
            }
        }
        ui.onNodeWithText("First account only").assertIsDisplayed()
        ui.onNodeWithText("Message").performTextInput("Private draft")
        ui.runOnIdle { current.value = TestInbox() }
        ui.onNodeWithText("Request chat").assertIsDisplayed()
        ui.onNodeWithText("First account only").assertDoesNotExist()
        ui.onNodeWithText("Private draft").assertDoesNotExist()
    }
}
