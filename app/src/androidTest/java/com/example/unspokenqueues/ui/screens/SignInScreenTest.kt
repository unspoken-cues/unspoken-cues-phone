package com.example.unspokenqueues.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.unspokenqueues.ui.theme.UnspokenQueuesTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class SignInScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private fun show(failure: Exception) {
        compose.setContent {
            UnspokenQueuesTheme {
                SignInScreen(onSignIn = { _, _ -> throw failure }, onSignUp = { _, _ -> throw failure })
            }
        }
        compose.onNodeWithText("Email").performTextInput("sam@example.com")
        compose.onNodeWithText("Password").performTextInput("hunter22")
    }

    @Test
    fun withoutAnEmailOrPassword_asksForThem() {
        compose.setContent {
            UnspokenQueuesTheme { SignInScreen(onSignIn = { _, _ -> }, onSignUp = { _, _ -> }) }
        }

        compose.onNodeWithText("Sign in").performClick()

        compose.onNodeWithText("Enter your email and password.").assertIsDisplayed()
    }

    @Test
    fun failedSignIn_showsAPlainMessage_notTheServersResponse() {
        show(IOException("HTTP request to https://abc.supabase.co/auth/v1/token (POST) failed with message: timeout"))

        compose.onNodeWithText("Sign in").performClick()

        compose.onNodeWithText("Couldn't reach the server. Check your connection and try again.").assertIsDisplayed()
        compose.onNodeWithText("supabase", substring = true, ignoreCase = true).assertDoesNotExist()
    }

    @Test
    fun failedSignUp_showsAPlainMessageToo() {
        show(IllegalStateException("URL: https://abc.supabase.co/auth/v1/signup Headers: [apikey=...]"))

        compose.onNodeWithText("Create an account").performClick()

        compose.onNodeWithText("Something went wrong. Please try again.").assertIsDisplayed()
        compose.onNodeWithText("supabase", substring = true, ignoreCase = true).assertDoesNotExist()
    }
}
