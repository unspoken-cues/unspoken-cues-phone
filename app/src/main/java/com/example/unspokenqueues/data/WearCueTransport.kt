package com.example.unspokenqueues.data

import android.content.Context
import android.content.SharedPreferences
import com.example.unspokenqueues.model.CueStatus
import com.example.unspokenqueues.model.WatchSnapshot
import com.google.android.gms.tasks.Task
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal const val CUE_REQUEST_PATH = "/unspoken-cues/cue-request"
private const val STATE_PATH = "/unspoken-cues/state"

private fun DataMap.uuid(key: String): String? = getString(key)?.takeIf {
    it.length == 36 && UUID.fromString(it).toString() == it
}

internal fun readCueRequest(path: String?, map: DataMap): WatchCueRequest? = runCatching {
    if (path != CUE_REQUEST_PATH || map.getInt("schema") != 1) return null
    WatchCueRequest(
        generation = map.uuid("generation") ?: return null,
        baseRevision = map.getLong("baseRevision").takeIf { it > 0 } ?: return null,
        operation = map.uuid("operation") ?: return null,
        writer = map.uuid("writer") ?: return null,
        sequence = map.getLong("sequence").takeIf { it > 0 } ?: return null,
        status = CueStatus.entries.firstOrNull { it.name == map.getString("status") } ?: return null,
    )
}.getOrNull()

@Serializable
private data class SnapshotContent(val snapshot: WatchSnapshot, val generation: String,
                                   val cueRevision: Long, val receipt: WatchCueRequest?)

internal fun companionRequest(prefs: SharedPreferences, state: PhoneCueState, force: Boolean): PutDataMapRequest {
    val shared = if (state.serverAllowsSharing) state.sharing else state.sharing?.copy(isPublic = false, token = "")
    val snapshot = shared?.takeIf { state.owner != null && state.status != null }
        ?.copy(status = state.status!!.name) ?: WatchSnapshot(false, "", "", "", false)
    val content = SnapshotContent(snapshot, if (snapshot.active) state.generation else "",
        if (snapshot.active) state.revision else 0, state.receipt.takeIf { snapshot.active })
    val fingerprint = Json.encodeToString(content)
    val previous = prefs.getLong("revision", 0)
    val publisher = prefs.getString("publisher", null) ?: UUID.randomUUID().toString()
    val changed = force || previous == 0L || prefs.getString("cue_snapshot_v1", null) != fingerprint
    val revision = if (changed) maxOf(System.currentTimeMillis(), previous + 1) else previous
    if (changed) {
        // Reuse the original installation identity and revision counter across app upgrades.
        check(prefs.edit().putLong("revision", revision).putString("publisher", publisher)
            .putString("cue_snapshot_v1", fingerprint)
            .remove("status").remove("name").remove("token").remove("active").remove("public").commit())
    }
    return PutDataMapRequest.create(STATE_PATH).apply {
        dataMap.putInt("schema", 1)
        dataMap.putBoolean("active", snapshot.active)
        dataMap.putString("status", snapshot.status)
        dataMap.putString("name", snapshot.name)
        dataMap.putString("token", snapshot.token)
        dataMap.putBoolean("public", snapshot.isPublic)
        dataMap.putLong("revision", revision)
        dataMap.putString("publisher", publisher)
        dataMap.putString("generation", content.generation)
        dataMap.putLong("cueRevision", content.cueRevision)
        dataMap.putString("receipt", content.receipt?.operation.orEmpty())
        dataMap.putString("receiptWriter", content.receipt?.writer.orEmpty())
        dataMap.putLong("receiptSequence", content.receipt?.sequence ?: 0L)
        dataMap.putLong("receiptBase", content.receipt?.baseRevision ?: 0L)
    }
}

internal class WearCueTransport(private val context: Context, private val prefs: SharedPreferences) : CueTransport {
    private val data get() = Wearable.getDataClient(context)

    override suspend fun requests(): List<WatchCueRequest> {
        val items = data.dataItems.result()
        try {
            return items.mapNotNull { item ->
                if (item.uri.path != CUE_REQUEST_PATH) return@mapNotNull null
                val map = runCatching { DataMapItem.fromDataItem(item).dataMap }.getOrNull() ?: return@mapNotNull null
                readCueRequest(item.uri.path, map)
            }
        } finally {
            items.release()
        }
    }

    override suspend fun publish(state: PhoneCueState, force: Boolean) {
        val request = withContext(Dispatchers.IO) { companionRequest(prefs, state, force).asPutDataRequest().setUrgent() }
        data.putDataItem(request).result()
    }
}

internal suspend fun <T> Task<T>.result(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { if (continuation.isActive) continuation.resume(it) }
    addOnFailureListener { if (continuation.isActive) continuation.resumeWithException(it) }
    addOnCanceledListener { continuation.cancel() }
}
