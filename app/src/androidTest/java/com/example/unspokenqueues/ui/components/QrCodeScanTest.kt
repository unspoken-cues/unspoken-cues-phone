package com.example.unspokenqueues.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.example.unspokenqueues.model.cardLink
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Checks that the QR codes the app draws can be read by the same ML Kit detector the scanner
 * uses. The drawn code is captured from the screen, so no camera is involved.
 */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 26) // captureToImage needs API 26
class QrCodeScanTest {

    @get:Rule
    val compose = createComposeRule()

    private val sampleCardLink = cardLink("0123456789abcdef0123456789abcdef")

    private fun scanDrawnCode(content: String, size: Dp = 240.dp, padding: Dp = 20.dp): List<String?> {
        compose.setContent {
            Box(Modifier.testTag("qr").size(size).background(Color.White).padding(padding)) { QrCode(content) }
        }
        val bitmap = compose.onNodeWithTag("qr").captureToImage().asAndroidBitmap()
        val scanner = BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build(),
        )
        return try {
            Tasks.await(scanner.process(InputImage.fromBitmap(bitmap, 0))).map { it.rawValue }
        } finally {
            scanner.close()
        }
    }

    @Test
    fun drawnJoinCode_isReadByTheScannersDetector() {
        assertEquals(listOf("MIXER1"), scanDrawnCode("MIXER1"))
    }

    @Test
    fun drawnCardLink_isReadByTheScannersDetector() {
        assertEquals(listOf(sampleCardLink), scanDrawnCode(sampleCardLink))
    }

    @Test
    fun cardLink_atTheSizeShownOnTheSwapCard_isStillReadable() {
        // The corner of the swap card gives the code a 92dp box with 8dp of padding.
        assertEquals(listOf(sampleCardLink), scanDrawnCode(sampleCardLink, size = 92.dp, padding = 8.dp))
    }
}
