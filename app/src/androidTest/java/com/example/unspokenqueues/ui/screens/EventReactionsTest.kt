package com.example.unspokenqueues.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.unspokenqueues.data.ReactionRepository
import com.example.unspokenqueues.model.CueStatus
import com.example.unspokenqueues.model.Event
import com.example.unspokenqueues.model.Reaction
import com.example.unspokenqueues.model.ReactionState
import com.example.unspokenqueues.model.WaveRecipient
import com.example.unspokenqueues.ui.components.EventReactionsSection
import com.example.unspokenqueues.ui.theme.UnspokenQueuesTheme
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant

private class FakeReactionRepository : ReactionRepository() {
    var snapshot = ReactionState(true, true, listOf(WaveRecipient("host", "Harper Host", false)), emptyList())
    var failLoad = false
    var failSetting = false
    var failSend = false
    var loadGate: CompletableDeferred<Unit>? = null
    var sendGate: CompletableDeferred<Unit>? = null
    var settingGate: CompletableDeferred<Unit>? = null
    val sends = mutableListOf<Pair<String, String>>()
    val settings = mutableListOf<Boolean>()
    var loads = 0
    override suspend fun state(eventId: String): ReactionState {
        loads++
        loadGate?.await()
        if (failLoad) error("Offline")
        return snapshot
    }
    override suspend fun setReceiveWaves(eventId: String, receiveWaves: Boolean): ReactionState {
        settings += receiveWaves
        settingGate?.await()
        if (failSetting) error("Denied")
        snapshot = snapshot.copy(receiveWaves = receiveWaves, canWave = receiveWaves)
        return snapshot
    }
    override suspend fun send(eventId: String, recipientId: String): Instant {
        sends += eventId to recipientId
        sendGate?.await()
        if (failSend) error("Denied")
        snapshot = snapshot.copy(recipients = snapshot.recipients.map { it.copy(sent = it.userId == recipientId || it.sent) })
        return Instant.parse("2026-11-17T20:00:00Z")
    }
}

@RunWith(AndroidJUnit4::class)
class EventReactionsTest {
    @get:Rule val compose = createComposeRule()
    private val event = Event("event", "host", "Mixer", "", "WAVE12", true)
    private val wave = Reaction("friend", "Jamie", Instant.parse("2026-11-17T20:00:00Z"))

    private fun show(repo: ReactionRepository, status: CueStatus = CueStatus.GREEN, event: Event = this.event, user: String = "member") {
        compose.setContent {
            UnspokenQueuesTheme {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    EventReactionsSection(repo, event, user, status, isProfilePublic = true)
                }
            }
        }
    }

    @Test fun loadingDoesNotExposeTargets_thenEmptyInboxIsExplicit() {
        val repo = FakeReactionRepository().apply { loadGate = CompletableDeferred() }
        show(repo)
        compose.onNodeWithText("Loading waves…").assertIsDisplayed()
        compose.onNodeWithText("Harper Host").assertDoesNotExist()
        compose.onNodeWithText("Refresh waves").assertIsNotEnabled()
        compose.runOnIdle { repo.loadGate!!.complete(Unit) }
        compose.onNodeWithText("No waves for you yet.").performScrollTo().assertIsDisplayed()
        assertEquals(emptyList<Pair<String, String>>(), repo.sends)
        assertTrue(repo.settings.isEmpty())
    }

    @Test fun emptyAvailableParticipantsAreExplicit_andSelfNeverGetsAButton() {
        val repo = FakeReactionRepository().apply {
            snapshot = snapshot.copy(recipients = listOf(WaveRecipient("member", "Yourself", false)))
        }
        show(repo)
        compose.onNodeWithText("No one is available for a wave right now.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Wave to Yourself").assertDoesNotExist()
        compose.onNodeWithText("No waves for you yet.").performScrollTo().assertIsDisplayed()
        assertTrue(repo.sends.isEmpty())
    }

    @Test fun failedLoadRefreshes_andOptedOutDoesNotExposeHistory() {
        val repo = FakeReactionRepository().apply { failLoad = true }
        show(repo)
        compose.onNodeWithText("Couldn't load waves", substring = true).assertIsDisplayed()
        compose.runOnIdle {
            repo.failLoad = false
            repo.snapshot = repo.snapshot.copy(receiveWaves = false, canWave = false, recipients = emptyList(), inbox = emptyList())
        }
        compose.onNodeWithText("Refresh waves").performClick()
        compose.onNodeWithText("Waves are off for this event.").assertIsDisplayed()
        compose.onNodeWithText("Harper Host").assertDoesNotExist()
    }

    @Test fun sendWaitsForAcknowledgement_andDisablesDuplicateTaps() {
        val repo = FakeReactionRepository().apply { sendGate = CompletableDeferred() }
        show(repo)
        compose.onNodeWithContentDescription("Wave to Harper Host").performScrollTo().performClick()
        compose.onNodeWithText("Sending…").assertIsNotEnabled()
        compose.onNodeWithText("Sent").assertDoesNotExist()
        compose.onNodeWithContentDescription("Receive waves").assertIsNotEnabled()
        assertEquals(listOf("event" to "host"), repo.sends)
        compose.runOnIdle { repo.sendGate!!.complete(Unit) }
        compose.onNodeWithText("Sent").assertIsNotEnabled()
        assertEquals(1, repo.sends.size)
    }

    @Test fun failedSendRefreshesPrivacy_andRetryOnlyClaimsAcknowledgedSuccess() {
        val repo = FakeReactionRepository().apply { failSend = true }
        show(repo)
        compose.onNodeWithContentDescription("Wave to Harper Host").performScrollTo().performClick()
        compose.onNodeWithText("Couldn't send the wave", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Sent").assertDoesNotExist()
        assertEquals(2, repo.loads)
        compose.runOnIdle { repo.failSend = false }
        compose.onNodeWithText("Retry wave").performScrollTo().performClick()
        compose.onNodeWithText("Sent").performScrollTo().assertIsNotEnabled()
        assertEquals(2, repo.sends.size)
    }

    @Test fun deniedSendHidesNewlyPrivateSenderAndInbox() {
        val repo = FakeReactionRepository().apply { snapshot = snapshot.copy(inbox = listOf(wave)); failSend = true }
        show(repo)
        compose.runOnIdle { repo.snapshot = repo.snapshot.copy(canWave = false, recipients = emptyList(), inbox = emptyList()) }
        compose.onNodeWithContentDescription("Wave to Harper Host").performScrollTo().performClick()
        compose.onNodeWithText("Harper Host").assertDoesNotExist()
        compose.onNodeWithText("Jamie waved").assertDoesNotExist()
        compose.onNodeWithText("Retry wave").assertDoesNotExist()
    }

    @Test fun optOutHidesInboxWhilePending_thenFailedSettingCanBeRetried() {
        val repo = FakeReactionRepository().apply {
            snapshot = snapshot.copy(inbox = listOf(wave)); failSetting = true; settingGate = CompletableDeferred()
        }
        show(repo)
        compose.onNodeWithContentDescription("Receive waves").performScrollTo().performClick()
        compose.onNodeWithText("Jamie waved").assertDoesNotExist()
        compose.onNodeWithText("Saving Receive waves…").assertIsDisplayed()
        compose.onNodeWithText("Refresh waves").assertIsNotEnabled()
        compose.runOnIdle { repo.settingGate!!.complete(Unit) }
        compose.onNodeWithText("Your setting is unconfirmed", substring = true).assertIsDisplayed()
        compose.runOnIdle { repo.failSetting = false }
        compose.onNodeWithText("Retry setting").performClick()
        compose.onNodeWithText("Waves are off for this event.").assertIsDisplayed()
        assertEquals(listOf(false, false), repo.settings)
    }

    @Test fun optingInPersists_thenAllowsWaves() {
        val repo = FakeReactionRepository().apply { snapshot = snapshot.copy(receiveWaves = false, canWave = false) }
        show(repo)
        compose.onNodeWithContentDescription("Receive waves").performClick()
        compose.onNodeWithContentDescription("Wave to Harper Host").performScrollTo().assertIsDisplayed()
        assertEquals(listOf(true), repo.settings)
    }

    @Test fun localRedAndPurpleOverrideAStaleGreenServerSnapshot() {
        val repo = FakeReactionRepository().apply { snapshot = snapshot.copy(inbox = listOf(wave)) }
        var status by mutableStateOf(CueStatus.RED)
        compose.setContent { UnspokenQueuesTheme { EventReactionsSection(repo, event, "member", status, isProfilePublic = true) } }
        compose.onNodeWithText("Harper Host").assertDoesNotExist()
        compose.onNodeWithText("Jamie waved").assertDoesNotExist()
        compose.runOnIdle { status = CueStatus.PURPLE }
        compose.onNodeWithText("Harper Host").assertDoesNotExist()
        compose.onNodeWithText("Jamie waved").assertDoesNotExist()
        assertTrue(repo.sends.isEmpty())
    }

    @Test fun localPrivateProfileOverridesAStalePublicServerSnapshot() {
        val repo = FakeReactionRepository().apply { snapshot = snapshot.copy(inbox = listOf(wave)) }
        compose.setContent { UnspokenQueuesTheme { EventReactionsSection(repo, event, "member", CueStatus.GREEN, isProfilePublic = false) } }
        compose.onNodeWithText("Harper Host").assertDoesNotExist()
        compose.onNodeWithText("Jamie waved").assertDoesNotExist()
        assertTrue(repo.sends.isEmpty())
    }

    @Test fun endBoundaryHidesActionsAndInboxWithoutRefresh() {
        val repo = FakeReactionRepository().apply { snapshot = snapshot.copy(inbox = listOf(wave)) }
        show(repo, event = event.copy(endsAt = Instant.now().plusSeconds(4)))
        compose.onNodeWithText("Harper Host").performScrollTo().assertIsDisplayed()
        compose.waitUntil(timeoutMillis = 8_000) {
            compose.onAllNodes(androidx.compose.ui.test.hasText("Harper Host")).fetchSemanticsNodes().isEmpty()
        }
        compose.onNodeWithText("Jamie waved").assertDoesNotExist()
        assertTrue(repo.sends.isEmpty())
    }

    @Test fun changingAccountCancelsOldLoadAndDiscardsPrivateResults() {
        val repo = FakeReactionRepository().apply { loadGate = CompletableDeferred() }
        var user by mutableStateOf("member")
        compose.setContent { UnspokenQueuesTheme { EventReactionsSection(repo, event, user, CueStatus.GREEN, isProfilePublic = true) } }
        compose.runOnIdle { user = "" }
        compose.runOnIdle { repo.loadGate!!.complete(Unit) }
        compose.onNodeWithText("Sign in to use waves.").assertIsDisplayed()
        compose.onNodeWithText("Harper Host").assertDoesNotExist()
        assertEquals(1, repo.loads)
    }

    @Test fun populatedSectionScreenshot() {
        val repo = FakeReactionRepository().apply { snapshot = snapshot.copy(inbox = listOf(wave)) }
        show(repo)
        compose.onNodeWithText("Harper Host").assertIsDisplayed()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.cacheDir, "quick-reactions-populated.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
