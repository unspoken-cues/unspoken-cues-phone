package com.example.unspokenqueues.data

import com.example.unspokenqueues.model.CueStatus
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class SwapHelpersTest {

    private val json = Json { ignoreUnknownKeys = true }

    // ---------- toCollectedCard ----------

    @Test
    fun profileRow_toCollectedCard_usesRowIdAsUserId() {
        val card = ProfileRow(id = "u1", displayName = "Sam", status = "YELLOW", avatarUrl = "https://x.test/a.jpg").toCollectedCard()
        assertEquals("u1", card.userId)
        assertEquals("Sam", card.name)
        assertEquals(CueStatus.YELLOW, card.status)
        assertEquals("https://x.test/a.jpg", card.profile.avatarUrl)
    }

    @Test
    fun swapRpcReply_decodesToACard() {
        // swap_by_card_token() returns the whole profiles row, server columns included.
        val row = json.decodeFromString<ProfileRow>(
            """{"id":"u1","display_name":"Sam","bio":"hi","preferences":["Text first"],"boundaries":[],"is_public":false,
               "status":"PURPLE","created_at":"2026-10-07T20:00:00Z","updated_at":"2026-10-07T20:00:00Z","avatar_url":""}""",
        )
        val card = row.toCollectedCard()
        assertEquals("u1", card.userId)
        assertEquals(listOf("Text first"), card.profile.preferences)
        assertEquals(false, card.profile.isPublic)
        assertEquals(CueStatus.PURPLE, card.status)
    }

    // ---------- swapFailureMessage ----------

    @Test
    fun unknownCard_andOwnCard_getTheirOwnMessages() {
        val unknown = swapFailureMessage(SwapRepository.NO_SUCH_CARD)
        val own = swapFailureMessage(SwapRepository.OWN_CARD)
        val other = swapFailureMessage(null)
        assertEquals("That's your own card.", own)
        assertNotEquals(unknown, own)
        assertNotEquals(unknown, other)
        assertNotEquals(own, other)
    }

    @Test
    fun anyOtherFailure_isTreatedAsAConnectionProblem() {
        val generic = swapFailureMessage(null)
        assertEquals(generic, swapFailureMessage("28000"))
        assertEquals(generic, swapFailureMessage("PGRST202"))
        assertEquals(generic, swapFailureMessage(""))
    }
}
