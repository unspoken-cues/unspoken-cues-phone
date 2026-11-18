package com.example.unspokenqueues.data

import com.example.unspokenqueues.model.CueStatus
import com.example.unspokenqueues.model.Profile
import com.example.unspokenqueues.model.ProfilePrivacy
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

    /** Onboarding and card edits preserve the cue, including when the row already exists. */
    suspend fun upsertProfile(userId: String, profile: Profile): Boolean {
        val row = table().upsert(profile.toUpsert(userId)) {
            select(SHARING_COLUMNS)
        }.decodeSingle<ProfileRow>()
        return row.allowsWatchSharing(userId)
    }

    /** A delayed PATCH can only modify the exact profile version that authorized it. */
    suspend fun updateStatus(userId: String, status: CueStatus, expectedUpdatedAt: String): ProfileRow? {
        require(expectedUpdatedAt.isNotBlank())
        return table().update(mapOf("status" to status.name)) {
            filter {
                eq("id", userId)
                eq("updated_at", expectedUpdatedAt)
            }
            select(Columns.list("id", "status", "updated_at", "is_public", "private_mode"))
        }.decodeSingleOrNull()
    }

    /**
     * Updates only the card image: the public URL of the picture on the user's S.W.A.P. card, or
     * blank to remove it. Kept out of [upsertProfile] so saving a profile never touches it, and a
     * database without the column only fails here.
     */
    suspend fun updateCardImage(userId: String, url: String) {
        table().update(mapOf("card_image_url" to url)) {
            filter { eq("id", userId) }
        }
    }

    /** Updates only the public-visibility flag. */
    suspend fun updateVisibility(userId: String, isPublic: Boolean): Boolean {
        val row = table().update(mapOf("is_public" to isPublic)) {
            filter { eq("id", userId) }
            select(SHARING_COLUMNS)
        }.decodeSingle<ProfileRow>()
        return row.allowsWatchSharing(userId)
    }

    /** Updates only which fields people the user hasn't swapped with may see. */
    suspend fun updatePrivacy(userId: String, privacy: ProfilePrivacy) {
        table().update(
            mapOf(
                "show_photo" to privacy.showPhoto,
                "show_bio" to privacy.showBio,
                "show_preferences" to privacy.showPreferences,
                "show_boundaries" to privacy.showBoundaries,
                "show_status" to privacy.showStatus,
            ),
        ) {
            filter { eq("id", userId) }
        }
    }

    /** Turns Private Mode on or off: hides the profile from everyone and pauses swaps while on. */
    suspend fun updatePrivateMode(userId: String, on: Boolean): Boolean {
        val row = table().update(mapOf("private_mode" to on)) {
            filter { eq("id", userId) }
            select(SHARING_COLUMNS)
        }.decodeSingle<ProfileRow>()
        return row.allowsWatchSharing(userId)
    }

    /** Records that the user has finished onboarding, so they aren't taken through it again. */
    suspend fun markOnboardingComplete(userId: String) {
        table().update(mapOf("onboarding_complete" to true)) {
            filter { eq("id", userId) }
        }
    }

    private fun ProfileRow.allowsWatchSharing(userId: String): Boolean {
        check(id == userId)
        return isPublic && !privateMode
    }

    private companion object {
        val SHARING_COLUMNS = Columns.list("id", "is_public", "private_mode")
        const val TABLE = "profiles"
    }
}
