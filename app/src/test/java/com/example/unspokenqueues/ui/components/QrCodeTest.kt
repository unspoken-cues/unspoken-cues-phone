package com.example.unspokenqueues.ui.components

import com.example.unspokenqueues.model.cardLink
import com.example.unspokenqueues.model.generateJoinCode
import com.google.zxing.qrcode.decoder.Decoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class QrCodeTest {

    private val sampleCardLink = cardLink("0123456789abcdef0123456789abcdef")

    // Reads the modules back with ZXing's decoder, the same way a scanner would after locating the code.
    private fun decode(modules: Array<BooleanArray>): String = Decoder().decode(modules).text

    @Test
    fun joinCode_survivesARoundTrip() {
        assertEquals("MIXER1", decode(qrModules("MIXER1")!!))
    }

    @Test
    fun generatedJoinCodes_surviveARoundTrip() {
        repeat(50) { seed ->
            val code = generateJoinCode(Random(seed))
            assertEquals(code, decode(qrModules(code)!!))
        }
    }

    @Test
    fun cardLink_survivesARoundTrip() {
        assertEquals(sampleCardLink, decode(qrModules(sampleCardLink)!!))
    }

    @Test
    fun modules_areSquare_withAFinderPatternInTheCorner() {
        val modules = qrModules("MIXER1")!!
        assertTrue(modules.all { it.size == modules.size })
        // Every QR code starts with a 7-cell dark bar along the top-left finder pattern.
        assertTrue(modules[0].take(7).all { it })
        assertTrue((0 until 7).all { modules[it][0] })
    }

    @Test
    fun differentContent_givesDifferentModules() {
        val a = qrModules("MIXER1")!!
        val b = qrModules("MIXER2")!!
        assertTrue(a.indices.any { !a[it].contentEquals(b[it]) })
    }

    @Test
    fun contentTooLongForAQrCode_givesNull() {
        assertNotNull(qrModules("x".repeat(500)))
        assertNull(qrModules("x".repeat(5000)))
    }
}
