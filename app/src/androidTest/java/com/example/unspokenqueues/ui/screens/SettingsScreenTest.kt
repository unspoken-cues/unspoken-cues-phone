package com.example.unspokenqueues.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.unspokenqueues.data.AuthRepository
import com.example.unspokenqueues.data.SafetyRepository
import com.example.unspokenqueues.model.BlockedUser
import com.example.unspokenqueues.model.ThemeMode
import com.example.unspokenqueues.model.WatchConnection
import com.example.unspokenqueues.ui.theme.UnspokenQueuesTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** A [SafetyRepository] that answers from memory, so these tests never reach the server. */
private class FakeSafetyRepository(blocked: List<BlockedUser> = emptyList()) : SafetyRepository() {
    val blocked = blocked.toMutableList()
    val unblocked = mutableListOf<String>()

    override suspend fun blockedUsers(): List<BlockedUser> = blocked.toList()

    override suspend fun unblock(otherUserId: String) {
        unblocked += otherUserId
        blocked.removeAll { it.userId == otherUserId }
    }
}

/** An [AuthRepository] whose account delete only counts calls, failing when told to. */
private class FakeAuthRepository(private val fail: Boolean = false) : AuthRepository() {
    var deletes = 0

    override suspend fun deleteAccount() {
        deletes++
        if (fail) error("offline")
    }
}

@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val clicks = mutableListOf<String>()

    private fun showSettings(
        safety: SafetyRepository = FakeSafetyRepository(),
        account: AuthRepository = FakeAuthRepository(),
    ) {
        compose.setContent {
            UnspokenQueuesTheme {
                SettingsScreen(
                    safety = safety,
                    account = account,
                    watch = WatchConnection.CONNECTED,
                    themeMode = ThemeMode.SYSTEM,
                    onThemeModeChange = { clicks += "theme" },
                    onReconnect = { clicks += "reconnect" },
                    onSignOut = { clicks += "signOut" },
                    onAccountDeleted = { clicks += "deleted" },
                    onBack = { clicks += "back" },
                )
            }
        }
    }

    private fun replay() = compose.onNodeWithText("Replay tutorial").performScrollTo().performClick()

    @Test
    fun replayTutorial_showsTheCarousel() {
        showSettings()

        replay()

        compose.onNodeWithContentDescription("Page 1 of 5").assertIsDisplayed()
        compose.onNodeWithText("Cue").assertIsDisplayed()
        compose.onNodeWithText("Settings").assertDoesNotExist()
        compose.onNodeWithText("Sign out").assertDoesNotExist()
    }

    @Test
    fun finishingTheReplay_returnsToSettings() {
        showSettings()
        replay()

        repeat(4) { compose.onNodeWithText("Next").performClick() }
        compose.onNodeWithText("Done").performClick()

        compose.onNodeWithText("Settings").assertIsDisplayed()
        compose.onNodeWithText("Replay tutorial").assertExists()
        compose.onNodeWithContentDescription("Page 5 of 5").assertDoesNotExist()
        // Leaving the tour is not leaving Settings, and it signs nobody out.
        assertEquals(emptyList<String>(), clicks)
    }

    @Test
    fun skippingTheReplay_returnsToSettings() {
        showSettings()
        replay()

        compose.onNodeWithText("Skip").performClick()

        compose.onNodeWithText("Settings").assertIsDisplayed()
        assertEquals(emptyList<String>(), clicks)
    }

    @Test
    fun systemBack_onTheFirstCard_returnsToSettings() {
        showSettings()
        replay()

        Espresso.pressBack()

        compose.onNodeWithText("Settings").assertIsDisplayed()
        assertEquals(emptyList<String>(), clicks)
    }

    @Test
    fun theReplay_canBeRunAgain() {
        showSettings()
        replay()
        compose.onNodeWithText("Skip").performClick()

        replay()

        compose.onNodeWithContentDescription("Page 1 of 5").assertIsDisplayed()
    }

    // ---------- Blocked people ----------

    @Test
    fun withNobodyBlocked_saysSo() {
        showSettings()

        compose.onNodeWithText("You haven't blocked anyone.").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun unblock_removesThePerson() {
        val safety = FakeSafetyRepository(listOf(BlockedUser("u2", "Sam")))
        showSettings(safety)

        compose.onNodeWithText("Sam").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Unblock").performClick()

        compose.onNodeWithText("Sam").assertDoesNotExist()
        compose.onNodeWithText("You haven't blocked anyone.").assertExists()
        assertEquals(listOf("u2"), safety.unblocked)
    }

    // ---------- Delete account ----------

    private fun askToDelete() = compose.onNodeWithText("Delete account").performScrollTo().performClick()

    @Test
    fun deleteAccount_asksFirst_andCancelDeletesNothing() {
        val account = FakeAuthRepository()
        showSettings(account = account)

        askToDelete()
        compose.onNodeWithText("Delete your account?").assertIsDisplayed()
        compose.onNodeWithText("can't be undone", substring = true).assertIsDisplayed()
        assertEquals(0, account.deletes)

        compose.onNodeWithText("Cancel").performClick()

        compose.onNodeWithText("Delete your account?").assertDoesNotExist()
        assertEquals(0, account.deletes)
        assertEquals(emptyList<String>(), clicks)
    }

    @Test
    fun deleteAccount_confirmed_deletesAndReportsIt() {
        val account = FakeAuthRepository()
        showSettings(account = account)

        askToDelete()
        compose.onNodeWithText("Delete").performClick()

        compose.waitUntil { "deleted" in clicks }
        assertEquals(1, account.deletes)
        assertEquals(listOf("deleted"), clicks)
    }

    @Test
    fun deleteAccount_failing_saysSo_andStaysSignedIn() {
        val account = FakeAuthRepository(fail = true)
        showSettings(account = account)

        askToDelete()
        compose.onNodeWithText("Delete").performClick()

        // The dialog stays open with the error, so the user can retry or cancel.
        compose.onNodeWithText("Couldn't delete your account", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Delete your account?").assertIsDisplayed()
        assertEquals(1, account.deletes)
        assertEquals(emptyList<String>(), clicks)
    }
}
