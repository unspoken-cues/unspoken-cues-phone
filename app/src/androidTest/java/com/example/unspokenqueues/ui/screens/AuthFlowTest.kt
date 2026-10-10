package com.example.unspokenqueues.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.unspokenqueues.ui.theme.UnspokenQueuesTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class AuthFlowTest {

    @get:Rule
    val compose = createComposeRule()

    // What each callback was handed, as "kind:email:password".
    private val submitted = mutableListOf<String>()

    private fun showFlow(failure: Exception? = null) {
        compose.setContent {
            UnspokenQueuesTheme {
                AuthFlow(
                    onSignIn = { email, password ->
                        if (failure != null) throw failure
                        submitted += "in:$email:$password"
                    },
                    onSignUp = { email, password ->
                        if (failure != null) throw failure
                        submitted += "up:$email:$password"
                    },
                    onPasswordReset = { email ->
                        if (failure != null) throw failure
                        submitted += "reset:$email"
                    },
                )
            }
        }
    }

    // ---------- Welcome ----------

    @Test
    fun welcome_showsTheBrand_andBothWaysIn() {
        showFlow()

        compose.onNodeWithText("Unspoken Cues").assertIsDisplayed()
        compose.onNodeWithText("Sign in").assertIsDisplayed()
        compose.onNodeWithText("Create an account").assertIsDisplayed()
        compose.onNodeWithText("Email").assertDoesNotExist()
        compose.onNodeWithText("Password").assertDoesNotExist()
    }

    // ---------- Sign in ----------

    @Test
    fun signIn_showsEmailAndPassword_withoutAConfirmField() {
        showFlow()

        compose.onNodeWithText("Sign in").performClick()

        compose.onNodeWithText("Welcome back").assertIsDisplayed()
        compose.onNodeWithText("Email").assertIsDisplayed()
        compose.onNodeWithText("Password").assertIsDisplayed()
        compose.onNodeWithText("Confirm password").assertDoesNotExist()
        compose.onNodeWithText("Create an account").assertDoesNotExist()
    }

    @Test
    fun signIn_submittedEmpty_asksForEmailAndPassword() {
        showFlow()
        compose.onNodeWithText("Sign in").performClick()

        compose.onNodeWithText("Sign in").performClick()

        compose.onNodeWithText("Enter your email and password.").assertIsDisplayed()
        assertEquals(emptyList<String>(), submitted)
    }

    @Test
    fun signIn_handsOverWhatWasTyped() {
        showFlow()
        compose.onNodeWithText("Sign in").performClick()

        compose.onNodeWithText("Email").performTextInput("sam@example.com")
        compose.onNodeWithText("Password").performTextInput("hunter22")
        compose.onNodeWithText("Sign in").performClick()
        compose.waitForIdle()

        assertEquals(listOf("in:sam@example.com:hunter22"), submitted)
    }

    @Test
    fun failedSignIn_showsAPlainMessage_notTheServersResponse() {
        showFlow(IOException("HTTP request to https://abc.supabase.co/auth/v1/token (POST) failed with message: timeout"))
        compose.onNodeWithText("Sign in").performClick()

        compose.onNodeWithText("Email").performTextInput("sam@example.com")
        compose.onNodeWithText("Password").performTextInput("hunter22")
        compose.onNodeWithText("Sign in").performClick()

        compose.onNodeWithText("Couldn't reach the server. Check your connection and try again.").assertIsDisplayed()
        compose.onNodeWithText("supabase", substring = true, ignoreCase = true).assertDoesNotExist()
    }

    // ---------- Create account ----------

    @Test
    fun forgotPassword_requestsReset_withoutAPassword_andReturnsToSignIn() {
        showFlow()
        compose.onNodeWithText("Sign in").performClick()
        compose.onNodeWithText("Forgot password?").performClick()
        compose.onNodeWithText("Password").assertDoesNotExist()
        compose.onNodeWithText("Email").performTextInput(" sam@example.com ")
        compose.onNodeWithText("Send reset link").performClick()
        compose.waitForIdle()
        assertEquals(listOf("reset:sam@example.com"), submitted)
        compose.onNodeWithText("Check your email").assertIsDisplayed()
        compose.onNodeWithText("If an account exists", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Back to sign in").performClick()
        compose.onNodeWithText("Welcome back").assertIsDisplayed()
    }

    @Test
    fun forgotPassword_invalidEmail_doesNotSend() {
        showFlow()
        compose.onNodeWithText("Sign in").performClick()
        compose.onNodeWithText("Forgot password?").performClick()
        compose.onNodeWithText("Email").performTextInput("invalid")
        compose.onNodeWithText("Send reset link").performClick()
        compose.onNodeWithText("Enter a valid email address.").assertIsDisplayed()
        assertEquals(emptyList<String>(), submitted)
    }

    @Test
    fun forgotPassword_networkFailure_allowsRetry() {
        showFlow(IOException("secret server details"))
        compose.onNodeWithText("Sign in").performClick()
        compose.onNodeWithText("Forgot password?").performClick()
        compose.onNodeWithText("Email").performTextInput("sam@example.com")
        compose.onNodeWithText("Send reset link").performClick()
        compose.onNodeWithText("Couldn't reach the server. Check your connection and try again.").assertIsDisplayed()
        compose.onNodeWithText("Check your email").assertDoesNotExist()
    }

    @Test
    fun forgotPassword_systemBack_returnsToSignIn() {
        showFlow()
        compose.onNodeWithText("Sign in").performClick()
        compose.onNodeWithText("Forgot password?").performClick()
        Espresso.pressBack()
        compose.onNodeWithText("Welcome back").assertIsDisplayed()
    }

    @Test
    fun createAccount_showsEmailPasswordAndConfirm() {
        showFlow()

        compose.onNodeWithText("Create an account").performClick()

        compose.onNodeWithText("Create your account").assertIsDisplayed()
        compose.onNodeWithText("Email").assertIsDisplayed()
        compose.onNodeWithText("Password").assertIsDisplayed()
        compose.onNodeWithText("Confirm password").assertIsDisplayed()
        compose.onNodeWithText("Sign in").assertDoesNotExist()
    }

    @Test
    fun createAccount_submittedEmpty_asksForEmailAndPassword() {
        showFlow()
        compose.onNodeWithText("Create an account").performClick()

        compose.onNodeWithText("Create account").performClick()

        compose.onNodeWithText("Enter your email and password.").assertIsDisplayed()
        assertEquals(emptyList<String>(), submitted)
    }

    @Test
    fun createAccount_withPasswordsThatDiffer_saysSo() {
        showFlow()
        compose.onNodeWithText("Create an account").performClick()

        compose.onNodeWithText("Email").performTextInput("sam@example.com")
        compose.onNodeWithText("Password").performTextInput("hunter22")
        compose.onNodeWithText("Confirm password").performTextInput("hunter23")
        compose.onNodeWithText("Create account").performClick()

        compose.onNodeWithText("Those passwords don't match.").assertIsDisplayed()
        assertEquals(emptyList<String>(), submitted)
    }

    @Test
    fun createAccount_withMatchingPasswords_handsOverWhatWasTyped() {
        showFlow()
        compose.onNodeWithText("Create an account").performClick()

        compose.onNodeWithText("Email").performTextInput("sam@example.com")
        compose.onNodeWithText("Password").performTextInput("hunter22")
        compose.onNodeWithText("Confirm password").performTextInput("hunter22")
        compose.onNodeWithText("Create account").performClick()
        compose.waitForIdle()

        assertEquals(listOf("up:sam@example.com:hunter22"), submitted)
    }

    @Test
    fun failedSignUp_showsAPlainMessageToo() {
        showFlow(IllegalStateException("URL: https://abc.supabase.co/auth/v1/signup Headers: [apikey=...]"))
        compose.onNodeWithText("Create an account").performClick()

        compose.onNodeWithText("Email").performTextInput("sam@example.com")
        compose.onNodeWithText("Password").performTextInput("hunter22")
        compose.onNodeWithText("Confirm password").performTextInput("hunter22")
        compose.onNodeWithText("Create account").performClick()

        compose.onNodeWithText("Something went wrong. Please try again.").assertIsDisplayed()
        compose.onNodeWithText("supabase", substring = true, ignoreCase = true).assertDoesNotExist()
    }

    // ---------- Back ----------

    @Test
    fun backButton_returnsFromEachFormToWelcome() {
        showFlow()

        compose.onNodeWithText("Sign in").performClick()
        compose.onNodeWithText("Back").performClick()
        compose.onNodeWithText("Unspoken Cues").assertIsDisplayed()

        compose.onNodeWithText("Create an account").performClick()
        compose.onNodeWithText("Back").performClick()
        compose.onNodeWithText("Unspoken Cues").assertIsDisplayed()
        compose.onNodeWithText("Confirm password").assertDoesNotExist()
    }

    @Test
    fun systemBack_returnsFromAFormToWelcome() {
        showFlow()
        compose.onNodeWithText("Create an account").performClick()
        compose.onNodeWithText("Create your account").assertIsDisplayed()

        Espresso.pressBack()

        compose.onNodeWithText("Unspoken Cues").assertIsDisplayed()
        compose.onNodeWithText("Create an account").assertIsDisplayed()
    }

    @Test
    fun aFormLeftAndReopened_startsEmpty() {
        showFlow()
        compose.onNodeWithText("Sign in").performClick()
        compose.onNodeWithText("Email").performTextInput("sam@example.com")
        compose.onNodeWithText("Back").performClick()

        compose.onNodeWithText("Sign in").performClick()

        compose.onNodeWithText("sam@example.com").assertDoesNotExist()
    }
}
