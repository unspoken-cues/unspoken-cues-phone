package com.example.unspokenqueues.data

import com.example.unspokenqueues.model.ReactionState
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant

/** RPC-only access. The server derives the sender, checks consent and applies privacy at read time. */
open class ReactionRepository(client: io.github.jan.supabase.SupabaseClient? = null) {
    private val postgrest by lazy { (client ?: SupabaseClientProvider.client).postgrest }

    open suspend fun state(eventId: String): ReactionState =
        postgrest.rpc("event_wave_state", buildJsonObject { put("p_event_id", eventId) })
            .decodeAs<ReactionStateRow>().toReactionState()

    open suspend fun setReceiveWaves(eventId: String, receiveWaves: Boolean): ReactionState =
        postgrest.rpc("set_event_wave_preference", buildJsonObject {
            put("p_event_id", eventId)
            put("p_receive_waves", receiveWaves)
        }).decodeAs<ReactionStateRow>().toReactionState()

    /** Idempotent: a retried request returns the timestamp of the original wave. */
    open suspend fun send(eventId: String, recipientId: String): Instant =
        postgrest.rpc("send_event_wave", buildJsonObject {
            put("p_event_id", eventId)
            put("p_recipient_id", recipientId)
        }).decodeAs<WaveReceiptRow>().createdAt.toInstantOrNull()
            ?: error("Wave receipt has no valid timestamp")
}
