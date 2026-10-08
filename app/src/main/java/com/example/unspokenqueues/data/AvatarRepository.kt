package com.example.unspokenqueues.data

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import com.example.unspokenqueues.BuildConfig
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException

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
        val jpeg = withContext(Dispatchers.IO) { compressToJpeg(resolver, uri) }
        storage.from(AVATAR_BUCKET).upload(avatarPath(userId), jpeg) { upsert = true }
        return avatarPublicUrl(BuildConfig.SUPABASE_URL, userId, System.currentTimeMillis())
    }

    /** Decodes the image, fixes its rotation, scales it down and encodes it as JPEG. */
    private fun compressToJpeg(resolver: ContentResolver, uri: Uri): ByteArray {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("Couldn't read image $uri")

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, AVATAR_MAX_DIMENSION)
        }
        val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: throw IOException("Couldn't decode image $uri")

        val (width, height) = scaledSize(decoded.width, decoded.height, AVATAR_MAX_DIMENSION)
        // Camera photos are often stored sideways with the real orientation in EXIF.
        val matrix = Matrix().apply {
            postScale(width.toFloat() / decoded.width, height.toFloat() / decoded.height)
            postRotate(rotationDegrees(resolver, uri))
        }
        val result = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)

        return ByteArrayOutputStream().use { out ->
            result.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            out.toByteArray()
        }
    }

    private fun rotationDegrees(resolver: ContentResolver, uri: Uri): Float {
        val orientation = runCatching {
            resolver.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            }
        }.getOrNull()
        return when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
    }

    private companion object {
        const val JPEG_QUALITY = 85
    }
}
