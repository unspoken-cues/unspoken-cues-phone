package com.example.unspokenqueues.data

import com.example.unspokenqueues.model.Rsvp
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class EventDtoTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun eventRow_decodesTheNewColumns() {
        val row = json.decodeFromString<EventRow>(
            """{"id":"e1","host_id":"h1","name":"Arduino night","details":"Bring a laptop","join_code":"ARD123",
               "active":true,"starts_at":"2026-10-28T21:00:00+00:00","ends_at":"2026-10-29T00:00:00+00:00",
               "location":"CASE 138","cover_url":"https://x/y.jpg","tags":["tech","study"],"is_public":true,
               "host_name":"INIT FIU","going_count":12}""",
        )
        val event = row.toEvent()
        assertEquals(Instant.parse("2026-10-28T21:00:00Z"), event.startsAt)
        assertEquals(Instant.parse("2026-10-29T00:00:00Z"), event.endsAt)
        assertEquals("CASE 138", event.location)
        assertEquals("https://x/y.jpg", event.coverUrl)
        assertEquals(listOf("tech", "study"), event.tags)
        assertEquals(true, event.isPublic)
        assertEquals("INIT FIU", event.hostName)
        assertEquals(12, event.goingCount)
    }

    @Test
    fun eventRow_fromBeforeEventPages_stillReadsAsAnEventWithNoTime() {
        val event = json.decodeFromString<EventRow>(
            """{"id":"e1","host_id":"h1","name":"Mixer","details":"7 PM","join_code":"MIXER1","active":true}""",
        ).toEvent()
        assertNull(event.startsAt)
        assertEquals(emptyList<String>(), event.tags)
        assertEquals(false, event.isPublic)
        assertEquals(0, event.goingCount)
    }

    @Test
    fun toInstantOrNull_readsServerTimestamps_andShrugsAtGarbage() {
        assertEquals(Instant.parse("2026-10-28T21:00:00Z"), "2026-10-28T17:00:00-04:00".toInstantOrNull())
        assertEquals(Instant.parse("2026-10-28T21:00:00Z"), "2026-10-28T21:00:00Z".toInstantOrNull())
        assertNull("".toInstantOrNull())
        assertNull(null.toInstantOrNull())
        assertNull("soon".toInstantOrNull())
    }

    @Test
    fun memberRows_becomeRsvps_andRowsFromBeforeRsvpsReadAsGoing() {
        val rows = json.decodeFromString<List<EventMemberRow>>(
            """[{"event_id":"a","user_id":"u","rsvp":"maybe"},{"event_id":"b","user_id":"u"},{"event_id":"c","user_id":"u","rsvp":"declined"}]""",
        )
        assertEquals(mapOf("a" to Rsvp.MAYBE, "b" to Rsvp.GOING), rows.toRsvps())
    }
}
