package com.example.unspokenqueues.data

import android.content.ContentResolver
import android.net.Uri
import com.example.unspokenqueues.BuildConfig
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

const val EVENT_COVER_BUCKET = "event-covers"

/** Longest side, in pixels, of an uploaded event cover. */
const val EVENT_COVER_MAX_DIMENSION = 1280

/**
 * Object path inside the event-covers bucket. The first folder is the user id because the bucket's
 * write policy only lets a user write under a folder named after their own id. [version] keeps a
 * new cover from overwriting an older one that an event still shows.
 */
fun eventCoverPath(userId: String, version: Long): String = "$userId/cover-$version.jpg"

/** Public URL of an uploaded event cover. */
fun eventCoverPublicUrl(supabaseUrl: String, userId: String, version: Long): String =
    "${supabaseUrl.trim().trimEnd('/')}/storage/v1/object/public/$EVENT_COVER_BUCKET/${eventCoverPath(userId, version)}"

/**
 * Uploads event cover images to the public `event-covers` bucket. Images are shrunk and re-encoded
 * as JPEG on the device first, so uploads stay small whatever the source photo.
 *
 * Open so UI tests can swap in a fake that never talks to the server.
 */
open class EventCoverRepository(
    client: io.github.jan.supabase.SupabaseClient? = null,
) {
    private val storage by lazy { (client ?: SupabaseClientProvider.client).storage }

    /** Uploads the image at [uri] as a new cover and returns its public URL, ready for `events.cover_url`. */
    open suspend fun upload(resolver: ContentResolver, userId: String, uri: Uri): String {
        val version = System.currentTimeMillis()
        val jpeg = withContext(Dispatchers.IO) { compressToJpeg(resolver, uri, EVENT_COVER_MAX_DIMENSION, JPEG_QUALITY) }
        storage.from(EVENT_COVER_BUCKET).upload(eventCoverPath(userId, version), jpeg)
        return eventCoverPublicUrl(BuildConfig.SUPABASE_URL, userId, version)
    }

    private companion object {
        const val JPEG_QUALITY = 82
    }
}
