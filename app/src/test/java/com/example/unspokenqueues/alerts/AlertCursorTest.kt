package com.example.unspokenqueues.alerts

import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class AlertCursorTest {
    @Test fun wireContractUsesOnlyGenerationAndMicrosecondCursor() {
        val bytes = ByteArray(24) { it.toByte() }
        val frame = AlertCursor(UUID.fromString("00010203-0405-0607-0809-0a0b0c0d0e0f"), 0x1011121314151617L)
        assertArrayEquals(bytes, frame.encode())
        assertEquals(frame, AlertCursor.decode(bytes))
        assertNull(AlertCursor.decode(byteArrayOf()))
        for (bad in listOf(ByteArray(1), ByteArray(25), ByteArray(24))) {
            assertThrows(IllegalArgumentException::class.java) { AlertCursor.decode(bad) }
        }
    }
}
