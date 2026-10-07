package com.example.unspokenqueues.data

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserInfo
import kotlinx.coroutines.flow.Flow

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
