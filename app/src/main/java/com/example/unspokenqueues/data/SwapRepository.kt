package com.example.unspokenqueues.data

import com.example.unspokenqueues.model.CollectedCard
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Row shape of `public.swaps`: one row per pair of users who hold each other's card. */
@Serializable
data class SwapRow(
    @SerialName("user_a") val userA: String,
    @SerialName("user_b") val userB: String,
)

/**
 * Reads and removes S.W.A.P. connections in the `swaps` table.
 *
 * A swap is a single row shared by both people, so deleting it removes each person's card from
 * the other's binder at once. Row Level Security limits every query to swaps the signed-in user
 * is part of.
 */
class SwapRepository(
    client: io.github.jan.supabase.SupabaseClient? = null,
) {
    private val postgrest by lazy { (client ?: SupabaseClientProvider.client).postgrest }

    /** Loads the cards in the user's binder: the profile of everyone they've swapped with. */
    suspend fun loadBinder(userId: String): List<CollectedCard> {
        val partnerIds = postgrest.from(SWAPS).select().decodeList<SwapRow>()
            .map { if (it.userA == userId) it.userB else it.userA }
        if (partnerIds.isEmpty()) return emptyList()
        return postgrest.from(PROFILES)
            .select { filter { isIn("id", partnerIds) } }
            .decodeList<ProfileRow>()
            .map { CollectedCard(it.id, it.toProfile(), it.toCueStatus()) }
            .sortedBy { it.name.lowercase() }
    }

    /** Deletes the swap between the two users, removing each from the other's binder. */
    suspend fun removeSwap(userId: String, otherUserId: String) {
        postgrest.from(SWAPS).delete {
            filter {
                or {
                    and { eq("user_a", userId); eq("user_b", otherUserId) }
                    and { eq("user_a", otherUserId); eq("user_b", userId) }
                }
            }
        }
    }

    private companion object {
        const val SWAPS = "swaps"
        const val PROFILES = "profiles"
    }
}
