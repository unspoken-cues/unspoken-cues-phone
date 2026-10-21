package com.example.unspokenqueues.model

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// Pure rules for when an event happens, how it is listed and how it is filtered. Kept free of
// Android types so they run as plain JVM unit tests.

/** Where an event sits in time, which decides which Events tab lists it. */
enum class EventPhase { UPCOMING, LIVE, PAST }

/** An event with no end time is treated as running this long after it starts. */
val DEFAULT_EVENT_LENGTH: Duration = Duration.ofHours(3)

/**
 * The end of the event. An event with a start but no end runs for [DEFAULT_EVENT_LENGTH]; one
 * with no start at all (made before events had times) has no end.
 */
fun Event.effectiveEnd(): Instant? = endsAt ?: startsAt?.plus(DEFAULT_EVENT_LENGTH)

/**
 * Whether the event is still to come, happening now, or over. A host ending an event makes it
 * past at once, whatever the clock says; an event with no time stays upcoming until it is ended.
 */
fun Event.phase(now: Instant): EventPhase = when {
    !active -> EventPhase.PAST
    startsAt == null -> EventPhase.UPCOMING
    now.isBefore(startsAt) -> EventPhase.UPCOMING
    now.isBefore(endsAt ?: startsAt.plus(DEFAULT_EVENT_LENGTH)) -> EventPhase.LIVE
    else -> EventPhase.PAST
}

/** Events on the Upcoming tab (live ones first, then soonest start), skipping past ones. */
fun upcomingEvents(events: List<Event>, now: Instant): List<Event> =
    events.filter { it.phase(now) != EventPhase.PAST }
        .sortedWith(
            compareBy<Event> { it.phase(now) != EventPhase.LIVE }
                .thenBy { it.startsAt ?: Instant.MAX },
        )

/** Events on the Past tab, most recent first. */
fun pastEvents(events: List<Event>, now: Instant): List<Event> =
    events.filter { it.phase(now) == EventPhase.PAST }
        .sortedByDescending { it.startsAt ?: Instant.MIN }

// ---------- Tags ----------

const val MAX_TAGS_PER_EVENT = 5
const val MAX_TAG_LENGTH = 20

/** A tag as stored: trimmed, lower-case, spaces and punctuation turned into single hyphens. */
fun normalizeTag(raw: String): String =
    raw.trim().lowercase(Locale.ROOT)
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')
        .take(MAX_TAG_LENGTH)
        .trim('-')

/** [raw] tags normalised, with blanks and repeats dropped, at most [MAX_TAGS_PER_EVENT]. */
fun normalizeTags(raw: List<String>): List<String> =
    raw.map(::normalizeTag).filter { it.isNotEmpty() }.distinct().take(MAX_TAGS_PER_EVENT)

/** Tags worth offering as filters, most used first (ties alphabetical). */
fun tagsOf(events: List<Event>): List<String> =
    events.flatMap { it.tags }.groupingBy { it }.eachCount().entries
        .sortedWith(compareBy({ -it.value }, { it.key })).map { it.key }

/** The events carrying every tag in [selected]; all of them when nothing is selected. */
fun filterByTags(events: List<Event>, selected: Set<String>): List<Event> =
    if (selected.isEmpty()) events else events.filter { it.tags.containsAll(selected) }

/** A tag as shown: hyphens back to spaces, e.g. "board-games" to "Board games". */
fun tagLabel(tag: String): String =
    tag.replace('-', ' ').replaceFirstChar { it.titlecase(Locale.getDefault()) }

// ---------- Dates for display ----------

private val timeFormat = DateTimeFormatter.ofPattern("h:mm a", Locale.US)
private val weekdayFormat = DateTimeFormatter.ofPattern("EEE", Locale.US)
private val monthDayFormat = DateTimeFormatter.ofPattern("MMM d", Locale.US)
private val longDayFormat = DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.US)

private fun LocalDate.relativeName(today: LocalDate): String? = when (this) {
    today -> "Today"
    today.plusDays(1) -> "Tomorrow"
    today.minusDays(1) -> "Yesterday"
    else -> null
}

/**
 * A short "when" for a list: "Today, 5:00 PM", "Tomorrow, 5:00 PM", "Mon, 3:00 PM" within the
 * coming week, otherwise "Oct 28, 5:00 PM". An event with no time reads "Time to be announced".
 */
fun formatWhen(event: Event, now: Instant, zone: ZoneId): String {
    val start = event.startsAt ?: return "Time to be announced"
    val day = start.atZone(zone).toLocalDate()
    val today = now.atZone(zone).toLocalDate()
    val time = timeFormat.format(start.atZone(zone))
    val daysAway = java.time.temporal.ChronoUnit.DAYS.between(today, day)
    val dayText = day.relativeName(today)
        ?: if (daysAway in 2..6) weekdayFormat.format(start.atZone(zone)) else monthDayFormat.format(start.atZone(zone))
    return "$dayText, $time"
}

/** The date line of an event page: "Wednesday, October 28". Blank when the event has no time. */
fun formatDay(event: Event, zone: ZoneId): String =
    event.startsAt?.let { longDayFormat.format(it.atZone(zone)) } ?: ""

/** The time line of an event page: "5:00 PM to 8:00 PM". Blank when the event has no time. */
fun formatTimeRange(event: Event, zone: ZoneId): String {
    val start = event.startsAt ?: return ""
    val from = timeFormat.format(start.atZone(zone))
    val end = event.endsAt ?: return from
    return "$from to ${timeFormat.format(end.atZone(zone))}"
}

/** The heading a list groups an event under: "Today", "Tomorrow", "Wed, Oct 28", or "Date to be announced". */
fun dayHeader(event: Event, now: Instant, zone: ZoneId): String {
    val start = event.startsAt ?: return "Date to be announced"
    val day = start.atZone(zone).toLocalDate()
    val today = now.atZone(zone).toLocalDate()
    return day.relativeName(today)
        ?: "${weekdayFormat.format(start.atZone(zone))}, ${monthDayFormat.format(start.atZone(zone))}"
}

/** "5 going", "1 going", or blank when nobody has said yes yet. */
fun goingText(count: Int): String = if (count <= 0) "" else "$count going"
