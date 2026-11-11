package com.example.unspokenqueues.data

import android.content.Context
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.example.unspokenqueues.model.WatchConnection
import com.example.unspokenqueues.model.WatchSnapshot
import com.google.android.gms.tasks.Task
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import java.util.UUID

/** Publish once per state change, then retry the SAME revision while this UI is alive. */
@Composable
fun WatchSync(snapshot: WatchSnapshot?, ownerId: String?, retry: Int,
              onConnection: (WatchConnection) -> Unit, onPublished: (String) -> Unit) {
    val context = LocalContext.current.applicationContext
    val report by rememberUpdatedState(onConnection)
    val published by rememberUpdatedState(onPublished)
    LaunchedEffect(context, snapshot, ownerId, retry) {
        if (snapshot == null) return@LaunchedEffect
        while (isActive) {
            try {
                report(WatchConnection.SYNCING)
                val request = companionRequest(context, snapshot)
                Wearable.getDataClient(context).putDataItem(request.asPutDataRequest().setUrgent()).result()
                if (snapshot.active && ownerId != null) published(ownerId)
                val nodes = Wearable.getNodeClient(context).connectedNodes.result()
                report(if (nodes.isEmpty()) WatchConnection.DISCONNECTED else WatchConnection.CONNECTED)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                report(WatchConnection.DISCONNECTED)
            }
            delay(30_000)
        }
    }
}

private fun companionRequest(context: Context, snapshot: WatchSnapshot): PutDataMapRequest {
    val prefs = context.getSharedPreferences("watch_publisher", Context.MODE_PRIVATE)
    val changed = prefs.getBoolean("active", false) != snapshot.active ||
        prefs.getString("status", "") != snapshot.status || prefs.getString("name", "") != snapshot.name ||
        prefs.getString("token", "") != snapshot.token || prefs.getBoolean("public", false) != snapshot.isPublic
    val previous = prefs.getLong("revision", 0)
    val publisher = prefs.getString("publisher", null) ?: UUID.randomUUID().toString()
    val revision = if (changed || previous == 0L) maxOf(System.currentTimeMillis(), previous + 1) else previous
    prefs.edit().putBoolean("active", snapshot.active).putString("status", snapshot.status)
        .putString("name", snapshot.name).putString("token", snapshot.token)
        .putBoolean("public", snapshot.isPublic).putLong("revision", revision)
        .putString("publisher", publisher).apply()
    return PutDataMapRequest.create("/unspoken-cues/state").apply {
        dataMap.putInt("schema", 1)
        dataMap.putBoolean("active", snapshot.active)
        dataMap.putString("status", snapshot.status)
        dataMap.putString("name", snapshot.name)
        dataMap.putString("token", snapshot.token)
        dataMap.putBoolean("public", snapshot.isPublic)
        dataMap.putLong("revision", revision)
        dataMap.putString("publisher", publisher)
    }
}

private suspend fun <T> Task<T>.result(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { if (continuation.isActive) continuation.resume(it) }
    addOnFailureListener { if (continuation.isActive) continuation.resumeWithException(it) }
    addOnCanceledListener { continuation.cancel() }
}
