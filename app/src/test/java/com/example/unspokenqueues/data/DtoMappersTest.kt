package com.example.unspokenqueues.data

import com.example.unspokenqueues.model.CueStatus
import com.example.unspokenqueues.model.Event
import com.example.unspokenqueues.model.Profile
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class DtoMappersTest {

    private val json = Json { ignoreUnknownKeys = true }

    // ---------- ProfileRow ----------

    @Test
    fun profileRow_toProfile_mapsAvatarUrl() {
        val row = ProfileRow(id = "u1", displayName = "Sam", avatarUrl = "https://x.test/avatars/u1/avatar.jpg?v=1")
        assertEquals("https://x.test/avatars/u1/avatar.jpg?v=1", row.toProfile().avatarUrl)
    }

    @Test
    fun profileRow_toProfile_avatarDefaultsToBlank() {
        assertEquals("", ProfileRow(id = "u1").toProfile().avatarUrl)
    }

    @Test
    fun profileRow_decodesAvatarUrlFromSnakeCaseJson() {
        val row = json.decodeFromString<ProfileRow>(
            """{"id":"u1","display_name":"Sam","avatar_url":"https://x.test/a.jpg","is_public":false,"status":"RED"}""",
        )
        assertEquals("https://x.test/a.jpg", row.avatarUrl)
        assertEquals(false, row.toProfile().isPublic)
        assertEquals(CueStatus.RED, row.toCueStatus())
    }

    @Test
    fun profileRow_withoutAvatarColumn_decodesToBlank() {
        val row = json.decodeFromString<ProfileRow>("""{"id":"u1","display_name":"Sam"}""")
        assertEquals("", row.toProfile().avatarUrl)
    }

    @Test
    fun profile_toUpsert_carriesAvatarUrl() {
        val profile = Profile("Sam", "bio", listOf("a"), listOf("b"), true, avatarUrl = "https://x.test/a.jpg")
        val upsert = profile.toUpsert("u1", CueStatus.YELLOW)
        assertEquals("https://x.test/a.jpg", upsert.avatarUrl)
        assertEquals("u1", upsert.id)
        assertEquals("YELLOW", upsert.status)
    }

    @Test
    fun profileUpsert_encodesAvatarUrlAsSnakeCase() {
        val upsert = Profile("Sam", "", emptyList(), emptyList(), true, "https://x.test/a.jpg").toUpsert("u1", CueStatus.GREEN)
        val encoded = json.encodeToString(ProfileUpsert.serializer(), upsert)
        assert(encoded.contains("\"avatar_url\":\"https://x.test/a.jpg\"")) { encoded }
    }

    // ---------- onboarding_complete ----------

    @Test
    fun profileRow_onboardingDefaultsToNotComplete() {
        assertEquals(false, ProfileRow(id = "u1").onboardingComplete)
    }

    @Test
    fun profileRow_decodesOnboardingCompleteFromSnakeCaseJson() {
        val done = json.decodeFromString<ProfileRow>("""{"id":"u1","onboarding_complete":true}""")
        val notDone = json.decodeFromString<ProfileRow>("""{"id":"u1","onboarding_complete":false}""")
        assertEquals(true, done.onboardingComplete)
        assertEquals(false, notDone.onboardingComplete)
    }

    @Test
    fun profileRow_withoutOnboardingColumn_decodesToNotComplete() {
        // A database that hasn't had the column added yet.
        val row = json.decodeFromString<ProfileRow>("""{"id":"u1","display_name":"Sam"}""")
        assertEquals(false, row.onboardingComplete)
    }

    @Test
    fun onboardingComplete_doesNotChangeTheMappedProfile() {
        val row = ProfileRow(id = "u1", displayName = "Sam", bio = "hi", avatarUrl = "https://x.test/a.jpg")
        assertEquals(row.toProfile(), row.copy(onboardingComplete = true).toProfile())
    }

    @Test
    fun profileUpsert_neverWritesOnboardingComplete() {
        // Saving a profile must not reset the flag, so the payload may not carry the column at all.
        val strict = Json { encodeDefaults = true }
        val upsert = Profile("Sam", "bio", listOf("a"), listOf("b"), true).toUpsert("u1", CueStatus.GREEN)
        val encoded = strict.encodeToString(ProfileUpsert.serializer(), upsert)
        assert(!encoded.contains("onboarding")) { encoded }
    }

    // ---------- EventRow ----------

    @Test
    fun eventRow_toEvent_mapsEveryField() {
        val row = EventRow(id = "e1", hostId = "h1", name = "Mixer", details = "7 PM", joinCode = "MIXER1", active = false)
        assertEquals(Event("e1", "h1", "Mixer", "7 PM", "MIXER1", false), row.toEvent())
    }

    @Test
    fun eventRow_decodesSnakeCaseJson_andIgnoresServerColumns() {
        val row = json.decodeFromString<EventRow>(
            """{"id":"e1","host_id":"h1","name":"Mixer","details":"7 PM","join_code":"MIXER1","active":true,"created_at":"2026-10-07T20:00:00Z"}""",
        )
        assertEquals(Event("e1", "h1", "Mixer", "7 PM", "MIXER1", true), row.toEvent())
    }

    // ---------- EventMemberRow ----------

    @Test
    fun eventMemberRow_decodesSnakeCaseJson() {
        val row = json.decodeFromString<EventMemberRow>(
            """{"event_id":"e1","user_id":"u1","joined_at":"2026-10-07T20:00:00Z"}""",
        )
        assertEquals(EventMemberRow("e1", "u1", "2026-10-07T20:00:00Z"), row)
    }

    @Test
    fun profileRow_toAttendee_usesRowIdAsUserId() {
        val attendee = ProfileRow(id = "u1", displayName = "Sam", status = "PURPLE", avatarUrl = "https://x.test/a.jpg").toAttendee()
        assertEquals("u1", attendee.userId)
        assertEquals("Sam", attendee.name)
        assertEquals(CueStatus.PURPLE, attendee.status)
        assertEquals("https://x.test/a.jpg", attendee.profile.avatarUrl)
    }

    @Test
    fun toAttendees_keepsMemberOrder_andSkipsMembersWithoutAReadableProfile() {
        val members = listOf(EventMemberRow("e1", "u2"), EventMemberRow("e1", "hidden"), EventMemberRow("e1", "u1"))
        val profiles = listOf(ProfileRow(id = "u1", displayName = "Ana"), ProfileRow(id = "u2", displayName = "Ben"))
        assertEquals(listOf("u2", "u1"), members.toAttendees(profiles).map { it.userId })
    }
}
