package com.example.unspokenqueues.data

import com.example.unspokenqueues.model.Attendee
import com.example.unspokenqueues.model.Event
import com.example.unspokenqueues.model.generateJoinCode
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Payload for creating an event. The id, active flag and timestamps are set by the server. */
@Serializable
data class EventInsert(
    @SerialName("host_id") val hostId: String,
    val name: String,
    val details: String,
    @SerialName("join_code") val joinCode: String,
)

/**
 * What to tell the user when joining an event failed. [sqlState] is the Postgres error code the
 * server raised, or null when the failure wasn't a server answer (e.g. no connection).
 */
fun joinFailureMessage(sqlState: String?): String = when (sqlState) {
    EventRepository.NO_SUCH_EVENT -> "No active event has that code. Check it with the host; the event may have ended."
    else -> "Couldn't join the event. Check your connection and try again."
}

/**
 * Creates, lists, joins and ends events in the `events` / `event_members` tables.
 *
 * Row Level Security decides what each call can touch: a user only ever sees events they host or
 * have joined, and only the host can change an event.
 *
 * Open so UI tests can swap in a fake that never talks to the server.
 */
open class EventRepository(
    client: io.github.jan.supabase.SupabaseClient? = null,
) {
    private val postgrest by lazy { (client ?: SupabaseClientProvider.client).postgrest }

    /** Creates an event hosted by [hostId] with a freshly generated join code. */
    open suspend fun createEvent(hostId: String, name: String, details: String): Event {
        // Join codes are random, so two events can collide; the unique constraint rejects the
        // insert and we try again with a new code.
        repeat(CODE_ATTEMPTS - 1) {
            try {
                return insertEvent(hostId, name, details)
            } catch (e: PostgrestRestException) {
                if (e.code != UNIQUE_VIOLATION) throw e
            }
        }
        return insertEvent(hostId, name, details)
    }

    private suspend fun insertEvent(hostId: String, name: String, details: String): Event =
        postgrest.from(EVENTS)
            .insert(EventInsert(hostId, name.trim(), details.trim(), generateJoinCode())) { select() }
            .decodeSingle<EventRow>()
            .toEvent()

    /**
     * Events the user hosts or has joined, newest first. Row Level Security already limits the
     * `events` table to exactly those rows for the signed-in user, so no filter is sent.
     */
    @Suppress("UNUSED_PARAMETER")
    open suspend fun myEvents(userId: String): List<Event> =
        postgrest.from(EVENTS)
            .select { order("created_at", Order.DESCENDING) }
            .decodeList<EventRow>()
            .map { it.toEvent() }

    /** Everyone who has joined the event, in the order they joined. */
    open suspend fun eventAttendees(eventId: String): List<Attendee> {
        val members = postgrest.from(MEMBERS)
            .select {
                filter { eq("event_id", eventId) }
                order("joined_at", Order.ASCENDING)
            }
            .decodeList<EventMemberRow>()
        if (members.isEmpty()) return emptyList()
        val profiles = postgrest.from(PROFILES)
            .select { filter { isIn("id", members.map { it.userId }) } }
            .decodeList<ProfileRow>()
        return members.toAttendees(profiles)
    }

    /**
     * Joins the active event with this code as the signed-in user and returns it. Throws a
     * PostgrestRestException whose code [joinFailureMessage] understands if no active event has
     * that code. Joining an event twice is harmless.
     */
    open suspend fun joinByCode(code: String): Event =
        postgrest.rpc(JOIN_RPC, buildJsonObject { put("code", code.trim()) })
            .decodeAs<EventRow>()
            .toEvent()

    /** Ends an event so nobody else can join it. Only has an effect for the host. */
    open suspend fun endEvent(eventId: String) {
        postgrest.from(EVENTS).update(mapOf("active" to false)) {
            filter { eq("id", eventId) }
        }
    }

    /**
     * Takes [userId] (the signed-in user) off an event they joined, so it leaves their events.
     * Throws if nothing was removed.
     */
    open suspend fun leaveEvent(eventId: String, userId: String) {
        // Row Level Security turns a delete that isn't allowed into one that matches no rows, so
        // the removed rows are read back to tell a real removal from a silent no-op.
        val removed = postgrest.from(MEMBERS)
            .delete {
                select()
                filter {
                    eq("event_id", eventId)
                    eq("user_id", userId)
                }
            }
            .decodeList<EventMemberRow>()
        check(removed.isNotEmpty()) { "Not a member of event $eventId" }
    }

    /**
     * Deletes an event for everyone: the host and all who joined. Only the host can; throws if
     * nothing was deleted.
     */
    open suspend fun deleteEvent(eventId: String) {
        val removed = postgrest.from(EVENTS)
            .delete {
                select()
                filter { eq("id", eventId) }
            }
            .decodeList<EventRow>()
        check(removed.isNotEmpty()) { "No event $eventId to delete" }
    }

    companion object {
        private const val EVENTS = "events"
        private const val MEMBERS = "event_members"
        private const val PROFILES = "profiles"
        private const val JOIN_RPC = "join_event_by_code"
        private const val CODE_ATTEMPTS = 5
        // Postgres error code for a unique-constraint violation.
        private const val UNIQUE_VIOLATION = "23505"

        // Error code raised by join_event_by_code() when no active event has the code.
        const val NO_SUCH_EVENT = "P0002"
    }
}
