package com.example.unspokenqueues.data

import com.example.unspokenqueues.model.CollectedCard
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Row shape of `public.swaps`: one row per pair of users who hold each other's card. */
@Serializable
data class SwapRow(
    @SerialName("user_a") val userA: String,
    @SerialName("user_b") val userB: String,
)

/** A swap partner's profile row as a card in the binder; the row's id is the partner's user id. */
fun ProfileRow.toCollectedCard(): CollectedCard = CollectedCard(id, toProfile(), toCueStatus())

/**
 * What to tell the user when collecting a card failed. [sqlState] is the Postgres error code the
 * server raised, or null when the failure wasn't a server answer (e.g. no connection).
 */
fun swapFailureMessage(sqlState: String?): String = when (sqlState) {
    SwapRepository.NO_SUCH_CARD -> "That card code isn't valid any more. Ask them to show it again."
    SwapRepository.OWN_CARD -> "That's your own card."
    else -> "Couldn't swap cards. Check your connection and try again."
}

/**
 * Creates, reads and removes S.W.A.P. connections in the `swaps` table.
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
            .map { it.toCollectedCard() }
            .sortedBy { it.name.lowercase() }
    }

    /**
     * The signed-in user's secret card token, which their QR code and share link carry. The
     * server creates it the first time it is asked for.
     */
    suspend fun myCardToken(): String = postgrest.rpc(TOKEN_RPC).decodeAs<String>()

    /**
     * Swaps cards with the owner of [token] and returns their card. From then on each person is
     * in the other's binder. Swapping with the same person again is harmless. Throws a
     * PostgrestRestException whose code [swapFailureMessage] understands if the token is unknown
     * or is the user's own.
     */
    suspend fun swapByToken(token: String): CollectedCard =
        postgrest.rpc(SWAP_RPC, buildJsonObject { put("card_token", token) })
            .decodeAs<ProfileRow>()
            .toCollectedCard()

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

    companion object {
        private const val SWAPS = "swaps"
        private const val PROFILES = "profiles"
        private const val TOKEN_RPC = "my_card_token"
        private const val SWAP_RPC = "swap_by_card_token"

        // Error codes raised by swap_by_card_token().
        const val NO_SUCH_CARD = "P0002"
        const val OWN_CARD = "22023"
    }
}
