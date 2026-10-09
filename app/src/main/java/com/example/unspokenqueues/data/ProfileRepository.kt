package com.example.unspokenqueues.data

import com.example.unspokenqueues.model.CueStatus
import com.example.unspokenqueues.model.Profile
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns

/**
 * Reads and writes the signed-in user's profile + current status in the `profiles` table.
 *
 * Row Level Security on the server restricts every query to `auth.uid() = id`, so even though we
 * pass the user id explicitly, a user can only ever touch their own row.
 */
class ProfileRepository(
    client: io.github.jan.supabase.SupabaseClient? = null,
) {
    private val postgrest by lazy { (client ?: SupabaseClientProvider.client).postgrest }

    private fun table() = postgrest.from(TABLE)

    /** Loads the user's profile row, or null if none exists yet. */
    suspend fun loadProfile(userId: String): ProfileRow? =
        table().select(Columns.ALL) {
            filter { eq("id", userId) }
            limit(1)
        }.decodeSingleOrNull()

    /** Creates or replaces the user's full profile row (including status). */
    suspend fun upsertProfile(userId: String, profile: Profile, status: CueStatus) {
        table().upsert(profile.toUpsert(userId, status))
    }

    /** Updates only the status column for the user. */
    suspend fun updateStatus(userId: String, status: CueStatus) {
        table().update(mapOf("status" to status.name)) {
            filter { eq("id", userId) }
        }
    }

    /** Updates only the public-visibility flag. */
    suspend fun updateVisibility(userId: String, isPublic: Boolean) {
        table().update(mapOf("is_public" to isPublic)) {
            filter { eq("id", userId) }
        }
    }

    /** Records that the user has finished onboarding, so they aren't taken through it again. */
    suspend fun markOnboardingComplete(userId: String) {
        table().update(mapOf("onboarding_complete" to true)) {
            filter { eq("id", userId) }
        }
    }

    private companion object {
        const val TABLE = "profiles"
    }
}
