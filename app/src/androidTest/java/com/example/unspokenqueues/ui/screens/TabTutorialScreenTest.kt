package com.example.unspokenqueues.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.unspokenqueues.Tab
import com.example.unspokenqueues.ui.theme.UnspokenQueuesTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TabTutorialScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private var finished = 0

    private fun showTutorial() {
        compose.setContent {
            UnspokenQueuesTheme { TabTutorialScreen(onFinish = { finished++ }) }
        }
    }

    private fun assertOnPage(number: Int) {
        compose.onNodeWithContentDescription("Page $number of 5").assertIsDisplayed()
        compose.onNodeWithText(Tab.entries[number - 1].label).assertIsDisplayed()
    }

    @Test
    fun startsOnTheFirstTab_withSkipAndNext() {
        showTutorial()

        assertOnPage(1)
        compose.onNodeWithText("Cue").assertIsDisplayed()
        compose.onNodeWithText("Skip").assertIsDisplayed()
        compose.onNodeWithText("Next").assertIsDisplayed()
        compose.onNodeWithText("Back").assertDoesNotExist()
        compose.onNodeWithText("Done").assertDoesNotExist()
    }

    @Test
    fun next_stepsThroughAllFiveTabs_andEndsOnDone() {
        showTutorial()

        listOf("Cue", "Events", "QR", "Binder", "Profile").forEachIndexed { index, label ->
            assertOnPage(index + 1)
            compose.onNodeWithText(label).assertIsDisplayed()
            if (index < 4) compose.onNodeWithText("Next").performClick()
        }

        compose.onNodeWithText("Next").assertDoesNotExist()
        compose.onNodeWithText("Skip").assertDoesNotExist()
        assertEquals(0, finished)

        compose.onNodeWithText("Done").performClick()

        assertEquals(1, finished)
    }

    @Test
    fun skip_finishesFromAnyEarlierPage() {
        showTutorial()

        compose.onNodeWithText("Next").performClick()
        assertOnPage(2)
        compose.onNodeWithText("Skip").performClick()

        assertEquals(1, finished)
    }

    @Test
    fun back_returnsToThePreviousTab() {
        showTutorial()
        compose.onNodeWithText("Next").performClick()
        compose.onNodeWithText("Next").performClick()
        assertOnPage(3)

        compose.onNodeWithText("Back").performClick()
        assertOnPage(2)

        Espresso.pressBack()
        assertOnPage(1)
        assertEquals(0, finished)
    }

    @Test
    fun swiping_changesPageToo() {
        showTutorial()

        // Kept away from the screen edges, where a swipe is the system's back gesture instead.
        compose.onRoot().performTouchInput { swipeLeft(startX = width * 0.8f, endX = width * 0.2f) }
        assertOnPage(2)

        compose.onRoot().performTouchInput { swipeRight(startX = width * 0.2f, endX = width * 0.8f) }
        assertOnPage(1)
    }
}
