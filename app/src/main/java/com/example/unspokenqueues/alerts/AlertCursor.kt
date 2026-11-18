package com.example.unspokenqueues.alerts

import java.nio.ByteBuffer
import java.util.UUID

internal const val ALERT_PATH = "/unspoken-cues/announcements/v1/poll"

/** Empty means baseline request or signed-out reply. Nonempty frames are exactly 24 bytes. */
internal data class AlertCursor(val generation: UUID, val micros: Long) {
    init { require(micros > 0) }

    fun encode(): ByteArray = ByteBuffer.allocate(24)
        .putLong(generation.mostSignificantBits).putLong(generation.leastSignificantBits)
        .putLong(micros).array()

    companion object {
        fun decode(data: ByteArray): AlertCursor? {
            if (data.isEmpty()) return null
            require(data.size == 24)
            val input = ByteBuffer.wrap(data)
            return AlertCursor(UUID(input.long, input.long), input.long)
        }
    }
}
