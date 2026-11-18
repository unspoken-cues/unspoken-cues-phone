package com.example.unspokenqueues.data

import com.example.unspokenqueues.model.CueStatus
import com.example.unspokenqueues.model.Event
import com.example.unspokenqueues.model.Profile
import com.example.unspokenqueues.model.ProfilePrivacy
import kotlinx.serialization.json.Json
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
        val upsert = profile.toUpsert("u1")
        assertEquals("https://x.test/a.jpg", upsert.avatarUrl)
        assertEquals("u1", upsert.id)
        assertFalse(json.encodeToString(ProfileUpsert.serializer(), upsert).contains("\"status\""))
    }

    @Test
    fun profileUpsert_encodesAvatarUrlAsSnakeCase() {
        val upsert = Profile("Sam", "", emptyList(), emptyList(), true, "https://x.test/a.jpg").toUpsert("u1")
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
        val upsert = Profile("Sam", "bio", listOf("a"), listOf("b"), true).toUpsert("u1")
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

    // ---------- AttendeeRow ----------

    @Test
    fun attendeeRpcReply_decodesToAnAttendee() {
        val row = json.decodeFromString<AttendeeRow>(
            """{"user_id":"u1","display_name":"Sam","avatar_url":"https://x.test/a.jpg","bio":"hi",
               "preferences":["Text first"],"boundaries":["No hugs"],"status":"PURPLE"}""",
        )
        val attendee = row.toAttendee()
        assertEquals("u1", attendee.userId)
        assertEquals("Sam", attendee.name)
        assertEquals(CueStatus.PURPLE, attendee.status)
        assertEquals("https://x.test/a.jpg", attendee.profile.avatarUrl)
        assertEquals("hi", attendee.profile.bio)
        assertEquals(listOf("Text first"), attendee.profile.preferences)
        assertEquals(listOf("No hugs"), attendee.profile.boundaries)
    }

    @Test
    fun attendeeRow_withHiddenFields_mapsToBlanksAndNoStatus() {
        // What the server sends for someone who hides everything but their name.
        val row = json.decodeFromString<AttendeeRow>(
            """{"user_id":"u1","display_name":"Sam","avatar_url":"","bio":"","preferences":[],"boundaries":[],"status":null}""",
        )
        val attendee = row.toAttendee()
        assertEquals("Sam", attendee.name)
        assertNull(attendee.status)
        assertNull(attendee.profile.avatarImage)
        assertEquals("", attendee.profile.bio)
        assertEquals(emptyList<String>(), attendee.profile.preferences)
    }

    @Test
    fun attendeeRow_carriesCuedOut_andReadsAsCuedInWhenAbsent() {
        val out = json.decodeFromString<AttendeeRow>("""{"user_id":"u1","cued_in":false}""").toAttendee()
        assertEquals(false, out.cuedIn)
        // A server without the event directory migration doesn't send it; everyone is listed there.
        assertEquals(true, json.decodeFromString<AttendeeRow>("""{"user_id":"u1"}""").toAttendee().cuedIn)
    }

    @Test
    fun attendeeRow_withUnknownStatus_readsAsHidden() {
        assertNull(AttendeeRow(userId = "u1", status = "BLUE").toAttendee().status)
    }

    // ---------- privacy ----------

    @Test
    fun profileRow_toProfile_carriesPrivateModeAndPrivacy() {
        val row = json.decodeFromString<ProfileRow>(
            """{"id":"u1","private_mode":true,"show_photo":false,"show_bio":true,"show_preferences":false,
               "show_boundaries":true,"show_status":false}""",
        )
        val profile = row.toProfile()
        assertEquals(true, profile.privateMode)
        assertEquals(
            ProfilePrivacy(showPhoto = false, showBio = true, showPreferences = false, showBoundaries = true, showStatus = false),
            profile.privacy,
        )
    }

    @Test
    fun profileRow_withoutPrivacyColumns_isNotPrivate_andShowsEverything() {
        // A database that hasn't had section 16 applied yet.
        val profile = json.decodeFromString<ProfileRow>("""{"id":"u1","display_name":"Sam"}""").toProfile()
        assertEquals(false, profile.privateMode)
        assertEquals(ProfilePrivacy(), profile.privacy)
    }

    @Test
    fun profileUpsert_neverWritesPrivacyOrPrivateMode() {
        // Saving a profile must not reset privacy settings, so the payload may not carry them.
        val strict = Json { encodeDefaults = true }
        val profile = Profile("Sam", "", emptyList(), emptyList(), true, privateMode = true, privacy = ProfilePrivacy(showBio = false))
        val encoded = strict.encodeToString(ProfileUpsert.serializer(), profile.toUpsert("u1"))
        assert(!encoded.contains("private_mode") && !encoded.contains("show_")) { encoded }
    }
}
