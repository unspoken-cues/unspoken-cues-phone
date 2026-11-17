package com.example.unspokenqueues.data

import com.example.unspokenqueues.model.BlockedUser
import com.example.unspokenqueues.model.ReportReason
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** One row of `my_blocked_users()`. */
@Serializable
data class BlockedUserRow(
    @SerialName("user_id") val userId: String,
    @SerialName("display_name") val displayName: String = "",
)

fun BlockedUserRow.toBlockedUser(): BlockedUser = BlockedUser(userId, displayName)

/** Payload for filing a report. The reporter and timestamp are set by the server. */
@Serializable
data class ReportInsert(
    val reported: String,
    val reason: String,
    val note: String,
    @SerialName("event_id") val eventId: String?,
)

/**
 * Blocks, unblocks and reports other users, in the `blocks` and `reports` tables.
 *
 * A block hides both people from each other, ends their swap and stops new ones. Reports are
 * write-only: the team reads them in the Supabase dashboard.
 *
 * Open so UI tests can swap in a fake that never talks to the server.
 */
open class SafetyRepository(
    client: io.github.jan.supabase.SupabaseClient? = null,
) {
    private val postgrest by lazy { (client ?: SupabaseClientProvider.client).postgrest }

    /** Blocks [otherUserId] and ends any swap with them. Blocking twice is harmless. */
    open suspend fun block(otherUserId: String) {
        postgrest.rpc(BLOCK_RPC, buildJsonObject { put("other_id", otherUserId) })
    }

    /**
     * Lifts the signed-in user's block on [otherUserId]. Row Level Security limits the delete to
     * the user's own blocks. Their swap doesn't come back; they can swap again.
     */
    open suspend fun unblock(otherUserId: String) {
        postgrest.from(BLOCKS).delete { filter { eq("blocked", otherUserId) } }
    }

    /** The people the signed-in user has blocked, newest first. */
    open suspend fun blockedUsers(): List<BlockedUser> =
        postgrest.rpc(BLOCKED_RPC).decodeList<BlockedUserRow>().map { it.toBlockedUser() }

    /**
     * Reports [otherUserId] for [reason], with an optional [note] (the server allows up to 1000
     * characters) and the event it happened at, if any.
     */
    open suspend fun report(otherUserId: String, reason: ReportReason, note: String, eventId: String?) {
        postgrest.from(REPORTS).insert(ReportInsert(otherUserId, reason.wire, note.trim(), eventId))
    }

    private companion object {
        const val BLOCKS = "blocks"
        const val REPORTS = "reports"
        const val BLOCK_RPC = "block_user"
        const val BLOCKED_RPC = "my_blocked_users"
    }
}
