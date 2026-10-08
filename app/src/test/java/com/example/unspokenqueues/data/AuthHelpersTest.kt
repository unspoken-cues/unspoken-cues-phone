package com.example.unspokenqueues.data

import io.github.jan.supabase.auth.exception.AuthErrorCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.io.IOException
import java.net.UnknownHostException

class AuthHelpersTest {

    private val generic = authErrorMessage(null)

    // ---------- authErrorMessage ----------

    @Test
    fun wrongEmailOrPassword_doesNotSayWhichOneWasWrong() {
        assertEquals("Incorrect email or password.", authErrorMessage(AuthErrorCode.InvalidCredentials))
    }

    @Test
    fun commonSignInAndSignUpFailures_eachGetTheirOwnMessage() {
        val messages = listOf(
            AuthErrorCode.InvalidCredentials,
            AuthErrorCode.EmailNotConfirmed,
            AuthErrorCode.UserAlreadyExists,
            AuthErrorCode.WeakPassword,
            AuthErrorCode.EmailAddressInvalid,
            AuthErrorCode.OverRequestRateLimit,
            AuthErrorCode.SignupDisabled,
            AuthErrorCode.UserBanned,
        ).map(::authErrorMessage)

        assertEquals(messages.size, messages.toSet().size)
        messages.forEach { assertNotEquals(generic, it) }
    }

    @Test
    fun codesThatMeanTheSameToTheUser_shareAMessage() {
        assertEquals(authErrorMessage(AuthErrorCode.UserAlreadyExists), authErrorMessage(AuthErrorCode.EmailExists))
        assertEquals(authErrorMessage(AuthErrorCode.EmailAddressInvalid), authErrorMessage(AuthErrorCode.ValidationFailed))
        assertEquals(authErrorMessage(AuthErrorCode.OverRequestRateLimit), authErrorMessage(AuthErrorCode.OverEmailSendRateLimit))
    }

    @Test
    fun anyOtherCode_getsTheGenericMessage() {
        assertEquals(generic, authErrorMessage(AuthErrorCode.UnexpectedFailure))
        assertEquals(generic, authErrorMessage(AuthErrorCode.BadJwt))
    }

    // ---------- authFailureMessage ----------

    @Test
    fun noAnswerFromTheServer_isAConnectionProblem() {
        val offline = authFailureMessage(UnknownHostException("Unable to resolve host \"abc.supabase.co\""))
        assertEquals(offline, authFailureMessage(IOException("timeout")))
        assertNotEquals(generic, offline)
    }

    @Test
    fun theExceptionsOwnText_isNeverShown() {
        val secretive = "HTTP request to https://abc.supabase.co/auth/v1/token failed"
        listOf(IOException(secretive), IllegalStateException(secretive), RuntimeException(secretive)).forEach {
            assertFalse(authFailureMessage(it).contains("supabase", ignoreCase = true))
        }
        assertEquals(generic, authFailureMessage(IllegalStateException(secretive)))
    }
}
