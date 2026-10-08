package com.example.unspokenqueues.ui.components

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

/** What the scanner shows: the live camera, or the reason it can't show it. */
internal enum class ScannerState { SCANNING, NEEDS_PERMISSION, PERMISSION_BLOCKED, NO_CAMERA, CAMERA_ERROR }

/**
 * Picks the scanner's state. A missing camera wins over everything (there is no point asking for
 * a permission that can't be used), then the permission, then a camera that failed to start.
 * [permissionBlocked] means Android no longer shows the permission prompt, so only the system
 * settings can grant it.
 */
internal fun scannerState(
    hasCamera: Boolean,
    permissionGranted: Boolean,
    permissionBlocked: Boolean,
    cameraFailed: Boolean,
): ScannerState = when {
    !hasCamera -> ScannerState.NO_CAMERA
    !permissionGranted -> if (permissionBlocked) ScannerState.PERMISSION_BLOCKED else ScannerState.NEEDS_PERMISSION
    cameraFailed -> ScannerState.CAMERA_ERROR
    else -> ScannerState.SCANNING
}

/**
 * Full-screen QR scanner. Asks for the camera permission when it is missing, then calls [onCode]
 * once with the text of the first QR code it reads. The scanner stays up until the caller removes
 * it, so [onCode] should close it. [onDismiss] is called when the user backs out without scanning.
 */
@Composable
fun QrScanner(
    onCode: (String) -> Unit,
    onDismiss: () -> Unit,
    hint: String = "Point the camera at a QR code",
) {
    val context = LocalContext.current
    val hasCamera = remember { context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY) }
    var granted by remember { mutableStateOf(context.hasCameraPermission()) }
    var blocked by remember { mutableStateOf(false) }
    var cameraFailed by remember { mutableStateOf(false) }
    // Bumped by "Try again" to rebuild the camera from scratch.
    var attempt by remember { mutableIntStateOf(0) }
    // Saveable so a rotation while the system prompt is up doesn't ask a second time.
    var asked by rememberSaveable { mutableStateOf(false) }

    val requestPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        // After repeated denials Android answers "denied" without showing the prompt, and reports
        // that no rationale is needed. From then on the button has to lead to Settings instead.
        blocked = !ok && context.findActivity()
            ?.let { !ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.CAMERA) } == true
    }
    LaunchedEffect(Unit) {
        if (hasCamera && !granted && !asked) {
            asked = true
            requestPermission.launch(Manifest.permission.CAMERA)
        }
    }
    // Picks up a permission granted in Settings once the user comes back.
    LifecycleResumeEffect(Unit) {
        if (context.hasCameraPermission()) granted = true
        onPauseOrDispose {}
    }

    Dialog(onDismiss, DialogProperties(usePlatformDefaultWidth = false)) {
        QrScannerContent(
            state = scannerState(hasCamera, granted, blocked, cameraFailed),
            onRequestPermission = { requestPermission.launch(Manifest.permission.CAMERA) },
            onOpenSettings = {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                )
            },
            onRetry = {
                cameraFailed = false
                attempt++
            },
            onDismiss = onDismiss,
            hint = hint,
        ) {
            key(attempt) { CameraPreview(onCode, onError = { cameraFailed = true }) }
        }
    }
}

/** The scanner's screen for a given [state]. [camera] is the live preview, shown only while scanning. */
@Composable
internal fun QrScannerContent(
    state: ScannerState,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    hint: String = "Point the camera at a QR code",
    camera: @Composable () -> Unit = {},
) {
    when (state) {
        ScannerState.SCANNING -> Box(Modifier.fillMaxSize().background(Color.Black)) {
            camera()
            Column(
                Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                // Only a guide for aiming: a code anywhere in view is read.
                Box(Modifier.fillMaxWidth(0.7f).aspectRatio(1f).border(3.dp, Color.White, RoundedCornerShape(20.dp)))
                Spacer(Modifier.height(24.dp))
                Text(hint, style = MaterialTheme.typography.bodyLarge, color = Color.White, textAlign = TextAlign.Center)
            }
            TextButton(onDismiss, Modifier.align(Alignment.TopEnd).padding(8.dp)) { Text("Close", color = Color.White) }
        }
        ScannerState.NEEDS_PERMISSION -> ScannerMessage(
            title = "Camera access needed",
            body = "Unspoken Cues uses the camera only to scan QR codes. Nothing is recorded or saved.",
            action = "Allow camera",
            onAction = onRequestPermission,
            onDismiss = onDismiss,
        )
        ScannerState.PERMISSION_BLOCKED -> ScannerMessage(
            title = "Camera access is turned off",
            body = "To scan QR codes, allow the camera for Unspoken Cues in your phone's settings.",
            action = "Open settings",
            onAction = onOpenSettings,
            onDismiss = onDismiss,
        )
        ScannerState.NO_CAMERA -> ScannerMessage(
            title = "No camera found",
            body = "This device has no camera to scan with. You can type the code in instead.",
            onDismiss = onDismiss,
        )
        ScannerState.CAMERA_ERROR -> ScannerMessage(
            title = "Couldn't start the camera",
            body = "Another app may be using it. Close that app and try again, or type the code in instead.",
            action = "Try again",
            onAction = onRetry,
            onDismiss = onDismiss,
        )
    }
}

@Composable
private fun ScannerMessage(
    title: String,
    body: String,
    onDismiss: () -> Unit,
    action: String? = null,
    onAction: () -> Unit = {},
) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            if (action != null) {
                Button(onAction, Modifier.fillMaxWidth().height(52.dp)) { Text(action) }
            }
            TextButton(onDismiss, Modifier.fillMaxWidth()) { Text("Close") }
        }
    }
}

// Live camera preview that feeds every frame to the QR analyzer until one decodes.
@Composable
private fun CameraPreview(onCode: (String) -> Unit, onError: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnCode by rememberUpdatedState(onCode)
    val currentOnError by rememberUpdatedState(onError)
    val previewView = remember {
        // TextureView mode: the default SurfaceView draws in its own layer and can show through
        // or lag behind the dialog window this scanner lives in.
        PreviewView(context).apply { implementationMode = PreviewView.ImplementationMode.COMPATIBLE }
    }

    DisposableEffect(lifecycleOwner) {
        val analysisExecutor = Executors.newSingleThreadExecutor()
        val analyzer = QrAnalyzer { currentOnCode(it) }
        val preview = Preview.Builder().build().apply { setSurfaceProvider(previewView.surfaceProvider) }
        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .apply { setAnalyzer(analysisExecutor, analyzer) }
        val providerFuture = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        var disposed = false

        providerFuture.addListener({
            if (disposed) return@addListener
            try {
                val cameraProvider = providerFuture.get()
                // Prefer the back camera, but a front-only device (some tablets) can still scan.
                val selector = listOf(CameraSelector.DEFAULT_BACK_CAMERA, CameraSelector.DEFAULT_FRONT_CAMERA)
                    .firstOrNull { cameraProvider.hasCamera(it) }
                if (selector == null) {
                    currentOnError()
                } else {
                    cameraProvider.bindToLifecycle(lifecycleOwner, selector, preview, analysis)
                    provider = cameraProvider
                }
            } catch (e: Exception) {
                // The camera is disabled by policy, in use elsewhere, or failed to initialise.
                currentOnError()
            }
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            disposed = true
            provider?.unbind(preview, analysis)
            analysis.clearAnalyzer()
            analyzer.close()
            analysisExecutor.shutdown()
        }
    }

    AndroidView({ previewView }, Modifier.fillMaxSize())
}

// Runs ML Kit's QR detector over camera frames and reports the first non-empty code, once.
private class QrAnalyzer(private val onCode: (String) -> Unit) : ImageAnalysis.Analyzer {
    private val scanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build(),
    )

    @Volatile
    private var delivered = false

    @androidx.annotation.OptIn(ExperimentalGetImage::class)
    override fun analyze(frame: ImageProxy) {
        val image = frame.image
        if (delivered || image == null) {
            frame.close()
            return
        }
        scanner.process(InputImage.fromMediaImage(image, frame.imageInfo.rotationDegrees))
            // Success listeners run on the main thread, so the once-only check needs no lock.
            .addOnSuccessListener { codes ->
                val text = codes.firstNotNullOfOrNull { code -> code.rawValue?.takeIf { it.isNotBlank() } }
                if (text != null && !delivered) {
                    delivered = true
                    onCode(text)
                }
            }
            // CameraX only hands over the next frame once this one is closed.
            .addOnCompleteListener { frame.close() }
    }

    fun close() {
        // A frame may still be in flight; its result must not reach a scanner that has gone away.
        delivered = true
        scanner.close()
    }
}

private fun Context.hasCameraPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
