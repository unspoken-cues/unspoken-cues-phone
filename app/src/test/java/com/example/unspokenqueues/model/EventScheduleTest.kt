package com.example.unspokenqueues.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

class EventScheduleTest {

    private val zone: ZoneId = ZoneOffset.UTC
    // Wednesday 21 October 2026, noon UTC.
    private val now: Instant = Instant.parse("2026-10-21T12:00:00Z")

    private fun event(
        id: String = "e",
        startsAt: String? = null,
        endsAt: String? = null,
        active: Boolean = true,
        tags: List<String> = emptyList(),
    ) = Event(
        id, "host", "Event $id", "", "CODE12", active,
        startsAt = startsAt?.let(Instant::parse), endsAt = endsAt?.let(Instant::parse), tags = tags,
    )

    // ---------- phase ----------

    @Test
    fun futureEvent_isUpcoming() {
        assertEquals(EventPhase.UPCOMING, event(startsAt = "2026-10-21T17:00:00Z").phase(now))
    }

    @Test
    fun eventThatHasStartedAndNotEnded_isLive() {
        assertEquals(EventPhase.LIVE, event(startsAt = "2026-10-21T11:00:00Z", endsAt = "2026-10-21T14:00:00Z").phase(now))
    }

    @Test
    fun eventWithNoEnd_runsThreeHours() {
        assertEquals(EventPhase.LIVE, event(startsAt = "2026-10-21T10:00:00Z").phase(now))
        assertEquals(EventPhase.PAST, event(startsAt = "2026-10-21T08:59:00Z").phase(now))
    }

    @Test
    fun finishedEvent_isPast() {
        assertEquals(EventPhase.PAST, event(startsAt = "2026-10-20T17:00:00Z", endsAt = "2026-10-20T19:00:00Z").phase(now))
    }

    @Test
    fun endedByTheHost_isPastEvenIfItIsStillInTheFuture() {
        assertEquals(EventPhase.PAST, event(startsAt = "2026-10-30T17:00:00Z", active = false).phase(now))
    }

    @Test
    fun eventWithNoTime_staysUpcomingUntilEnded() {
        assertEquals(EventPhase.UPCOMING, event().phase(now))
        assertEquals(EventPhase.PAST, event(active = false).phase(now))
    }

    // ---------- upcoming and past lists ----------

    @Test
    fun upcomingEvents_putsLiveFirst_thenSoonestStart_andDropsPast() {
        val later = event("later", startsAt = "2026-10-25T17:00:00Z")
        val soon = event("soon", startsAt = "2026-10-22T17:00:00Z")
        val live = event("live", startsAt = "2026-10-21T11:00:00Z")
        val over = event("over", startsAt = "2026-10-01T17:00:00Z")
        assertEquals(listOf(live, soon, later), upcomingEvents(listOf(later, over, soon, live), now))
    }

    @Test
    fun pastEvents_listsMostRecentFirst() {
        val old = event("old", startsAt = "2026-09-01T17:00:00Z")
        val recent = event("recent", startsAt = "2026-10-20T17:00:00Z")
        val future = event("future", startsAt = "2026-10-30T17:00:00Z")
        assertEquals(listOf(recent, old), pastEvents(listOf(old, future, recent), now))
    }

    // ---------- tags ----------

    @Test
    fun normalizeTag_lowercasesAndHyphenates() {
        assertEquals("board-games", normalizeTag("  Board Games! "))
        assertEquals("ai-ml", normalizeTag("AI / ML"))
        assertEquals("", normalizeTag("   "))
    }

    @Test
    fun normalizeTags_dropsBlanksAndRepeats_andCapsTheCount() {
        assertEquals(listOf("a", "b"), normalizeTags(listOf("A", "", "b", "a")))
        assertEquals(MAX_TAGS_PER_EVENT, normalizeTags((1..9).map { "tag$it" }).size)
    }

    @Test
    fun tagsOf_ordersByUseThenName() {
        val events = listOf(
            event("1", tags = listOf("food", "music")),
            event("2", tags = listOf("music")),
            event("3", tags = listOf("arts")),
        )
        assertEquals(listOf("music", "arts", "food"), tagsOf(events))
    }

    @Test
    fun filterByTags_needsEverySelectedTag_andPassesAllWhenNoneSelected() {
        val a = event("a", tags = listOf("food", "music"))
        val b = event("b", tags = listOf("music"))
        assertEquals(listOf(a, b), filterByTags(listOf(a, b), emptySet()))
        assertEquals(listOf(a, b), filterByTags(listOf(a, b), setOf("music")))
        assertEquals(listOf(a), filterByTags(listOf(a, b), setOf("music", "food")))
    }

    @Test
    fun tagLabel_turnsHyphensIntoSpaces() {
        assertEquals("Board games", tagLabel("board-games"))
    }

    // ---------- dates ----------

    @Test
    fun formatWhen_usesTodayAndTomorrow_thenWeekday_thenDate() {
        assertEquals("Today, 5:00 PM", formatWhen(event(startsAt = "2026-10-21T17:00:00Z"), now, zone))
        assertEquals("Tomorrow, 9:30 AM", formatWhen(event(startsAt = "2026-10-22T09:30:00Z"), now, zone))
        assertEquals("Mon, 3:00 PM", formatWhen(event(startsAt = "2026-10-26T15:00:00Z"), now, zone))
        assertEquals("Oct 28, 5:00 PM", formatWhen(event(startsAt = "2026-10-28T17:00:00Z"), now, zone))
    }

    @Test
    fun formatWhen_withNoTime_saysSo() {
        assertEquals("Time to be announced", formatWhen(event(), now, zone))
    }

    @Test
    fun formatWhen_followsTheViewersTimeZone() {
        // 02:00 UTC on the 22nd is still the 21st evening in Los Angeles.
        val la = ZoneId.of("America/Los_Angeles")
        assertEquals("Today, 7:00 PM", formatWhen(event(startsAt = "2026-10-22T02:00:00Z"), now, la))
    }

    @Test
    fun dayHeader_groupsByDay() {
        assertEquals("Today", dayHeader(event(startsAt = "2026-10-21T20:00:00Z"), now, zone))
        assertEquals("Tomorrow", dayHeader(event(startsAt = "2026-10-22T20:00:00Z"), now, zone))
        assertEquals("Wed, Oct 28", dayHeader(event(startsAt = "2026-10-28T20:00:00Z"), now, zone))
        assertEquals("Date to be announced", dayHeader(event(), now, zone))
    }

    @Test
    fun formatDayAndTimeRange_forTheEventPage() {
        val e = event(startsAt = "2026-10-28T17:00:00Z", endsAt = "2026-10-28T20:00:00Z")
        assertEquals("Wednesday, October 28", formatDay(e, zone))
        assertEquals("5:00 PM to 8:00 PM", formatTimeRange(e, zone))
        assertEquals("5:00 PM", formatTimeRange(event(startsAt = "2026-10-28T17:00:00Z"), zone))
        assertEquals("", formatDay(event(), zone))
    }

    @Test
    fun goingText_isBlankForZero() {
        assertEquals("", goingText(0))
        assertEquals("1 going", goingText(1))
        assertTrue(goingText(42).endsWith("going"))
    }

    @Test
    fun rsvp_roundTripsItsWireName() {
        Rsvp.entries.forEach { assertEquals(it, Rsvp.fromWire(it.wire)) }
        assertEquals(null, Rsvp.fromWire("declined"))
    }
}
