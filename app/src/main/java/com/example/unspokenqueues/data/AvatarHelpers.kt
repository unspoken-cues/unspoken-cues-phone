package com.example.unspokenqueues.data

import kotlin.math.max
import kotlin.math.roundToInt

// Pure helpers for avatar uploads. Kept free of Android types so they can be unit-tested on the JVM.

const val AVATAR_BUCKET = "avatars"

/** Longest side, in pixels, of an uploaded avatar. */
const val AVATAR_MAX_DIMENSION = 512

/**
 * Object path inside the avatars bucket. The first folder is the user id because the bucket's
 * write policy only lets a user write under a folder named after their own id.
 */
fun avatarPath(userId: String): String = "$userId/avatar.jpg"

/**
 * Public URL of a user's avatar. [version] changes on every upload so image caches don't keep
 * showing the previous photo, which lives at the same path.
 */
fun avatarPublicUrl(supabaseUrl: String, userId: String, version: Long): String =
    "${supabaseUrl.trim().trimEnd('/')}/storage/v1/object/public/$AVATAR_BUCKET/${avatarPath(userId)}?v=$version"

/**
 * Size to scale a [width] x [height] image to so its longest side is at most [maxDimension],
 * keeping the aspect ratio. Images that already fit are returned unchanged (never upscaled).
 */
fun scaledSize(width: Int, height: Int, maxDimension: Int): Pair<Int, Int> {
    require(width > 0 && height > 0) { "Image size must be positive, got ${width}x$height" }
    require(maxDimension > 0) { "maxDimension must be positive, got $maxDimension" }
    val longest = max(width, height)
    if (longest <= maxDimension) return width to height
    val scale = maxDimension.toDouble() / longest
    return max(1, (width * scale).roundToInt()) to max(1, (height * scale).roundToInt())
}

/**
 * Largest power-of-two factor the image can be subsampled by while decoding and still have its
 * longest side at least [maxDimension], so the final resize only ever scales down.
 */
fun sampleSizeFor(width: Int, height: Int, maxDimension: Int): Int {
    require(maxDimension > 0) { "maxDimension must be positive, got $maxDimension" }
    var sample = 1
    while (max(width, height) / (sample * 2) >= maxDimension) sample *= 2
    return sample
}
