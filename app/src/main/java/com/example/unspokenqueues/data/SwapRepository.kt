package com.example.unspokenqueues.data

import com.example.unspokenqueues.model.CollectedCard
import com.example.unspokenqueues.model.CueStatus
import com.example.unspokenqueues.model.Profile
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant

/** One row of `binder_cards()`: a swap partner's card, and when the swap ends if it is temporary. */
@Serializable
data class BinderCardRow(
    val id: String,
    @SerialName("display_name") val displayName: String = "",
    val bio: String = "",
    val preferences: List<String> = emptyList(),
    val boundaries: List<String> = emptyList(),
    val status: String = CueStatus.GREEN.name,
    @SerialName("avatar_url") val avatarUrl: String = "",
    @SerialName("card_image_url") val cardImageUrl: String = "",
    @SerialName("expires_at") val expiresAt: String? = null,
)

/** A swap partner's profile row as a card in the binder; the row's id is the partner's user id. */
fun ProfileRow.toCollectedCard(): CollectedCard = CollectedCard(id, toProfile(), toCueStatus())

/** A binder row as a card. A status the app doesn't know reads as green, as for a profile row. */
fun BinderCardRow.toCollectedCard(): CollectedCard = CollectedCard(
    userId = id,
    // isPublic isn't sent: someone else's visibility setting is none of the viewer's business.
    profile = Profile(displayName, bio, preferences, boundaries, isPublic = false, avatarUrl = avatarUrl, cardImageUrl = cardImageUrl),
    status = CueStatus.entries.firstOrNull { it.name == status } ?: CueStatus.GREEN,
    expiresAt = expiresAt.toInstantOrNull(),
)

/**
 * What to tell the user when collecting a card failed. [sqlState] is the Postgres error code the
 * server raised, or null when the failure wasn't a server answer (e.g. no connection).
 */
fun swapFailureMessage(sqlState: String?): String = when (sqlState) {
    SwapRepository.NO_SUCH_CARD -> "That card code isn't valid any more. Ask them to show it again."
    SwapRepository.OWN_CARD -> "That's your own card."
    SwapRepository.PRIVATE_MODE_ON -> "Turn off Private Mode to swap cards."
    SwapRepository.NOT_ACCEPTING -> "This person isn't accepting swaps right now."
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

    /**
     * Loads the cards in the signed-in user's binder: everyone they have an active swap with. The
     * server leaves out expired swaps, people in Private Mode and blocks either way.
     */
    suspend fun loadBinder(): List<CollectedCard> =
        postgrest.rpc(BINDER_RPC)
            .decodeList<BinderCardRow>()
            .map { it.toCollectedCard() }
            .sortedBy { it.name.lowercase() }

    /**
     * Makes the swap with [otherUserId] temporary and returns when it ends: when the soonest event
     * they're both in ends, else 24 hours from now. If the swap already ends sooner, that time
     * stays. Throws a PostgrestRestException with code [NO_SUCH_CARD] if there is no active swap.
     */
    suspend fun makeTemporary(otherUserId: String): Instant {
        val ends = postgrest.rpc(TEMPORARY_RPC, buildJsonObject { put("other_id", otherUserId) })
            .decodeAs<String>()
        return checkNotNull(ends.toInstantOrNull()) { "Unreadable swap end time: $ends" }
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
     * or is the user's own, or either person is in Private Mode. Swapping again after a swap
     * expired or was made temporary makes it permanent.
     */
    suspend fun swapByToken(token: String): CollectedCard =
        postgrest.rpc(SWAP_RPC, buildJsonObject { put("card_token", token) })
            .decodeAs<ProfileRow>()
            .toCollectedCard()

    /**
     * Ends the swap with [otherUserId], removing each from the other's binder, and replaces the
     * signed-in user's card token so their old QR code and link stop working. Call [myCardToken]
     * afterwards for the new one.
     */
    suspend fun removeSwap(otherUserId: String) {
        postgrest.rpc(REMOVE_RPC, buildJsonObject { put("other_id", otherUserId) })
    }

    companion object {
        private const val REMOVE_RPC = "remove_swap"
        private const val TOKEN_RPC = "my_card_token"
        private const val SWAP_RPC = "swap_by_card_token"
        private const val BINDER_RPC = "binder_cards"
        private const val TEMPORARY_RPC = "make_swap_temporary"

        // Error codes raised by swap_by_card_token(). A block reads as NO_SUCH_CARD on purpose.
        const val NO_SUCH_CARD = "P0002"
        const val OWN_CARD = "22023"
        const val PRIVATE_MODE_ON = "UC001"
        const val NOT_ACCEPTING = "UC002"
    }
}
