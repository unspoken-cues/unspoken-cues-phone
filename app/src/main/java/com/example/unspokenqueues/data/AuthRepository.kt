package com.example.unspokenqueues.data

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.parseSessionFromUrl
import io.github.jan.supabase.auth.exception.AuthErrorCode
import io.github.jan.supabase.auth.exception.AuthRestException
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserInfo
import kotlinx.coroutines.flow.Flow
import java.io.IOException

/** The account was created, but there is no session until the user confirms their email. */
class EmailConfirmationRequiredException : Exception("Email confirmation required")

/**
 * What to tell the user when signing in or creating an account failed. The exception's own
 * message is never shown: it is written for developers and carries the request URL and headers.
 */
fun authFailureMessage(e: Throwable): String = when (e) {
    is EmailConfirmationRequiredException -> "Almost there. Open the confirmation link we emailed you, then sign in."
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
    AuthErrorCode.SamePassword -> "Choose a password different from your current password."
    AuthErrorCode.SessionExpired, AuthErrorCode.SessionNotFound, AuthErrorCode.OtpExpired ->
        "Your session or reset link has expired. Sign in again or request a new reset link."
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

    suspend fun requestPasswordReset(email: String) {
        auth.resetPasswordForEmail(email.trim(), redirectUrl = PASSWORD_RESET_REDIRECT)
    }

    /** Validate the recovery session with Supabase before replacing any existing session. */
    @OptIn(kotlin.time.ExperimentalTime::class)
    suspend fun recoverPassword(link: String) {
        require(isPasswordRecoveryLink(link))
        auth.awaitInitialization()
        val session = auth.parseSessionFromUrl(link)
        require(session.type == "recovery")
        val user = auth.retrieveUser(session.accessToken)
        auth.importSession(session.copy(user = user))
    }

    suspend fun updatePassword(password: String) {
        auth.awaitInitialization()
        check(auth.currentSessionOrNull() != null) { "Recovery session expired" }
        auth.updateUser { this.password = password }
    }

    suspend fun cancelPasswordRecovery() {
        // Drop the temporary session locally even when the device is offline.
        auth.clearSession()
    }

    suspend fun signIn(email: String, password: String) {
        auth.signInWith(Email) {
            this.email = email.trim()
            this.password = password
        }
    }

    /**
     * Creates a new account and signs it in. If the project requires email confirmation there is
     * no session until the user confirms, which is reported as an
     * [EmailConfirmationRequiredException] so the caller can tell them to check their inbox.
     */
    suspend fun signUp(email: String, password: String) {
        auth.signUpWith(Email) {
            this.email = email.trim()
            this.password = password
        }
        // signUpWith has already stored the session if the server sent one.
        if (auth.currentSessionOrNull() == null) throw EmailConfirmationRequiredException()
    }

    suspend fun signOut() {
        auth.signOut()
    }
}
