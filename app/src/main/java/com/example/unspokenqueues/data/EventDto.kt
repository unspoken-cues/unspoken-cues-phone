package com.example.unspokenqueues.data

import com.example.unspokenqueues.model.Attendee
import com.example.unspokenqueues.model.Event
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Row shape of the `public.events` table. */
@Serializable
data class EventRow(
    val id: String,
    @SerialName("host_id") val hostId: String,
    val name: String = "",
    val details: String = "",
    @SerialName("join_code") val joinCode: String = "",
    val active: Boolean = true,
)

/** Row shape of the `public.event_members` table: one row per person who joined an event. */
@Serializable
data class EventMemberRow(
    @SerialName("event_id") val eventId: String,
    @SerialName("user_id") val userId: String,
    // Set by the server; blank when the row has not been read back yet.
    @SerialName("joined_at") val joinedAt: String = "",
)

// ---------- Mappers ----------

fun EventRow.toEvent(): Event = Event(
    id = id,
    hostId = hostId,
    name = name,
    details = details,
    joinCode = joinCode,
    active = active,
)

/** An event member's profile row as an attendee; the profile row's id is the member's user id. */
fun ProfileRow.toAttendee(): Attendee = Attendee(
    userId = id,
    profile = toProfile(),
    status = toCueStatus(),
)

/**
 * Joins membership rows to the members' profiles, keeping the order people joined in.
 * Members whose profile could not be read (e.g. hidden by Row Level Security) are skipped.
 */
fun List<EventMemberRow>.toAttendees(profiles: List<ProfileRow>): List<Attendee> {
    val byId = profiles.associateBy { it.id }
    return mapNotNull { byId[it.userId]?.toAttendee() }
}
