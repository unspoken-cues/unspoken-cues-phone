package com.example.unspokenqueues.data

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.example.unspokenqueues.model.WatchConnection
import com.example.unspokenqueues.model.WatchSnapshot
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
internal fun WatchSync(sync: PhoneCueSync, snapshot: WatchSnapshot?, ownerId: String?, retry: Int,
                       onConnection: (WatchConnection) -> Unit, onPublished: (String) -> Unit) {
    val context = LocalContext.current.applicationContext
    val report by rememberUpdatedState(onConnection)
    val published by rememberUpdatedState(onPublished)
    var handledRetry by remember { mutableIntStateOf(0) }
    val localEdits by sync.publicationRequests.collectAsState()
    // The coordinator owns status. A watch commit during this effect must not cancel its own receipt.
    val sharing = snapshot?.copy(status = "")
    LaunchedEffect(context, sharing, ownerId, retry, localEdits) {
        if (sharing == null && retry == handledRetry) return@LaunchedEffect
        while (isActive) {
            val manual = handledRetry != retry
            report(WatchConnection.SYNCING)
            try {
                if (sharing != null) sync.sharing(ownerId, sharing)
                sync.reconcile(manual)
                handledRetry = retry
                if (snapshot?.active == true && ownerId != null) published(ownerId)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Feedback from reconciliation is independent of the node's connection state.
            }
            val nodes = try {
                Wearable.getNodeClient(context).connectedNodes.result()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                emptyList()
            }
            report(if (nodes.isEmpty()) WatchConnection.DISCONNECTED else WatchConnection.CONNECTED)
            delay(30_000)
        }
    }
}
