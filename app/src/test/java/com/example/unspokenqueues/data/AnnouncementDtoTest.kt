package com.example.unspokenqueues.data

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class AnnouncementDtoTest {
    @Test fun decodesServerRowAndKeepsPlainText() {
        val row = Json.decodeFromString<AnnouncementRow>("""{"id":"a","event_id":"e","body":"<b>Meet upstairs</b>","created_at":"2026-11-17T13:00:00-05:00","request_id":"request"}""")
        val announcement = row.toAnnouncement()
        assertEquals("e", announcement.eventId)
        assertEquals("<b>Meet upstairs</b>", announcement.body)
        assertEquals(Instant.parse("2026-11-17T18:00:00Z"), announcement.createdAt)
        assertEquals(row, Json.decodeFromString<AnnouncementRow>(Json.encodeToString(row)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun malformedPersistedTimestampCannotBecomeFalseSuccess() {
        AnnouncementRow("a", "e", "Hello", "not a timestamp", "r").toAnnouncement()
    }
}
