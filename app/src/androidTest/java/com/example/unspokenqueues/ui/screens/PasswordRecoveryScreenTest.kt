package com.example.unspokenqueues.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.unspokenqueues.ui.theme.UnspokenQueuesTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class PasswordRecoveryScreenTest {
    @get:Rule val compose = createComposeRule()
    private val saved = mutableListOf<String>()
    private var continued = false

    private fun show(ready: Boolean = true, processing: Boolean = false, failure: Exception? = null) {
        compose.setContent {
            UnspokenQueuesTheme {
                NewPasswordScreen(
                    ready = ready, processing = processing,
                    linkError = "This reset link is invalid or has expired.",
                    onUpdatePassword = {
                        if (failure != null) throw failure
                        saved += it
                    },
                    onCancel = {}, onContinue = { continued = true },
                )
            }
        }
    }

    @Test fun checkingLink_doesNotExposePasswordEntry() {
        show(ready = false, processing = true)
        compose.onNodeWithText("Checking your reset link…").assertIsDisplayed()
        compose.onNodeWithText("New password").assertDoesNotExist()
        compose.onNodeWithText("Save new password").assertDoesNotExist()
    }

    @Test fun invalidLink_doesNotAllowUpdatingPassword() {
        show(ready = false)
        compose.onNodeWithText("This reset link is invalid or has expired.").assertIsDisplayed()
        compose.onNodeWithText("Back to sign in").assertIsDisplayed()
        compose.onNodeWithText("New password").assertDoesNotExist()
        assertEquals(emptyList<String>(), saved)
    }

    @Test fun mismatchedPasswords_doNotUpdateAccount() {
        show()
        compose.onNodeWithText("New password").performTextInput("long-password-1")
        compose.onNodeWithText("Confirm password").performTextInput("long-password-2")
        compose.onNodeWithText("Save new password").performClick()
        compose.onNodeWithText("Those passwords don't match.").assertIsDisplayed()
        assertEquals(emptyList<String>(), saved)
    }

    @Test fun emptyPassword_doesNotUpdateAccount() {
        show()
        compose.onNodeWithText("Save new password").performClick()
        compose.onNodeWithText("Enter a new password.").assertIsDisplayed()
        assertEquals(emptyList<String>(), saved)
    }

    @Test fun matchingPasswords_updateAccount_andAllowContinuing() {
        show()
        compose.onNodeWithText("New password").performTextInput("long-password-1")
        compose.onNodeWithText("Confirm password").performTextInput("long-password-1")
        compose.onNodeWithText("Save new password").performClick()
        compose.waitForIdle()
        assertEquals(listOf("long-password-1"), saved)
        compose.onNodeWithText("Password updated").assertIsDisplayed()
        compose.onNodeWithText("New password").assertDoesNotExist()
        compose.onNodeWithText("Continue").performClick()
        compose.runOnIdle { assertEquals(true, continued) }
    }

    @Test fun failedUpdate_keepsFormAvailable_andHidesServerDetails() {
        show(failure = IOException("secret server details"))
        compose.onNodeWithText("New password").performTextInput("long-password-1")
        compose.onNodeWithText("Confirm password").performTextInput("long-password-1")
        compose.onNodeWithText("Save new password").performClick()
        compose.onNodeWithText("Couldn't reach the server. Check your connection and try again.").assertIsDisplayed()
        compose.onNodeWithText("Password updated").assertDoesNotExist()
        compose.onNodeWithText("secret server details").assertDoesNotExist()
    }
}
