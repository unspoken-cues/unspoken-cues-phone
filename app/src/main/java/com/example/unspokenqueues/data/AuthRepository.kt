package com.example.unspokenqueues.data

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.exception.AuthErrorCode
import io.github.jan.supabase.auth.exception.AuthRestException
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserInfo
import kotlinx.coroutines.flow.Flow
import java.io.IOException

/**
 * What to tell the user when signing in or creating an account failed. The exception's own
 * message is never shown: it is written for developers and carries the request URL and headers.
 */
fun authFailureMessage(e: Throwable): String = when (e) {
    is AuthRestException -> authErrorMessage(e.errorCode)
    // The request never got an answer: offline, DNS failure or a timeout.
    is IOException -> "Couldn't reach the server. Check your connection and try again."
    else -> authErrorMessage(null)
}

/**
 * The message for an error [code] Supabase Auth answered with; null stands for a code this app
 * doesn't know.
 */
fun authErrorMessage(code: AuthErrorCode?): String = when (code) {
    AuthErrorCode.InvalidCredentials -> "Incorrect email or password."
    AuthErrorCode.EmailNotConfirmed -> "Confirm your email first: open the link we sent you, then sign in."
    AuthErrorCode.UserAlreadyExists, AuthErrorCode.EmailExists ->
        "An account with that email already exists. Sign in instead."
    AuthErrorCode.WeakPassword -> "That password is too weak. Use a longer one with letters, numbers and symbols."
    AuthErrorCode.EmailAddressInvalid, AuthErrorCode.ValidationFailed ->
        "That doesn't look like a valid email address."
    AuthErrorCode.OverRequestRateLimit, AuthErrorCode.OverEmailSendRateLimit ->
        "Too many attempts. Wait a minute, then try again."
    AuthErrorCode.SignupDisabled, AuthErrorCode.EmailProviderDisabled ->
        "New accounts can't be created right now."
    AuthErrorCode.UserBanned -> "This account has been disabled."
    else -> "Something went wrong. Please try again."
}

/**
 * Thin wrapper around Supabase Auth for email/password accounts.
 *
 * All calls are suspending and may throw [io.github.jan.supabase.auth.exception.AuthRestException]
 * (and subtypes like weak-password / invalid-credentials). Callers should catch and surface a
 * friendly message. Sessions are persisted and auto-refreshed by the Auth plugin.
 */
class AuthRepository(
    client: io.github.jan.supabase.SupabaseClient? = null,
) {
    private val auth by lazy { (client ?: SupabaseClientProvider.client).auth }

    /** Emits the current session state (loading / authenticated / not authenticated). */
    val sessionStatus: Flow<SessionStatus> get() = auth.sessionStatus

    /** The signed-in user, or null if there is no active session. */
    fun currentUser(): UserInfo? = auth.currentUserOrNull()

    fun currentUserId(): String? = currentUser()?.id

    suspend fun signIn(email: String, password: String) {
        auth.signInWith(Email) {
            this.email = email.trim()
            this.password = password
        }
    }

    /**
     * Creates a new account. If the project requires email confirmation, there will be no active
     * session until the user confirms; [currentUserId] stays null in that case.
     */
    suspend fun signUp(email: String, password: String) {
        auth.signUpWith(Email) {
            this.email = email.trim()
            this.password = password
        }
    }

    suspend fun signOut() {
        auth.signOut()
    }
}
