package com.example.unspokenqueues.data.chat

import android.content.Context
import com.example.unspokenqueues.data.SupabaseClientProvider
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.security.GeneralSecurityException
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Serializable
data class ChatThread(
    @SerialName("thread_id") val id: String,
    @SerialName("peer_id") val peerId: String,
    @SerialName("peer_name") val peerName: String,
    val state: String,
    val incoming: Boolean,
    @SerialName("peer_key") val peerKey: String? = null,
)

data class ChatMessage(val id: String, val mine: Boolean, val text: String, val createdAt: String, val readable: Boolean = true)

interface ChatAccess {
    suspend fun eligiblePeers(eventId: String): Set<String>
    suspend fun threads(eventId: String): List<ChatThread>
    suspend fun request(eventId: String, peerId: String)
    suspend fun respond(threadId: String, accept: Boolean)
    suspend fun messages(eventId: String, thread: ChatThread, before: ChatMessage? = null): List<ChatMessage>
    suspend fun send(eventId: String, thread: ChatThread, messageId: String, text: String)
    suspend fun block(peerId: String)
    fun myFingerprint(): String
    fun peerFingerprint(thread: ChatThread): String?
}

@Serializable
private data class ChatTarget(@SerialName("peer_id") val id: String)

@Serializable
private data class EnvelopeRow(
    @SerialName("message_id") val id: String,
    @SerialName("sender_id") val sender: String,
    val body: String,
    @SerialName("created_at") val created: String,
)

class ChatRepository internal constructor(
    context: Context,
    private val userId: String,
    private val client: () -> SupabaseClient,
    private val currentUser: () -> String?,
) : ChatAccess {
    constructor(context: Context, userId: String) : this(
        context, userId, { SupabaseClientProvider.client },
        { SupabaseClientProvider.client.auth.currentUserOrNull()?.id },
    )

    private val app = context.applicationContext
    private val crypto by lazy { ChatCrypto(app, userId) }
    private val registration = Mutex()
    private var registered = false

    private suspend fun rpc(name: String, params: JsonObjectBuilder.() -> Unit) =
        client().postgrest.rpc(name, buildJsonObject {
            check(currentUser() == userId) { "Please reopen chat with your current account." }
            put("p_expected_user", userId)
            params()
        })

    private suspend fun register() = registration.withLock {
        if (!registered) {
            val publicKey = crypto.publicKey
            rpc("chat_register_key") { put("p_key", publicKey) }
            registered = true
        }
    }

    override suspend fun eligiblePeers(eventId: String) = withContext(Dispatchers.IO) {
        rpc("chat_targets") { put("p_event", eventId) }
            .decodeList<ChatTarget>().map { it.id }.toSet()
    }

    override suspend fun threads(eventId: String) = withContext(Dispatchers.IO) {
        register()
        rpc("chat_threads") { put("p_event", eventId) }.decodeList<ChatThread>()
    }

    override suspend fun request(eventId: String, peerId: String) = withContext(Dispatchers.IO) {
        register()
        rpc("chat_request") { put("p_event", eventId); put("p_peer", peerId) }
        Unit
    }

    override suspend fun respond(threadId: String, accept: Boolean) = withContext(Dispatchers.IO) {
        if (accept) register()
        rpc("chat_respond") { put("p_thread", threadId); put("p_accept", accept) }
        Unit
    }

    override suspend fun messages(eventId: String, thread: ChatThread, before: ChatMessage?) = withContext(Dispatchers.IO) {
        val key = checkNotNull(thread.peerKey) { "Waiting for this attendee to set up chat." }
        crypto.trust(thread.peerId, key)
        val rows = rpc("chat_messages") {
            put("p_thread", thread.id)
            if (before != null) {
                put("p_before", before.createdAt)
                put("p_before_id", before.id)
            }
        }.decodeList<EnvelopeRow>()
        rows.asReversed().map { row ->
            val text = try {
                crypto.open(eventId, thread.id, row.id, row.sender, thread.peerId, key, row.body)
            } catch (_: GeneralSecurityException) {
                null
            } catch (_: IllegalArgumentException) {
                null
            }
            ChatMessage(row.id, row.sender == userId, text.orEmpty(), row.created, readable = text != null)
        }
    }

    override suspend fun send(eventId: String, thread: ChatThread, messageId: String, text: String) =
        withContext(Dispatchers.IO) {
            require(text.isNotBlank() && text.toByteArray().size <= 4096) { "Use up to 4096 bytes of text." }
            val key = checkNotNull(thread.peerKey) { "Waiting for this attendee to set up chat." }
            val body = crypto.seal(eventId, thread.id, messageId, thread.peerId, key, text)
            rpc("chat_send") {
                put("p_thread", thread.id)
                put("p_id", messageId)
                put("p_body", body)
            }
            Unit
        }

    override suspend fun block(peerId: String) {
        rpc("chat_block") { put("p_peer", peerId) }
    }

    override fun myFingerprint() = crypto.fingerprint(crypto.publicKey)
    override fun peerFingerprint(thread: ChatThread) = thread.peerKey?.let { key ->
        crypto.trust(thread.peerId, key)
        crypto.fingerprint(key)
    }
}
