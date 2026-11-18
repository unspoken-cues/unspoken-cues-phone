package com.example.unspokenqueues.data

import com.example.unspokenqueues.model.Attendee
import com.example.unspokenqueues.model.CueStatus
import com.example.unspokenqueues.model.Event
import com.example.unspokenqueues.model.EventProfile
import com.example.unspokenqueues.model.Profile
import com.example.unspokenqueues.model.eventProfileOf
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

/**
 * One row of `event_attendees()`: an attendee as the viewer may see them. Fields they hide arrive
 * blank, and status arrives null when they hide their cue color.
 */
@Serializable
data class AttendeeRow(
    @SerialName("user_id") val userId: String,
    @SerialName("display_name") val displayName: String = "",
    @SerialName("avatar_url") val avatarUrl: String = "",
    val bio: String = "",
    val preferences: List<String> = emptyList(),
    val boundaries: List<String> = emptyList(),
    val status: String? = null,
    // Missing from a server without the event directory migration, where everyone is listed.
    @SerialName("cued_in") val cuedIn: Boolean = true,
)

/** The answer of `my_event_profile()` / `set_event_profile()`: the caller's override, null where unset. */
@Serializable
data class EventProfileRow(
    @SerialName("display_name") val displayName: String? = null,
    val bio: String? = null,
    val status: String? = null,
)

/** A status the app doesn't know reads as unset, so the main cue applies; the server decides waves anyway. */
fun EventProfileRow.toEventProfile(): EventProfile =
    eventProfileOf(displayName.orEmpty(), bio.orEmpty(), CueStatus.entries.firstOrNull { it.name == status })

/** An attendee row as an attendee. A status the app doesn't know reads as hidden. */
fun AttendeeRow.toAttendee(): Attendee = Attendee(
    userId = userId,
    // isPublic isn't sent: someone else's visibility setting is none of the viewer's business.
    profile = Profile(displayName, bio, preferences, boundaries, isPublic = false, avatarUrl = avatarUrl),
    status = CueStatus.entries.firstOrNull { it.name == status },
    cuedIn = cuedIn,
)
