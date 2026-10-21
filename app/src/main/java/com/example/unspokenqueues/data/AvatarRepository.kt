package com.example.unspokenqueues.data

import android.content.ContentResolver
import android.net.Uri
import com.example.unspokenqueues.BuildConfig
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Uploads profile photos to the public `avatars` Storage bucket.
 *
 * Each user has a single avatar object that is overwritten on every upload. Images are shrunk and
 * re-encoded as JPEG on the device first, so uploads stay small regardless of the source photo.
 */
class AvatarRepository(
    client: io.github.jan.supabase.SupabaseClient? = null,
) {
    private val storage by lazy { (client ?: SupabaseClientProvider.client).storage }

    /**
     * Uploads the image at [uri] (from the gallery or the camera) as the user's avatar and returns
     * its public URL, ready to store in `profiles.avatar_url`.
     */
    suspend fun upload(resolver: ContentResolver, userId: String, uri: Uri): String {
        val jpeg = withContext(Dispatchers.IO) { compressToJpeg(resolver, uri, AVATAR_MAX_DIMENSION, JPEG_QUALITY) }
        storage.from(AVATAR_BUCKET).upload(avatarPath(userId), jpeg) { upsert = true }
        return avatarPublicUrl(BuildConfig.SUPABASE_URL, userId, System.currentTimeMillis())
    }

    /**
     * Uploads the image at [uri] as the user's S.W.A.P. card image and returns its public URL,
     * ready to hand to [ProfileRepository.updateCardImage]. It lives beside the avatar in the same
     * bucket and is overwritten on every upload.
     */
    suspend fun uploadCardImage(resolver: ContentResolver, userId: String, uri: Uri): String {
        val jpeg = withContext(Dispatchers.IO) { compressToJpeg(resolver, uri, CARD_IMAGE_MAX_DIMENSION, JPEG_QUALITY) }
        storage.from(AVATAR_BUCKET).upload(cardImagePath(userId), jpeg) { upsert = true }
        return cardImagePublicUrl(BuildConfig.SUPABASE_URL, userId, System.currentTimeMillis())
    }

    private companion object {
        const val JPEG_QUALITY = 85
    }
}
