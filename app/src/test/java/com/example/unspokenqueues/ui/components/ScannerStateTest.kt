package com.example.unspokenqueues.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class ScannerStateTest {

    @Test
    fun cameraAndPermission_scan() {
        assertEquals(
            ScannerState.SCANNING,
            scannerState(hasCamera = true, permissionGranted = true, permissionBlocked = false, cameraFailed = false),
        )
    }

    @Test
    fun missingPermission_asksForIt() {
        assertEquals(
            ScannerState.NEEDS_PERMISSION,
            scannerState(hasCamera = true, permissionGranted = false, permissionBlocked = false, cameraFailed = false),
        )
    }

    @Test
    fun blockedPermission_pointsToSettings() {
        assertEquals(
            ScannerState.PERMISSION_BLOCKED,
            scannerState(hasCamera = true, permissionGranted = false, permissionBlocked = true, cameraFailed = false),
        )
    }

    @Test
    fun permissionGrantedLater_clearsABlock() {
        // The blocked flag is left over from the denial; granting in Settings must still win.
        assertEquals(
            ScannerState.SCANNING,
            scannerState(hasCamera = true, permissionGranted = true, permissionBlocked = true, cameraFailed = false),
        )
    }

    @Test
    fun noCamera_winsOverEverything() {
        for (granted in listOf(true, false)) for (blocked in listOf(true, false)) for (failed in listOf(true, false)) {
            assertEquals(ScannerState.NO_CAMERA, scannerState(false, granted, blocked, failed))
        }
    }

    @Test
    fun cameraFailure_showsOnlyOncePermissionIsGranted() {
        assertEquals(
            ScannerState.CAMERA_ERROR,
            scannerState(hasCamera = true, permissionGranted = true, permissionBlocked = false, cameraFailed = true),
        )
        assertEquals(
            ScannerState.NEEDS_PERMISSION,
            scannerState(hasCamera = true, permissionGranted = false, permissionBlocked = false, cameraFailed = true),
        )
    }
}
