package com.example.unspokenqueues.alerts

import com.example.unspokenqueues.data.SupabaseClientProvider
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.WearableListenerService
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.UUID

@Serializable
private data class CursorRow(val generation: String, @SerialName("cursor_us") val micros: Long)

/** GMS binds this service for a request even when the phone activity is closed. */
class AnnouncementRequestService : WearableListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onRequest(nodeId: String, path: String, request: ByteArray): Task<ByteArray> {
        if (path != ALERT_PATH) return Tasks.forException(IllegalArgumentException("Unknown request"))
        val reply = TaskCompletionSource<ByteArray>()
        scope.launch {
            try {
                reply.trySetResult(withTimeout(30_000) { poll(AlertCursor.decode(request)) })
            } catch (failure: Exception) {
                // GMS forwards task failures. Keep network diagnostics/credentials off this channel.
                reply.trySetException(IllegalStateException("Announcement check unavailable"))
            }
        }
        return reply.task
    }

    private suspend fun poll(cursor: AlertCursor?): ByteArray {
        val client = SupabaseClientProvider.client
        client.auth.awaitInitialization()
        if (client.auth.sessionStatus.value is SessionStatus.NotAuthenticated) return byteArrayOf()
        val token = checkNotNull(client.auth.currentSessionOrNull()?.accessToken)
        val row = client.postgrest.rpc("watch_announcement_cursor", buildJsonObject {
            if (cursor != null) {
                put("p_generation", cursor.generation.toString())
                put("p_after", cursor.micros)
            }
        }).decodeSingle<CursorRow>()
        // A sign-out, account switch or token refresh during I/O invalidates this response.
        // The caller retries using its last acknowledged cursor.
        check(client.auth.currentSessionOrNull()?.accessToken == token)
        return AlertCursor(UUID.fromString(row.generation), row.micros).encode()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
