package com.example.unspokenqueues.data

import com.example.unspokenqueues.model.CueStatus
import com.example.unspokenqueues.model.Profile
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class CardImageTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun profile(
        avatarUrl: String = "",
        cardImageUrl: String = "",
        localAvatar: Any? = null,
        localCardImage: Any? = null,
    ) = Profile(
        "Sam", "", emptyList(), emptyList(), true,
        avatarUrl = avatarUrl, localAvatar = localAvatar,
        cardImageUrl = cardImageUrl, localCardImage = localCardImage,
    )

    // ---------- paths and URLs ----------

    @Test
    fun cardImagePath_isInTheUsersOwnFolder_besideTheAvatar() {
        assertEquals("u1/card.jpg", cardImagePath("u1"))
        assertEquals("u1", cardImagePath("u1").substringBefore('/'))
        assertEquals(avatarPath("u1").substringBefore('/'), cardImagePath("u1").substringBefore('/'))
    }

    @Test
    fun cardImagePublicUrl_pointsAtTheAvatarsBucket_andCarriesTheVersion() {
        assertEquals(
            "https://x.supabase.co/storage/v1/object/public/avatars/u1/card.jpg?v=7",
            cardImagePublicUrl(" https://x.supabase.co/ ", "u1", 7),
        )
    }

    // ---------- which picture the card shows ----------

    @Test
    fun cardPicture_prefersTheCardImage_thenTheProfilePhoto_thenNothing() {
        assertEquals("card.jpg", profile(avatarUrl = "avatar.jpg", cardImageUrl = "card.jpg").cardPicture)
        assertEquals("avatar.jpg", profile(avatarUrl = "avatar.jpg").cardPicture)
        assertNull(profile().cardPicture)
    }

    @Test
    fun cardImage_showsTheLocalCopyBeforeTheUploadedUrl() {
        val local = Any()
        assertEquals(local, profile(cardImageUrl = "card.jpg", localCardImage = local).cardImage)
    }

    @Test
    fun cardImage_isNullWhenBlank_soTheProfilePhotoStandsIn() {
        assertNull(profile(cardImageUrl = "  ").cardImage)
        assertNull(profile(cardImageUrl = "").cardImage)
    }

    // ---------- the profile row ----------

    @Test
    fun profileRow_readsTheCardImage_andOlderRowsWithoutOneStillDecode() {
        val withImage = json.decodeFromString<ProfileRow>("""{"id":"u1","card_image_url":"https://x/card.jpg"}""")
        assertEquals("https://x/card.jpg", withImage.toProfile().cardImageUrl)
        val without = json.decodeFromString<ProfileRow>("""{"id":"u1"}""")
        assertEquals("", without.toProfile().cardImageUrl)
    }

    @Test
    fun profileUpsert_neverCarriesTheCardImage_soSavingAProfileCannotTouchIt() {
        val strict = Json { encodeDefaults = true }
        val upsert = profile(cardImageUrl = "https://x/card.jpg").toUpsert("u1", CueStatus.GREEN)
        val encoded = strict.encodeToString(ProfileUpsert.serializer(), upsert)
        assertFalse(encoded, encoded.contains("card_image"))
    }
}
