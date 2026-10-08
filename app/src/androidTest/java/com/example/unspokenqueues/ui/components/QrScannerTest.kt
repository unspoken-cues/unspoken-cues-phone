package com.example.unspokenqueues.ui.components

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.unspokenqueues.ui.theme.UnspokenQueuesTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The scanner's screens for each state. These drive [QrScannerContent] directly so they don't
 * depend on whether this device has already granted the camera permission; the live camera and
 * decoding are checked by hand on a device.
 */
@RunWith(AndroidJUnit4::class)
class QrScannerTest {

    @get:Rule
    val compose = createComposeRule()

    private val clicks = mutableListOf<String>()

    private fun show(state: ScannerState) {
        compose.setContent {
            UnspokenQueuesTheme {
                QrScannerContent(
                    state = state,
                    onRequestPermission = { clicks += "request" },
                    onOpenSettings = { clicks += "settings" },
                    onRetry = { clicks += "retry" },
                    onDismiss = { clicks += "dismiss" },
                    hint = "Scan the event's QR code",
                ) { Text("camera preview") }
            }
        }
    }

    @Test
    fun withoutPermission_showsThePermissionRequest_andNoCamera() {
        show(ScannerState.NEEDS_PERMISSION)

        compose.onNodeWithText("Camera access needed").assertIsDisplayed()
        compose.onNodeWithText("Allow camera").assertIsDisplayed().assertHasClickAction()
        compose.onNodeWithText("camera preview").assertDoesNotExist()
        compose.onNodeWithText("Scan the event's QR code").assertDoesNotExist()
    }

    @Test
    fun allowCamera_requestsThePermission() {
        show(ScannerState.NEEDS_PERMISSION)

        compose.onNodeWithText("Allow camera").performClick()

        assertEquals(listOf("request"), clicks)
    }

    @Test
    fun permissionRequest_canBeClosed() {
        show(ScannerState.NEEDS_PERMISSION)

        compose.onNodeWithText("Close").performClick()

        assertEquals(listOf("dismiss"), clicks)
    }

    @Test
    fun blockedPermission_offersSettingsInsteadOfThePrompt() {
        show(ScannerState.PERMISSION_BLOCKED)

        compose.onNodeWithText("Camera access is turned off").assertIsDisplayed()
        compose.onNodeWithText("Allow camera").assertDoesNotExist()
        compose.onNodeWithText("Open settings").performClick()

        assertEquals(listOf("settings"), clicks)
    }

    @Test
    fun noCamera_explains_andOffersOnlyClose() {
        show(ScannerState.NO_CAMERA)

        compose.onNodeWithText("No camera found").assertIsDisplayed()
        compose.onNodeWithText("Allow camera").assertDoesNotExist()
        compose.onNodeWithText("Open settings").assertDoesNotExist()
        compose.onNodeWithText("camera preview").assertDoesNotExist()
        compose.onNodeWithText("Close").performClick()

        assertEquals(listOf("dismiss"), clicks)
    }

    @Test
    fun cameraError_offersARetry() {
        show(ScannerState.CAMERA_ERROR)

        compose.onNodeWithText("Couldn't start the camera").assertIsDisplayed()
        compose.onNodeWithText("Try again").performClick()

        assertEquals(listOf("retry"), clicks)
    }

    @Test
    fun scanning_showsTheCameraWithTheHint() {
        show(ScannerState.SCANNING)

        compose.onNodeWithText("camera preview").assertIsDisplayed()
        compose.onNodeWithText("Scan the event's QR code").assertIsDisplayed()
        compose.onNodeWithText("Camera access needed").assertDoesNotExist()
        compose.onNodeWithText("Close").performClick()

        assertEquals(listOf("dismiss"), clicks)
    }
}
