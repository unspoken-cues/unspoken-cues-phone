package com.example.unspokenqueues.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.unspokenqueues.model.ThemeMode
import com.example.unspokenqueues.model.WatchConnection
import com.example.unspokenqueues.ui.theme.UnspokenQueuesTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val clicks = mutableListOf<String>()

    private fun showSettings() {
        compose.setContent {
            UnspokenQueuesTheme {
                SettingsScreen(
                    watch = WatchConnection.CONNECTED,
                    themeMode = ThemeMode.SYSTEM,
                    onThemeModeChange = { clicks += "theme" },
                    onReconnect = { clicks += "reconnect" },
                    onSignOut = { clicks += "signOut" },
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
}
