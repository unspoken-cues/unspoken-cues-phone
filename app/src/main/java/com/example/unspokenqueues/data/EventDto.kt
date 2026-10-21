package com.example.unspokenqueues.data

import com.example.unspokenqueues.model.Attendee
import com.example.unspokenqueues.model.Event
import com.example.unspokenqueues.model.Rsvp
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.OffsetDateTime

/** Row shape of the `public.events` table. */
@Serializable
data class EventRow(
    val id: String,
    @SerialName("host_id") val hostId: String,
    val name: String = "",
    val details: String = "",
    @SerialName("join_code") val joinCode: String = "",
    val active: Boolean = true,
    @SerialName("starts_at") val startsAt: String? = null,
    @SerialName("ends_at") val endsAt: String? = null,
    val location: String = "",
    @SerialName("cover_url") val coverUrl: String = "",
    val tags: List<String> = emptyList(),
    @SerialName("is_public") val isPublic: Boolean = false,
    @SerialName("host_name") val hostName: String = "",
    @SerialName("going_count") val goingCount: Int = 0,
)

/** Row shape of the `public.event_members` table: one row per person who joined an event. */
@Serializable
data class EventMemberRow(
    @SerialName("event_id") val eventId: String,
    @SerialName("user_id") val userId: String,
    // Set by the server; blank when the row has not been read back yet.
    @SerialName("joined_at") val joinedAt: String = "",
    // "going" or "maybe"; rows from before RSVPs existed read as going.
    val rsvp: String = "going",
)

// ---------- Mappers ----------

fun EventRow.toEvent(): Event = Event(
    id = id,
    hostId = hostId,
    name = name,
    details = details,
    joinCode = joinCode,
    active = active,
    startsAt = startsAt.toInstantOrNull(),
    endsAt = endsAt.toInstantOrNull(),
    location = location,
    coverUrl = coverUrl,
    tags = tags,
    isPublic = isPublic,
    hostName = hostName,
    goingCount = goingCount,
)

/** A timestamp as the server writes it ("2026-10-28T21:00:00+00:00"), or null if absent or unreadable. */
fun String?.toInstantOrNull(): Instant? =
    this?.takeIf { it.isNotBlank() }?.let { runCatching { OffsetDateTime.parse(it).toInstant() }.getOrNull() }

/** The RSVPs in these member rows by event id; rows with an answer the app doesn't know are skipped. */
fun List<EventMemberRow>.toRsvps(): Map<String, Rsvp> =
    mapNotNull { row -> Rsvp.fromWire(row.rsvp)?.let { row.eventId to it } }.toMap()

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
