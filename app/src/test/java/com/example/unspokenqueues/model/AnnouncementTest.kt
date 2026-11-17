package com.example.unspokenqueues.model

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

class AnnouncementTest {
    private val now = Instant.parse("2026-11-17T18:00:00Z")
    private val event = Event("event", "host", "Mixer", "", "MIXER2", true)

    @Test fun validatesTrimmedTextAndUnicodeCharacterLimit() {
        assertFalse(validAnnouncement(" \t\n\u00a0\u2007\u202f\u3000"))
        assertTrue(validAnnouncement(" \nHello\t "))
        assertTrue(validAnnouncement("x".repeat(1000)))
        assertFalse(validAnnouncement("x".repeat(1001)))
        assertTrue(validAnnouncement("🎉".repeat(1000)))
        assertEquals(1000, announcementLength("🎉".repeat(1000)))
        assertFalse(validAnnouncement("🎉".repeat(1001)))
    }

    @Test fun onlyCurrentHostCanPostBeforeEffectiveEnd() {
        assertTrue(event.canPostAnnouncement("host", now))
        assertFalse(event.canPostAnnouncement("member", now))
        assertFalse(event.canPostAnnouncement("", now))
        assertFalse(event.copy(active = false).canPostAnnouncement("host", now))
        assertFalse(event.copy(endsAt = now).canPostAnnouncement("host", now))
        assertFalse(event.copy(endsAt = now.minusSeconds(1)).canPostAnnouncement("host", now))
        assertTrue(event.copy(endsAt = now.plusSeconds(1)).canPostAnnouncement("host", now))
        assertFalse(event.copy(startsAt = now.minusSeconds(10800)).canPostAnnouncement("host", now))
        assertTrue(event.copy(startsAt = now.minusSeconds(10799)).canPostAnnouncement("host", now))
    }

    @Test fun feedDeduplicatesAndUsesIdToBreakTimestampTiesBeforeLimiting() {
        val rows = (1..60).map { Announcement("%03d".format(it), "event", "$it", now) }
        val feed = announcementFeed(rows + rows.take(2))
        assertEquals(50, feed.size)
        assertEquals("060", feed.first().id)
        assertEquals("011", feed.last().id)
        assertEquals("older-id", announcementFeed(rows + Announcement("older-id", "event", "new", now.plusSeconds(1))).first().id)
    }

    @Test fun timestampUsesRequestedLocaleAndTimezone() {
        val utc = formatAnnouncementTime(now, ZoneId.of("UTC"), Locale.US)
        val newYork = formatAnnouncementTime(now, ZoneId.of("America/New_York"), Locale.US)
        val french = formatAnnouncementTime(now, ZoneId.of("UTC"), Locale.FRANCE)
        assertNotEquals(utc, newYork)
        assertNotEquals(utc, french)
        assertTrue(utc.contains("2026"))
    }
}
