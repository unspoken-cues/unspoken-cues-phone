package com.example.unspokenqueues.ui.components

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.unspokenqueues.model.ReportReason
import com.example.unspokenqueues.ui.theme.UnspokenQueuesTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReportDialogTest {

    @get:Rule
    val compose = createComposeRule()

    private val sent = mutableListOf<Pair<ReportReason, String>>()
    private val done = mutableListOf<Boolean>()

    private fun showDialog() {
        compose.setContent {
            UnspokenQueuesTheme {
                ReportDialog(
                    "Sam",
                    onSend = { reason, note -> sent += reason to note },
                    onSent = { done += it },
                    onDismiss = {},
                )
            }
        }
    }

    @Test
    fun alsoBlock_isCheckedByDefault() {
        showDialog()

        compose.onNodeWithText("Also block Sam").assertIsOn()
    }

    @Test
    fun report_sendsTheChosenReasonAndNote_thenAsksToBlock() {
        showDialog()

        compose.onNodeWithText("Report").assertIsNotEnabled()
        compose.onNodeWithText("Spam").performClick()
        compose.onNodeWithText("Anything else? (optional)").performTextInput("Sends the same link to everyone")
        compose.onNodeWithText("Report").performClick()
        compose.waitForIdle()

        assertEquals(listOf(ReportReason.SPAM to "Sends the same link to everyone"), sent)
        assertEquals(listOf(true), done)
    }
}
