package com.example.unspokenqueues.model

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

const val MAX_ANNOUNCEMENT_LENGTH = 1000
const val ANNOUNCEMENT_FEED_LIMIT = 50

/** Host updates contain plain text only; no profile fields are fetched for the feed. */
data class Announcement(
    val id: String,
    val eventId: String,
    val body: String,
    val createdAt: Instant,
)

/** Count Unicode characters, as PostgreSQL char_length does, rather than UTF-16 code units. */
fun announcementLength(body: String): Int = body.codePointCount(0, body.length)

fun validAnnouncement(body: String): Boolean =
    body.trim().let { it.isNotEmpty() && announcementLength(it) <= MAX_ANNOUNCEMENT_LENGTH }

fun Event.canPostAnnouncement(userId: String, now: Instant): Boolean =
    userId.isNotBlank() && hostId == userId && active && effectiveEnd()?.let { now.isBefore(it) } != false

/** Stable newest-first ordering is shared by refreshes and acknowledged posts. */
fun announcementFeed(rows: List<Announcement>): List<Announcement> =
    rows.distinctBy { it.id }
        .sortedWith(compareByDescending<Announcement> { it.createdAt }.thenByDescending { it.id })
        .take(ANNOUNCEMENT_FEED_LIMIT)

fun formatAnnouncementTime(
    instant: Instant,
    zone: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault(),
): String = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
    .withLocale(locale).withZone(zone).format(instant)
