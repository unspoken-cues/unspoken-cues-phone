package com.example.unspokenqueues.ui.screens

import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.unspokenqueues.model.Profile
import com.example.unspokenqueues.ui.theme.UnspokenQueuesTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProfileSetupWizardTest {

    @get:Rule
    val compose = createComposeRule()

    private val blank = Profile("", "", emptyList(), emptyList(), isPublic = true)
    private val finished = mutableListOf<Profile>()
    private val uploads = mutableListOf<Uri>()

    private fun showWizard(initial: Profile = blank) {
        compose.setContent {
            UnspokenQueuesTheme {
                ProfileSetupWizard(
                    initial = initial,
                    onUploadAvatar = { uri ->
                        uploads += uri
                        "https://x.test/uploaded.jpg"
                    },
                    onFinish = { finished += it },
                )
            }
        }
    }

    private fun next() = compose.onNodeWithText("Next").performClick()
    private fun back() = compose.onNodeWithText("Back").performClick()

    // The list steps have a single field, identified by its placeholder rather than a label.
    private fun addEntry(text: String) {
        compose.onNode(hasSetTextAction()).performTextInput(text)
        compose.onNodeWithText("Add").performClick()
    }

    // ---------- Steps ----------

    @Test
    fun startsOnTheNameStep_andNeedsANameToGoOn() {
        showWizard()

        compose.onNodeWithText("Step 1 of 6").assertIsDisplayed()
        compose.onNodeWithText("Back").assertDoesNotExist()
        compose.onNodeWithText("Next").assertIsNotEnabled()

        compose.onNodeWithText("Display name").performTextInput("   ")
        compose.onNodeWithText("Next").assertIsNotEnabled()

        compose.onNodeWithText("Display name").performTextInput("Sam")
        compose.onNodeWithText("Next").assertIsEnabled()
    }

    @Test
    fun walksThroughTheSixStepsInOrder() {
        showWizard()

        compose.onNodeWithText("Display name").performTextInput("Sam")
        next()
        compose.onNodeWithText("Step 2 of 6").assertIsDisplayed()
        compose.onNodeWithText("Bio").assertIsDisplayed()
        next()
        compose.onNodeWithText("Step 3 of 6").assertIsDisplayed()
        compose.onNodeWithText("How do you like to connect?").assertIsDisplayed()
        next()
        compose.onNodeWithText("Step 4 of 6").assertIsDisplayed()
        compose.onNodeWithText("What are your boundaries?").assertIsDisplayed()
        next()
        compose.onNodeWithText("Step 5 of 6").assertIsDisplayed()
        compose.onNodeWithText("Add photo").assertIsDisplayed()
        next()
        compose.onNodeWithText("Step 6 of 6").assertIsDisplayed()
        compose.onNodeWithText("Public profile").assertIsDisplayed()
        compose.onNodeWithText("Next").assertDoesNotExist()
        compose.onNodeWithText("Finish").assertIsDisplayed()
    }

    // ---------- Finish gating ----------

    @Test
    fun finishStaysOff_untilThereIsAPreferenceOrBoundary() {
        showWizard()

        compose.onNodeWithText("Display name").performTextInput("Sam")
        repeat(5) { next() }

        compose.onNodeWithText("Finish").assertIsNotEnabled()
        compose.onNodeWithText("Go back and add at least one preference or boundary to finish.").assertIsDisplayed()

        // Back to the boundaries step, add one, and return.
        back()
        back()
        compose.onNodeWithText("Step 4 of 6").assertIsDisplayed()
        addEntry("No photos")
        next()
        next()

        compose.onNodeWithText("Go back and add at least one preference or boundary to finish.").assertDoesNotExist()
        compose.onNodeWithText("Finish").assertIsEnabled().performClick()
        compose.waitForIdle()

        assertEquals(listOf(Profile("Sam", "", emptyList(), listOf("No photos"), true)), finished)
    }

    @Test
    fun theListSteps_sayWhatIsStillNeeded() {
        showWizard()
        compose.onNodeWithText("Display name").performTextInput("Sam")
        next()
        next()

        compose.onNodeWithText("You need at least one preference or boundary", substring = true).assertIsDisplayed()
        addEntry("Text over calls")
        // The chip, not the field's "e.g. Text over calls" placeholder.
        compose.onNodeWithText("Text over calls  ✕").assertIsDisplayed()
        compose.onNodeWithText("You need at least one preference or boundary", substring = true).assertDoesNotExist()

        next()
        compose.onNodeWithText("You need at least one preference or boundary", substring = true).assertDoesNotExist()
    }

    @Test
    fun aPreferenceAloneIsEnoughToFinish() {
        showWizard()

        compose.onNodeWithText("Display name").performTextInput("Sam")
        next()
        next()
        addEntry("Text over calls")
        repeat(3) { next() }

        compose.onNodeWithText("Finish").assertIsEnabled()
    }

    // ---------- The finished profile ----------

    @Test
    fun finish_emitsEverythingThatWasEntered() {
        showWizard()

        compose.onNodeWithText("Display name").performTextInput("  Sam Lee ")
        next()
        compose.onNodeWithText("Bio").performTextInput("Designer. Coffee first.")
        next()
        addEntry("Text over calls")
        addEntry("Small groups")
        next()
        addEntry("No photos")
        next()
        next()
        compose.onNode(isToggleable()).performClick()
        compose.onNodeWithText("Finish").performClick()
        compose.waitForIdle()

        val expected = Profile(
            displayName = "Sam Lee",
            bio = "Designer. Coffee first.",
            preferences = listOf("Text over calls", "Small groups"),
            boundaries = listOf("No photos"),
            isPublic = false,
            avatarUrl = "",
        )
        assertEquals(listOf(expected), finished)
        assertEquals(emptyList<Uri>(), uploads)
    }

    @Test
    fun textTypedButNotAdded_isKeptWhenMovingOn() {
        showWizard()

        compose.onNodeWithText("Display name").performTextInput("Sam")
        next()
        next()
        compose.onNode(hasSetTextAction()).performTextInput("Text over calls")
        next()
        compose.onNode(hasSetTextAction()).performTextInput("No photos")
        next()
        next()
        compose.onNodeWithText("Finish").assertIsEnabled().performClick()
        compose.waitForIdle()

        assertEquals(listOf(Profile("Sam", "", listOf("Text over calls"), listOf("No photos"), true)), finished)
    }

    @Test
    fun startsFromTheProfileItIsGiven() {
        showWizard(Profile("Ana", "Hello", listOf("Direct feedback"), emptyList(), isPublic = false, avatarUrl = "https://x.test/a.jpg"))

        compose.onNodeWithText("Next").assertIsEnabled()
        repeat(5) { next() }
        compose.onNodeWithText("Finish").assertIsEnabled().performClick()
        compose.waitForIdle()

        assertEquals(
            listOf(Profile("Ana", "Hello", listOf("Direct feedback"), emptyList(), false, "https://x.test/a.jpg")),
            finished,
        )
    }

    // ---------- Back ----------

    @Test
    fun back_returnsToThePreviousStep_withItsAnswerIntact() {
        showWizard()

        compose.onNodeWithText("Display name").performTextInput("Sam")
        next()
        compose.onNodeWithText("Bio").performTextInput("Hello")
        next()
        compose.onNodeWithText("Step 3 of 6").assertIsDisplayed()

        back()
        compose.onNodeWithText("Step 2 of 6").assertIsDisplayed()
        compose.onNodeWithText("Hello").assertIsDisplayed()

        Espresso.pressBack()
        compose.onNodeWithText("Step 1 of 6").assertIsDisplayed()
        compose.onNodeWithText("Sam").assertIsDisplayed()
    }
}
