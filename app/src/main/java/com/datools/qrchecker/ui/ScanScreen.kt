package com.datools.qrchecker.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.datools.qrchecker.R
import com.datools.qrchecker.Screen
import com.datools.qrchecker.TYPE_NOT_SCANNED
import com.datools.qrchecker.TYPE_SCANNED
import com.datools.qrchecker.data.SessionRepository
import com.datools.qrchecker.model.SessionData
import com.datools.qrchecker.ui.theme.Error
import com.datools.qrchecker.ui.theme.Success
import com.datools.qrchecker.ui.theme.Warning
import com.datools.qrchecker.util.normalizeCode
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

private const val TAG = "QRChecker"

private data class UiFeedback(
    val message: String,
    val color: Color,
    val code: String?
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(
    navController: NavController,
    sessionId: String
) {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val repo = remember { SessionRepository(context) }
    val scope = rememberCoroutineScope()
    val view = LocalView.current

    var session by remember { mutableStateOf<SessionData?>(null) }

    // ----- feedback state (declared before any early return so the composition
    // ----- keeps a stable set of remembered slots) -----
    var feedback by remember { mutableStateOf<UiFeedback?>(null) }
    var lastFeedbackAt by remember { mutableLongStateOf(0L) }
    var lastShownCode by remember { mutableStateOf<String?>(null) }
    val vibrator = remember { ContextCompat.getSystemService(context, Vibrator::class.java) }

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    LaunchedEffect(sessionId) {
        session = repo.getById(sessionId)
    }

    BackHandler {
        navController.navigate(Screen.Home.route) {
            popUpTo(Screen.Home.route) { inclusive = true }
        }
    }

    val cooldownMs = 1000L
    val displayMs = 1200L

    // localized strings
    val alreadyScannedMsg = stringResource(id = R.string.msg_already_scanned)
    val scannedMsg = stringResource(id = R.string.msg_scanned)
    val notFoundMsg = stringResource(id = R.string.msg_not_in_list)
    val scannedButtonText = stringResource(id = R.string.btn_scanned)
    val notScannedButtonText = stringResource(id = R.string.btn_not_scanned)
    val noCameraPermissionText = stringResource(id = R.string.no_camera_permission)

    fun showFeedback(message: String, color: Color, vibrMs: Long, code: String?) {
        val now = System.currentTimeMillis()

        // if something is already being shown - we don't show the new one
        if (feedback != null) return

        // code deduplication + cooldown
        if (code != null) {
            if (code == lastShownCode && (now - lastFeedbackAt) < cooldownMs) return
            lastShownCode = code
        } else {
            if (now - lastFeedbackAt < cooldownMs) return
        }

        lastFeedbackAt = now
        feedback = UiFeedback(message, color, code)

        try {
            vibrator?.takeIf { it.hasVibrator() }?.let {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    it.vibrate(
                        VibrationEffect.createOneShot(vibrMs, VibrationEffect.DEFAULT_AMPLITUDE)
                    )
                } else {
                    @Suppress("DEPRECATION")
                    it.vibrate(vibrMs)
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Vibrate failed", t)
        }

        scope.launch {
            delay(displayMs)
            // reset only if it is the same feedback
            if (feedback?.code == code) feedback = null
        }
    }

    // Runs on the main thread (posted from the analyzer), so reading and updating
    // `session` here cannot interleave with another decoded frame.
    fun onCodeScanned(rawCode: String) {
        val current = session ?: return
        val code = normalizeCode(rawCode)
        if (code.isEmpty()) return

        when {
            code !in current.codes ->
                showFeedback(notFoundMsg, Error, 120L, code)

            code in current.scannedCodes ->
                showFeedback(alreadyScannedMsg, Warning, 30L, code)

            else -> {
                session = current.copy(scannedCodes = current.scannedCodes + code)
                showFeedback(scannedMsg, Success, 60L, code)
                scope.launch {
                    try {
                        // a single UPDATE of one row; two scans cannot overwrite each other
                        repo.markScanned(sessionId, code)
                    } catch (t: Throwable) {
                        Log.e(TAG, "Can't save the scanned code", t)
                    }
                }
            }
        }
    }

    val loaded = session
    if (loaded == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(id = R.string.session_not_found))
        }
        return
    }

    val progressText = stringResource(
        id = R.string.progress_format,
        loaded.scannedCodes.size,
        loaded.codes.size
    )

    Scaffold { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            if (hasPermission) {
                CameraPreview(onCodeScanned = { code -> onCodeScanned(code) })
            }

            // title (session name)
            Text(
                loaded.name,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = padding.calculateTopPadding() + 4.dp),
                style = MaterialTheme.typography.headlineMedium
            )

            // bottom row: two buttons + progress in center
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(
                        start = 4.dp,
                        end = 4.dp,
                        bottom = padding.calculateBottomPadding() + 8.dp
                    )
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Button(
                    onClick = {
                        navController.navigate(Screen.CodesList.createRoute(sessionId, TYPE_SCANNED))
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp),
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = scannedButtonText,
                        maxLines = 1,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Text(
                    text = progressText,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

                Button(
                    onClick = {
                        navController.navigate(
                            Screen.CodesList.createRoute(sessionId, TYPE_NOT_SCANNED)
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp),
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = notScannedButtonText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            if (!hasPermission) {
                Text(
                    text = noCameraPermissionText,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(bottom = padding.calculateBottomPadding() + 4.dp),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.displayMedium
                )
            }

            // FEEDBACK OVERLAY (animated)
            AnimatedVisibility(
                visible = feedback != null,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = padding.calculateTopPadding() + 64.dp)
            ) {
                val f = feedback
                if (f != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .wrapContentHeight()
                            .background(color = f.color, shape = MaterialTheme.shapes.small)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = f.message,
                            color = Color.White,
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

/**
 * Camera preview bound to the current lifecycle.
 *
 * Analysis runs on its own executor: decoding a frame takes tens of milliseconds and
 * would otherwise block the UI thread on every frame. Both the executor and the camera
 * binding are released when this leaves the composition, so the camera does not keep
 * running after navigating away.
 */
@Composable
private fun CameraPreview(onCodeScanned: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    val previewView = remember {
        PreviewView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
    }
    val currentOnCodeScanned by rememberUpdatedState(onCodeScanned)
    val mainExecutor = remember(context) { ContextCompat.getMainExecutor(context) }

    DisposableEffect(lifecycleOwner) {
        // owned by this effect: restarting it must not reuse an executor already shut down
        val analysisExecutor = Executors.newSingleThreadExecutor()
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        var boundProvider: ProcessCameraProvider? = null

        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                boundProvider = cameraProvider

                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }

                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { analysis ->
                        analysis.setAnalyzer(
                            analysisExecutor,
                            ZxingQrCodeAnalyzer { text ->
                                mainExecutor.execute { currentOnCodeScanned(text) }
                            }
                        )
                    }

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analysis
                )
            } catch (e: Exception) {
                Log.e(TAG, "Camera launch error", e)
            }
        }, mainExecutor)

        onDispose {
            boundProvider?.unbindAll()
            analysisExecutor.shutdown()
        }
    }

    AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
}

/**
 * Decodes QR codes from camera frames. One reader per instance: [MultiFormatReader]
 * keeps internal state and is not safe to share between threads.
 */
class ZxingQrCodeAnalyzer(
    private val onQrCodeDetected: (String) -> Unit
) : ImageAnalysis.Analyzer {

    private val reader = MultiFormatReader().apply {
        setHints(
            mapOf(
                DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE)
            )
        )
    }

    @androidx.annotation.OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val image = imageProxy.image
        if (image == null || image.format != ImageFormat.YUV_420_888 || image.planes.size < 3) {
            imageProxy.close()
            return
        }

        try {
            val yPlane = image.planes[0]
            val yBuffer = yPlane.buffer
            yBuffer.rewind()
            val rowStride = yPlane.rowStride
            val width = image.width
            val height = image.height

            val yData = ByteArray(width * height)
            if (rowStride == width) {
                yBuffer.get(yData)
            } else {
                // if stride != width - copy line by line
                for (row in 0 until height) {
                    yBuffer.position(row * rowStride)
                    yBuffer.get(yData, row * width, width)
                }
            }

            val source = PlanarYUVLuminanceSource(
                yData, width, height, 0, 0, width, height, false
            )

            try {
                val result = reader.decode(BinaryBitmap(HybridBinarizer(source)))
                onQrCodeDetected(result.text)
            } catch (_: NotFoundException) {
                // QR code not found in this frame
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Frame analysis failed", t)
        } finally {
            imageProxy.close()
        }
    }
}
