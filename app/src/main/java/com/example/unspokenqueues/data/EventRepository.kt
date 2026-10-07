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
 * Creates, lists, joins and ends events in the `events` / `event_members` tables.
 *
 * Row Level Security decides what each call can touch: a user only ever sees events they host or
 * have joined, and only the host can change an event.
 */
class EventRepository(
    client: io.github.jan.supabase.SupabaseClient? = null,
) {
    private val postgrest by lazy { (client ?: SupabaseClientProvider.client).postgrest }

    /** Creates an event hosted by [hostId] with a freshly generated join code. */
    suspend fun createEvent(hostId: String, name: String, details: String): Event {
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
    suspend fun myEvents(userId: String): List<Event> =
        postgrest.from(EVENTS)
            .select { order("created_at", Order.DESCENDING) }
            .decodeList<EventRow>()
            .map { it.toEvent() }

    /** Everyone who has joined the event, in the order they joined. */
    suspend fun eventAttendees(eventId: String): List<Attendee> {
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
     * Joins the active event with this code as the signed-in user and returns it. Throws if no
     * active event has that code. Joining an event twice is harmless.
     */
    suspend fun joinByCode(code: String): Event =
        postgrest.rpc(JOIN_RPC, buildJsonObject { put("code", code.trim()) })
            .decodeAs<EventRow>()
            .toEvent()

    /** Ends an event so nobody else can join it. Only has an effect for the host. */
    suspend fun endEvent(eventId: String) {
        postgrest.from(EVENTS).update(mapOf("active" to false)) {
            filter { eq("id", eventId) }
        }
    }

    private companion object {
        const val EVENTS = "events"
        const val MEMBERS = "event_members"
        const val PROFILES = "profiles"
        const val JOIN_RPC = "join_event_by_code"
        const val CODE_ATTEMPTS = 5
        // Postgres error code for a unique-constraint violation.
        const val UNIQUE_VIOLATION = "23505"
    }
}
