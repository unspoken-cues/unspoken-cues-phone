package com.example.unspokenqueues.debug

import com.example.unspokenqueues.data.SafetyRepository
import com.example.unspokenqueues.model.ReactionState
import com.example.unspokenqueues.model.ReportReason

/** Safety actions in the debug event previews stay in memory. */
internal class PreviewSafetyRepository(private val viewer: () -> String) : SafetyRepository() {
    private val blocks = mutableSetOf<Pair<String, String>>()

    override suspend fun block(otherUserId: String) {
        blocks += viewer() to otherUserId
    }

    override suspend fun report(otherUserId: String, reason: ReportReason, note: String, eventId: String?) = Unit

    fun visible(other: String): Boolean {
        val me = viewer()
        return (me to other) !in blocks && (other to me) !in blocks
    }

    fun visibleWaves(state: ReactionState) = state.copy(
        recipients = state.recipients.filter { visible(it.userId) },
        inbox = state.inbox.filter { visible(it.senderId) },
    )
}
