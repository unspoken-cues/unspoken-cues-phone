package com.example.unspokenqueues.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.unspokenqueues.model.CollectedCard
import com.example.unspokenqueues.model.CueStatus
import com.example.unspokenqueues.model.Profile
import com.example.unspokenqueues.model.SwapResult
import com.example.unspokenqueues.model.cardLink
import com.example.unspokenqueues.ui.theme.UnspokenQueuesTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QrScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val profile = Profile("Alex Rivera", "", emptyList(), emptyList(), true)
    private val link = cardLink("0123456789abcdef0123456789abcdef")
    private val collected = mutableListOf<String>()

    private fun showScreen(cardLink: String?, failed: Boolean = false) {
        compose.setContent {
            UnspokenQueuesTheme {
                QrScreen(CueStatus.GREEN, profile, cardLink, failed) { collected += it }
            }
        }
    }

    // ---------- My Card ----------

    @Test
    fun withACardLink_theCodeCanBeEnlarged_andShared() {
        showScreen(link)

        compose.onNodeWithText("Tap to enlarge").assertIsDisplayed()
        compose.onNodeWithText("Share").assertIsEnabled().performClick()
        compose.onNodeWithText(link).assertIsDisplayed()
        compose.onNodeWithText("Copy link").assertIsDisplayed()
    }

    @Test
    fun whileTheCodeIsLoading_sharingIsOff_butScanningStillWorks() {
        showScreen(cardLink = null)

        compose.onNodeWithText("Share").assertIsNotEnabled()
        compose.onNodeWithText("Tap to enlarge").assertDoesNotExist()
        compose.onNodeWithText("Scan a code").assertIsEnabled()
        compose.onNodeWithText("Enter a link instead").assertIsEnabled()
        compose.onNodeWithText("Couldn't load your code", substring = true).assertDoesNotExist()
    }

    @Test
    fun whenTheCodeFailsToLoad_itSaysSo() {
        showScreen(cardLink = null, failed = true)

        compose.onNodeWithText("Couldn't load your code", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Share").assertIsNotEnabled()
    }

    // ---------- Enter a link ----------

    @Test
    fun pastedLink_isHandedOverToBeCollected() {
        showScreen(link)
        val message = "Swap cards with me: $link"

        compose.onNodeWithText("Enter a link instead").performClick()
        compose.onNodeWithText("Swap cards").assertIsNotEnabled()
        compose.onNodeWithText("Card link").performTextInput(message)
        compose.onNodeWithText("Swap cards").assertIsEnabled().performClick()

        assertEquals(listOf(message), collected)
        compose.onNodeWithText("Enter a card link").assertDoesNotExist()
    }

    @Test
    fun textWithoutACardLink_cannotBeSubmitted() {
        showScreen(link)

        compose.onNodeWithText("Enter a link instead").performClick()
        compose.onNodeWithText("Card link").performTextInput("https://example.com/nope")

        compose.onNodeWithText("That doesn't contain a card link.").assertIsDisplayed()
        compose.onNodeWithText("Swap cards").assertIsNotEnabled()
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(emptyList<String>(), collected)
    }

    // ---------- Result dialogs ----------

    @Test
    fun swapped_namesThePerson_andOffersTheBinder() {
        val clicks = mutableListOf<String>()
        val card = CollectedCard("u2", Profile("Sam", "", emptyList(), emptyList(), true), CueStatus.YELLOW)
        compose.setContent {
            UnspokenQueuesTheme {
                SwapResultDialog(SwapResult.Swapped(card), onViewBinder = { clicks += "binder" }, onDismiss = { clicks += "dismiss" })
            }
        }

        compose.onNodeWithText("Sam's card is in your binder, and yours is in theirs.").assertIsDisplayed()
        compose.onNodeWithText("View binder").performClick()

        assertEquals(listOf("binder"), clicks)
    }

    @Test
    fun failed_showsTheReason() {
        val clicks = mutableListOf<String>()
        compose.setContent {
            UnspokenQueuesTheme {
                SwapResultDialog(SwapResult.Failed("That's your own card."), onViewBinder = { clicks += "binder" }, onDismiss = { clicks += "dismiss" })
            }
        }

        compose.onNodeWithText("That's your own card.").assertIsDisplayed()
        compose.onNodeWithText("View binder").assertDoesNotExist()
        compose.onNodeWithText("OK").performClick()

        assertEquals(listOf("dismiss"), clicks)
    }

    @Test
    fun openedLink_asksBeforeSwapping() {
        val clicks = mutableListOf<String>()
        compose.setContent {
            UnspokenQueuesTheme {
                ConfirmLinkSwapDialog(onConfirm = { clicks += "confirm" }, onDismiss = { clicks += "dismiss" })
            }
        }

        compose.onNodeWithText("Swap cards?").assertIsDisplayed()
        compose.onNodeWithText("Not now").performClick()
        compose.onNodeWithText("Swap cards").performClick()

        assertEquals(listOf("dismiss", "confirm"), clicks)
    }
}
