package com.example.unspokenqueues.data

import com.example.unspokenqueues.model.BlockedUser
import com.example.unspokenqueues.model.ReportReason
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class SafetyHelpersTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun reportReasons_matchTheReasonsTheServerAccepts() {
        // The check constraint on reports.reason in supabase/schema.sql (section 16).
        assertEquals(
            listOf("harassment", "unsafe", "spam", "fake", "other"),
            ReportReason.entries.map { it.wire },
        )
    }

    @Test
    fun reportInsert_encodesSnakeCase_andANullEvent() {
        val encoded = json.encodeToString(ReportInsert.serializer(), ReportInsert("u2", ReportReason.SPAM.wire, "", null))
        assertEquals("""{"reported":"u2","reason":"spam","note":"","event_id":null}""", encoded)
    }

    @Test
    fun blockedUsersRpcReply_decodesToBlockedUsers() {
        val row = json.decodeFromString<BlockedUserRow>(
            """{"user_id":"u2","display_name":"Sam","blocked_at":"2026-11-17T20:00:00+00:00"}""",
        )
        assertEquals(BlockedUser("u2", "Sam"), row.toBlockedUser())
    }
}
