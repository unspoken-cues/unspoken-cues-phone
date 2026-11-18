package com.example.unspokenqueues.data

import android.content.Context
import android.content.SharedPreferences
import com.example.unspokenqueues.model.CueStatus
import com.example.unspokenqueues.model.WatchSnapshot
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException
import java.util.UUID

@Serializable
internal data class WatchCueRequest(
    val generation: String,
    val baseRevision: Long,
    val operation: String,
    val writer: String,
    val sequence: Long,
    val status: CueStatus,
)

@Serializable
internal data class PhoneCueState(
    // Only the random generation is shared with the watch. The Supabase id stays on this phone.
    val owner: String? = null,
    val generation: String = UUID.randomUUID().toString(),
    val revision: Long = 0,
    val status: CueStatus? = null,
    val receipt: WatchCueRequest? = null,
    val inFlight: WatchCueRequest? = null,
    val sharing: WatchSnapshot? = null,
    val serverAllowsSharing: Boolean = false,
    val needsProfileCheck: Boolean = false,
) {
    fun accepts(request: WatchCueRequest): Boolean {
        if (sharing == null || status == null || generation != request.generation) return false
        if (receipt?.operation == request.operation) return false
        if (inFlight != null && inFlight != request &&
            (inFlight.operation == request.operation ||
                (inFlight.writer == request.writer && request.sequence <= inFlight.sequence))) return false
        if (receipt?.writer == request.writer && request.sequence <= receipt.sequence) return false
        if (request.baseRevision == revision) return true
        // The same watch may replace a choice before receiving its receipt.
        return receipt?.writer == request.writer && receipt.baseRevision == request.baseRevision
    }

    fun confirm(cue: CueStatus, accepted: WatchCueRequest? = null) =
        copy(status = cue, revision = revision + 1, receipt = accepted, inFlight = null)
}

internal interface CueBackend {
    fun userId(): String?
    suspend fun profile(id: String): ProfileRow?
    suspend fun write(id: String, cue: CueStatus, expectedUpdatedAt: String): ProfileRow?
}

internal interface CueTransport {
    suspend fun requests(): List<WatchCueRequest>
    suspend fun publish(state: PhoneCueState, force: Boolean)
}

/** The UI and Wear listener share this lock, journal and stream of confirmed cues. */
internal class PhoneCueSync(
    private val prefs: SharedPreferences,
    private val backend: CueBackend,
    private val transport: CueTransport,
) {
    private val mutex = Mutex()
    private val current = MutableStateFlow(
        prefs.getString("cue_state_v1", null)?.let { Json.decodeFromString<PhoneCueState>(it) }
            ?: PhoneCueState(),
    )
    val state = current.asStateFlow()
    private val message = MutableStateFlow("")
    val feedback = message.asStateFlow()
    // Wake the foreground publisher for explicit local edits, including same-value phone choices.
    private val localEdits = MutableStateFlow(0L)
    val publicationRequests = localEdits.asStateFlow()

    private suspend fun save(next: PhoneCueState) {
        if (next == current.value) return
        // Persist fences and operation identities before starting a network write.
        withContext(Dispatchers.IO + NonCancellable) {
            check(prefs.edit().putString("cue_state_v1", Json.encodeToString(next)).commit())
            current.value = next
        }
    }

    private suspend fun alignAccount() {
        val id = backend.userId()
        if (current.value.owner != id) {
            save(PhoneCueState(owner = id))
            message.value = ""
        }
    }

    suspend fun signedOut() = mutex.withLock {
        if (current.value.owner != null) save(PhoneCueState())
        message.value = ""
    }

    suspend fun updateSharingPermission(id: String, allowed: Boolean) = mutex.withLock {
        requireOwner(id)
        save(current.value.copy(serverAllowsSharing = allowed))
        localEdits.value++
    }

    private fun requireOwner(id: String) {
        check(backend.userId() == id && current.value.owner == id)
    }

    private suspend fun refresh(id: String): ProfileRow? {
        requireOwner(id)
        val row = backend.profile(id)
        requireOwner(id)
        if (row == null) {
            save(PhoneCueState(owner = id))
            return null
        }
        check(row.id == id)
        val cue = row.toCueStatus()
        val isPublic = row.isPublic && !row.privateMode
        val state = current.value.copy(serverAllowsSharing = isPublic, needsProfileCheck = false)
        save(state)
        // The process may have died after Supabase committed but before the receipt was stored.
        if (state.inFlight?.status == cue) {
            save(state.confirm(cue, state.inFlight))
            message.value = "Watch cue saved to your profile."
        } else if (state.status != cue) save(state.confirm(cue))
        return row
    }

    suspend fun loadProfile(id: String): ProfileRow? = mutex.withLock {
        alignAccount()
        refresh(id)
    }

    // Once dispatched, the write and local receipt finish together even if Compose or JobScheduler
    // cancels its caller. The DB version predicate also fences a PATCH that outlives an HTTP timeout.
    private suspend fun commitIfCurrent(id: String, cue: CueStatus, base: ProfileRow,
                                        request: WatchCueRequest? = null): ProfileRow? = withContext(NonCancellable) {
        requireOwner(id)
        check(base.id == id && base.updatedAt.isNotBlank())
        try {
            withTimeout(20_000) {
                val updated = backend.write(id, cue, base.updatedAt) ?: return@withTimeout null
                requireOwner(id)
                check(updated.id == id && updated.status == cue.name && updated.updatedAt.isNotBlank())
                val confirmed = if (request == null) current.value.copy(status = cue)
                    else current.value.confirm(cue, request)
                save(confirmed.copy(serverAllowsSharing = updated.isPublic && !updated.privateMode,
                    needsProfileCheck = false))
                updated
            }
        } catch (e: TimeoutCancellationException) {
            throw IOException("Timed out confirming the profile cue", e)
        }
    }

    suspend fun phoneChoice(id: String, cue: CueStatus) = mutex.withLock {
        alignAccount()
        requireOwner(id)
        val state = current.value
        // Even reselecting today's cue invalidates edits based on the previous revision.
        save(state.copy(revision = state.revision + 1, receipt = null, inFlight = null, needsProfileCheck = true))
        try {
            repeat(3) {
                val row = checkNotNull(backend.profile(id))
                if (commitIfCurrent(id, cue, row) != null) {
                    message.value = "Phone cue saved."
                    return@withLock
                }
            }
            error("Profile changed while saving the cue")
        } finally {
            localEdits.value++
        }
    }

    suspend fun sharing(id: String?, snapshot: WatchSnapshot) = mutex.withLock {
        alignAccount()
        if (id != current.value.owner) return@withLock
        val shared = snapshot.takeIf { it.active && id != null }
        save(current.value.copy(sharing = shared?.copy(status = "")))
    }

    /** Called on delivery, foreground retry and Sync now. A retained DataItem is safe to replay. */
    suspend fun reconcile(force: Boolean = false, verifyProfile: Boolean = false) = mutex.withLock {
        alignAccount()
        val id = current.value.owner
        try {
            if (id != null && current.value.sharing != null) {
                val requests = transport.requests()
                val needsRefresh = force || verifyProfile || current.value.needsProfileCheck ||
                    current.value.inFlight != null || requests.any(current.value::accepts)
                if (needsRefresh) {
                    var row = checkNotNull(refresh(id))
                    for (request in requests) {
                        val state = current.value
                        if (!state.accepts(request)) continue
                        requireOwner(id)
                        save(state.copy(inFlight = request))
                        val updated = commitIfCurrent(id, request.status, row, request)
                        if (updated != null) {
                            row = updated
                            message.value = "Watch cue saved to your profile."
                        } else {
                            row = checkNotNull(refresh(id))
                            // A concurrent profile edit may only have changed the bio or visibility.
                            // Retry later if this choice is still eligible against the refreshed cue.
                            check(!current.value.accepts(request))
                        }
                    }
                    if (force && current.value.inFlight == null) message.value = "Profile cue confirmed."
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            alignAccount()
            message.value = "Cue sync pending. Check your connection and try Sync now."
            // Always deliver clears. If the server cannot confirm privacy, withhold the token.
            val state = current.value
            save(state.copy(serverAllowsSharing = false, needsProfileCheck = state.owner != null))
            transport.publish(current.value, force)
            throw e
        }
        alignAccount()
        try {
            transport.publish(current.value, force)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            message.value = "Waiting to send your profile cue to the watch."
            throw e
        }
    }

    companion object {
        @Volatile private var instance: PhoneCueSync? = null

        fun get(context: Context): PhoneCueSync = instance ?: synchronized(this) {
            instance ?: run {
                val app = context.applicationContext
                val prefs = app.getSharedPreferences("watch_publisher", Context.MODE_PRIVATE)
                val profiles = ProfileRepository()
                val backend = object : CueBackend {
                    override fun userId(): String? {
                        val auth = SupabaseClientProvider.client.auth
                        return when (auth.sessionStatus.value) {
                            is SessionStatus.Authenticated -> checkNotNull(auth.currentUserOrNull()?.id)
                            is SessionStatus.NotAuthenticated -> null
                            else -> error("Waiting for the phone session")
                        }
                    }
                    override suspend fun profile(id: String) = profiles.loadProfile(id)
                    override suspend fun write(id: String, cue: CueStatus, expectedUpdatedAt: String) =
                        profiles.updateStatus(id, cue, expectedUpdatedAt)
                }
                PhoneCueSync(prefs, backend, WearCueTransport(app, prefs)).also { instance = it }
            }
        }
    }
}
