package com.example.unspokenqueues.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordRecoveryTest {
    @Test
    fun onlyDedicatedRecoveryCallbacksAreAccepted() {
        assertTrue(isPasswordRecoveryLink(PASSWORD_RESET_REDIRECT + "#type=recovery&access_token=example"))
        assertTrue(isPasswordRecoveryLink(PASSWORD_RESET_REDIRECT + "/#error=access_denied"))
        listOf(null, "", "not a url", "unspokencues://card/token",
            "https://password-reset", "unspokencues://password-reset.evil",
            "unspokencues://user@password-reset", "unspokencues://password-reset:80",
            "unspokencues://password-reset/other").forEach {
            assertFalse("Unexpected callback: $it", isPasswordRecoveryLink(it))
        }
    }
}
