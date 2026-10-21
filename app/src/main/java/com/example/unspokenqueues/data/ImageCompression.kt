package com.example.unspokenqueues.data

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.IOException

/**
 * Decodes the image at [uri] (from the gallery or the camera), fixes its rotation, scales it down
 * so its longest side is at most [maxDimension] and encodes it as JPEG at [quality]. Blocking:
 * call it off the main thread.
 */
fun compressToJpeg(resolver: ContentResolver, uri: Uri, maxDimension: Int, quality: Int): ByteArray {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("Couldn't read image $uri")

    val options = BitmapFactory.Options().apply {
        inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, maxDimension)
    }
    val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        ?: throw IOException("Couldn't decode image $uri")

    val (width, height) = scaledSize(decoded.width, decoded.height, maxDimension)
    // Camera photos are often stored sideways with the real orientation in EXIF.
    val matrix = Matrix().apply {
        postScale(width.toFloat() / decoded.width, height.toFloat() / decoded.height)
        postRotate(rotationDegrees(resolver, uri))
    }
    val result = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)

    return ByteArrayOutputStream().use { out ->
        result.compress(Bitmap.CompressFormat.JPEG, quality, out)
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
