package com.example.unspokenqueues.data

import com.example.unspokenqueues.model.Reaction
import com.example.unspokenqueues.model.ReactionState
import com.example.unspokenqueues.model.WaveRecipient
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Required consent/eligibility fields deliberately have no defaults: unknown data fails closed. */
@Serializable
data class ReactionStateRow(
    @SerialName("receive_waves") val receiveWaves: Boolean,
    @SerialName("can_wave") val canWave: Boolean,
    val recipients: List<WaveRecipientRow>,
    val inbox: List<ReactionRow>,
)

@Serializable
data class WaveRecipientRow(
    @SerialName("user_id") val userId: String,
    @SerialName("display_name") val displayName: String,
    val sent: Boolean,
)

@Serializable
data class ReactionRow(
    @SerialName("sender_id") val senderId: String,
    @SerialName("sender_name") val senderName: String,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class WaveReceiptRow(@SerialName("created_at") val createdAt: String)

fun ReactionStateRow.toReactionState(): ReactionState {
    val eligible = receiveWaves && canWave
    return ReactionState(
        receiveWaves = receiveWaves,
        canWave = eligible,
        recipients = if (eligible) recipients.filter { it.userId.isNotBlank() }.distinctBy { it.userId }
            .map { WaveRecipient(it.userId, it.displayName.ifBlank { "Participant" }, it.sent) } else emptyList(),
        inbox = if (eligible) inbox.mapNotNull { row ->
            row.createdAt.toInstantOrNull()?.takeIf { row.senderId.isNotBlank() }?.let {
                Reaction(row.senderId, row.senderName.ifBlank { "Participant" }, it)
            }
        }.sortedByDescending { it.createdAt } else emptyList(),
    )
}
