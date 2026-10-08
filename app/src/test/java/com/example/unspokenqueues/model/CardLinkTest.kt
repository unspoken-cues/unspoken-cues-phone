package com.example.unspokenqueues.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CardLinkTest {

    private val token = "0123456789abcdef0123456789abcdef"

    @Test
    fun cardLink_usesTheAppScheme() {
        assertEquals("unspokencues://card/$token", cardLink(token))
    }

    @Test
    fun link_roundTripsToItsToken() {
        assertEquals(token, cardTokenFrom(cardLink(token)))
    }

    @Test
    fun bareToken_isAccepted_ignoringSurroundingWhitespace() {
        assertEquals(token, cardTokenFrom(token))
        assertEquals(token, cardTokenFrom("  $token\n"))
    }

    @Test
    fun linkInsideAMessage_isFound() {
        val message = "Swap cards with me on Unspoken Cues. Open this link on your phone: ${cardLink(token)}\nSee you there!"
        assertEquals(token, cardTokenFrom(message))
    }

    @Test
    fun linkFollowedByPunctuation_isFound() {
        assertEquals(token, cardTokenFrom("Here: ${cardLink(token)}."))
    }

    @Test
    fun upperCase_isLoweredToMatchTheServer() {
        assertEquals(token, cardTokenFrom(cardLink(token).uppercase()))
        assertEquals(token, cardTokenFrom(token.uppercase()))
    }

    @Test
    fun wrongLengthToken_isRejected() {
        assertNull(cardTokenFrom(cardLink(token.dropLast(1))))
        assertNull(cardTokenFrom(cardLink(token + "0")))
        assertNull(cardTokenFrom(token.dropLast(1)))
        assertNull(cardTokenFrom(token + "0"))
    }

    @Test
    fun nonHexToken_isRejected() {
        assertNull(cardTokenFrom(cardLink("g".repeat(32))))
        assertNull(cardTokenFrom("z".repeat(32)))
    }

    @Test
    fun otherText_isRejected() {
        assertNull(cardTokenFrom(""))
        assertNull(cardTokenFrom("   "))
        assertNull(cardTokenFrom("MIXER1"))
        assertNull(cardTokenFrom("https://example.com/card/$token"))
        assertNull(cardTokenFrom("unspokencues://event/$token"))
    }

    @Test
    fun bareToken_mustBeTheWholeText() {
        // Any 32 hex digits buried in unrelated text are not a card: only a link is trusted there.
        assertNull(cardTokenFrom("order number $token shipped"))
    }
}
