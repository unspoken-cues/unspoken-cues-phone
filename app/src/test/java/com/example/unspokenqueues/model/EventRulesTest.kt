package com.example.unspokenqueues.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EventRulesTest {

    private fun attendee(id: String, status: CueStatus) =
        Attendee(id, Profile(id, "", emptyList(), emptyList(), true), status)

    private val green = attendee("green", CueStatus.GREEN)
    private val yellow = attendee("yellow", CueStatus.YELLOW)
    private val red = attendee("red", CueStatus.RED)
    private val purple = attendee("purple", CueStatus.PURPLE)
    private val everyone = listOf(green, yellow, red, purple)

    // ---------- visibleAttendees ----------

    @Test
    fun host_seesEveryone() {
        assertEquals(everyone, visibleAttendees(viewerId = "host", hostId = "host", attendees = everyone))
    }

    @Test
    fun hostWhoIsAlsoAnAttendee_seesEveryone() {
        val withHost = everyone + attendee("host", CueStatus.RED)
        assertEquals(withHost, visibleAttendees("host", "host", withHost))
    }

    @Test
    fun attendee_seesOnlyGreenAndYellow() {
        assertEquals(listOf(green, yellow), visibleAttendees("green", "host", everyone))
    }

    @Test
    fun redAttendee_seesGreenYellowAndThemselves() {
        assertEquals(listOf(green, yellow, red), visibleAttendees("red", "host", everyone))
    }

    @Test
    fun purpleAttendee_seesGreenYellowAndThemselves_butNotRed() {
        assertEquals(listOf(green, yellow, purple), visibleAttendees("purple", "host", everyone))
    }

    @Test
    fun viewerWhoIsNotAttending_seesOnlyGreenAndYellow() {
        assertEquals(listOf(green, yellow), visibleAttendees("stranger", "host", everyone))
    }

    @Test
    fun emptyEvent_isEmptyForEveryone() {
        assertEquals(emptyList<Attendee>(), visibleAttendees("host", "host", emptyList()))
        assertEquals(emptyList<Attendee>(), visibleAttendees("green", "host", emptyList()))
    }

    // ---------- isValidJoinCode ----------

    @Test
    fun joinCode_acceptsLettersAndDigitsWithinLength() {
        assertTrue(isValidJoinCode("MIXER1"))
        assertTrue(isValidJoinCode("ABCD"))
        assertTrue(isValidJoinCode("ABCD1234"))
        assertTrue(isValidJoinCode("1234"))
    }

    @Test
    fun joinCode_isCaseInsensitive_andIgnoresSurroundingWhitespace() {
        assertTrue(isValidJoinCode("mixer1"))
        assertTrue(isValidJoinCode("  MIXER1\n"))
    }

    @Test
    fun joinCode_rejectsWrongLength() {
        assertFalse(isValidJoinCode(""))
        assertFalse(isValidJoinCode("   "))
        assertFalse(isValidJoinCode("ABC"))
        assertFalse(isValidJoinCode("ABCD12345"))
    }

    @Test
    fun joinCode_rejectsOtherCharacters() {
        assertFalse(isValidJoinCode("MIX-ER"))
        assertFalse(isValidJoinCode("MIX ER"))
        assertFalse(isValidJoinCode("MIXER!"))
        assertFalse(isValidJoinCode("MIXÉR1"))
        assertFalse(isValidJoinCode("١٢٣٤"))
    }
}
