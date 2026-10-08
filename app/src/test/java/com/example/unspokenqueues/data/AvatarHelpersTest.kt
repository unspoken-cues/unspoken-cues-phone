package com.example.unspokenqueues.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AvatarHelpersTest {

    // ---------- scaledSize ----------

    @Test
    fun scaledSize_landscape_fitsLongestSide() {
        assertEquals(512 to 384, scaledSize(4000, 3000, 512))
    }

    @Test
    fun scaledSize_portrait_fitsLongestSide() {
        assertEquals(384 to 512, scaledSize(3000, 4000, 512))
    }

    @Test
    fun scaledSize_square() {
        assertEquals(512 to 512, scaledSize(2048, 2048, 512))
    }

    @Test
    fun scaledSize_smallImage_isNotUpscaled() {
        assertEquals(200 to 100, scaledSize(200, 100, 512))
    }

    @Test
    fun scaledSize_exactFit_isUnchanged() {
        assertEquals(512 to 300, scaledSize(512, 300, 512))
    }

    @Test
    fun scaledSize_extremeAspectRatio_neverCollapsesToZero() {
        assertEquals(512 to 1, scaledSize(10000, 1, 512))
    }

    @Test
    fun scaledSize_rejectsInvalidInput() {
        assertThrows(IllegalArgumentException::class.java) { scaledSize(0, 100, 512) }
        assertThrows(IllegalArgumentException::class.java) { scaledSize(100, -1, 512) }
        assertThrows(IllegalArgumentException::class.java) { scaledSize(100, 100, 0) }
    }

    // ---------- sampleSizeFor ----------

    @Test
    fun sampleSizeFor_smallImage_isOne() {
        assertEquals(1, sampleSizeFor(400, 300, 512))
        assertEquals(1, sampleSizeFor(1000, 800, 512))
    }

    @Test
    fun sampleSizeFor_largeImage_isLargestPowerOfTwoThatKeepsEnoughPixels() {
        // 4000 / 4 = 1000 (>= 512) but 4000 / 8 = 500 (< 512).
        assertEquals(4, sampleSizeFor(4000, 3000, 512))
        assertEquals(4, sampleSizeFor(3000, 4000, 512))
        // 1024 / 2 = 512 still meets the target exactly.
        assertEquals(2, sampleSizeFor(1024, 768, 512))
    }

    @Test
    fun sampleSizeFor_thenScaledSize_endsAtTargetSize() {
        val sample = sampleSizeFor(4032, 3024, 512)
        assertEquals(512 to 384, scaledSize(4032 / sample, 3024 / sample, 512))
    }

    // ---------- URL building ----------

    @Test
    fun avatarPath_isInsideTheUsersFolder() {
        assertEquals("abc-123/avatar.jpg", avatarPath("abc-123"))
    }

    @Test
    fun avatarPublicUrl_buildsStoragePublicUrlWithVersion() {
        assertEquals(
            "https://ref.supabase.co/storage/v1/object/public/avatars/abc-123/avatar.jpg?v=42",
            avatarPublicUrl("https://ref.supabase.co", "abc-123", 42),
        )
    }

    @Test
    fun avatarPublicUrl_toleratesTrailingSlashAndWhitespace() {
        assertEquals(
            "https://ref.supabase.co/storage/v1/object/public/avatars/u1/avatar.jpg?v=7",
            avatarPublicUrl(" https://ref.supabase.co/ ", "u1", 7),
        )
    }
}
