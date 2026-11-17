package com.example.unspokenqueues.model

import java.time.Instant

/** A quiet, event-scoped wave. This never creates a card connection or a conversation. */
data class Reaction(val senderId: String, val senderName: String, val createdAt: Instant)

/** Only actionable public participants are returned; private consent settings never leave the server. */
data class WaveRecipient(val userId: String, val displayName: String, val sent: Boolean)

data class ReactionState(
    val receiveWaves: Boolean,
    val canWave: Boolean,
    val recipients: List<WaveRecipient>,
    val inbox: List<Reaction>,
)

/** Local cue changes and clock expiry hide history even before a best-effort profile save reaches the server. */
fun ReactionState.withLocalEligibility(allowed: Boolean): ReactionState =
    if (allowed) this else copy(canWave = false, recipients = emptyList(), inbox = emptyList())
