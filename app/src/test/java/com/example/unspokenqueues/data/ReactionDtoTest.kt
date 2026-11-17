package com.example.unspokenqueues.data

import com.example.unspokenqueues.model.withLocalEligibility
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class ReactionDtoTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val recipient = WaveRecipientRow("host", "Host", false)
    private val wave = ReactionRow("sender", "Sender", "2026-11-17T17:00:00-05:00")

    @Test
    fun serverSnapshot_decodesConsentRecipientAndPrivateTimestamp() {
        val state = json.decodeFromString<ReactionStateRow>(
            """{"receive_waves":true,"can_wave":true,"recipients":[{"user_id":"host","display_name":"Host","sent":true}],"inbox":[{"sender_id":"member","sender_name":"Mae","created_at":"2026-11-17T17:00:00-05:00"}]}""",
        ).toReactionState()
        assertTrue(state.canWave)
        assertTrue(state.recipients.single().sent)
        assertEquals("Mae", state.inbox.single().senderName)
        assertEquals(Instant.parse("2026-11-17T22:00:00Z"), state.inbox.single().createdAt)
    }

    @Test(expected = SerializationException::class)
    fun unknownEligibility_isNotAssumedEnabled() {
        json.decodeFromString<ReactionStateRow>("""{"receive_waves":true,"recipients":[],"inbox":[]}""")
    }

    @Test
    fun optOutOrDeniedEligibility_discardsHistoryEvenWithInconsistentPayload() {
        listOf(false to true, true to false, false to false).forEach { (opted, allowed) ->
            val state = ReactionStateRow(opted, allowed, listOf(recipient), listOf(wave)).toReactionState()
            assertFalse(state.canWave)
            assertTrue(state.recipients.isEmpty())
            assertTrue(state.inbox.isEmpty())
        }
    }

    @Test
    fun invalidHistoryIsDropped_andInboxSortsNewestFirst() {
        val state = ReactionStateRow(true, true, listOf(recipient, recipient, recipient.copy(userId = "")), listOf(
            wave.copy(createdAt = "bad"), wave.copy(senderId = ""), wave,
            wave.copy(senderId = "new", createdAt = "2026-11-17T23:00:00Z"),
        )).toReactionState()
        assertEquals(1, state.recipients.size)
        assertEquals(listOf("new", "sender"), state.inbox.map { it.senderId })
    }

    @Test
    fun localRedPurpleOrExpiryHidesHistory_withoutErasingThePersistedSetting() {
        val state = ReactionStateRow(true, true, listOf(recipient), listOf(wave)).toReactionState()
        val hidden = state.withLocalEligibility(false)
        assertTrue(hidden.receiveWaves)
        assertFalse(hidden.canWave)
        assertTrue(hidden.inbox.isEmpty())
        assertTrue(hidden.recipients.isEmpty())
        assertEquals(state, state.withLocalEligibility(true))
    }
}
