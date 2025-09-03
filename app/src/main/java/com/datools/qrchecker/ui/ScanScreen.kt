package com.datools.qrchecker.ui

import android.Manifest
import android.graphics.ImageFormat
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
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
import com.datools.qrchecker.Screen
import com.datools.qrchecker.model.SessionData
import com.datools.qrchecker.data.SessionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer
import com.datools.qrchecker.R
import com.datools.qrchecker.ui.theme.Success
import com.datools.qrchecker.ui.theme.Warning
import com.datools.qrchecker.ui.theme.Error

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(
    navController: NavController,
    sessionId: String
) {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    var session by remember { mutableStateOf<SessionData?>(null) }
    val repo = remember { SessionRepository(context) }
    val view = LocalView.current

    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    var hasPermission by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }

    LaunchedEffect(Unit) { permissionLauncher.launch(Manifest.permission.CAMERA) }

    // load session
    LaunchedEffect(sessionId) {
        session = repo.getById(sessionId)
    }

    BackHandler {
        navController.navigate(Screen.Home.route) {
            popUpTo(Screen.Home.route) { inclusive = true }
        }
    }

    if (session == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(id = R.string.session_not_found))
        }
        return
    }

    val scannedCount = session!!.scannedCodes.size
    val totalCount = session!!.codes.size

    // ----- FEEDBACK STATE -----
    data class UiFeedback(
        val message: String,
        val color: Color,
        val vibrationMs: Long,
        val code: String?
    )

    var feedback by remember { mutableStateOf<UiFeedback?>(null) }
    val scope = rememberCoroutineScope()
    val vibrator = remember {
        ContextCompat.getSystemService(context, Vibrator::class.java)
    }
    var lastFeedbackAt by remember { mutableLongStateOf(0L) }
    val cooldownMs = 1000L
    val displayMs = 1200L
    var lastShownCode by remember { mutableStateOf<String?>(null) }

    // localized strings
    val alreadyScannedMsg = stringResource(id = R.string.msg_already_scanned)
    val scannedMsg = stringResource(id = R.string.msg_scanned)
    val notFoundMsg = stringResource(id = R.string.msg_not_in_list)
    val scannedButtonText = stringResource(id = R.string.btn_scanned)
    val notScannedButtonText = stringResource(id = R.string.btn_not_scanned)
    val progressText = stringResource(id = R.string.progress_format, scannedCount, totalCount)
    val noCameraPermissionText = stringResource(id = R.string.no_camera_permission)

    fun showFeedback(message: String, color: Color, vibrMs: Long = 40L, code: String? = null) {
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
        feedback = UiFeedback(message, color, vibrMs, code)

        try {
            vibrator?.takeIf { if (Build.VERSION.SDK_INT >= 26) it.hasVibrator() else true }?.let {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    it.vibrate(
                        VibrationEffect.createOneShot(
                            vibrMs,
                            VibrationEffect.DEFAULT_AMPLITUDE
                        )
                    )
                } else {
                    @Suppress("DEPRECATION")
                    it.vibrate(vibrMs)
                }
            }
        } catch (t: Throwable) {
            Log.w("LogCat", "Vibrate failed: ${t.message}")
        }

        scope.launch {
            delay(displayMs)
            // reset only if it is the same feedback
            if (feedback?.code == code) feedback = null
        }
    }

    // ----------------------------

    Scaffold { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(
                factory = { ctx ->
                    val previewView = PreviewView(ctx).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }

                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()

                        val preview = Preview.Builder().build().also {
                            it.surfaceProvider = previewView.surfaceProvider
                        }

                        val analyzer = ImageAnalysis.Builder().build().also { analysis ->
                            analysis.setAnalyzer(
                                ContextCompat.getMainExecutor(ctx),
                                ZxingQrCodeAnalyzer { result ->
                                    val code = result.text
                                    val normalizedCode = code.replace("\n", "")
                                        .replace("\r", "")
                                        .replace(Regex("\\p{C}"), "")

                                    // 1) code present in list?
                                    if (normalizedCode in session!!.codes) {
                                        // 1a) already scanned?
                                        if (normalizedCode in session!!.scannedCodes) {
                                            showFeedback(
                                                alreadyScannedMsg,
                                                Warning,
                                                30L,
                                                normalizedCode
                                            )
                                        } else {
                                            val newScanned = session!!.scannedCodes + normalizedCode
                                            val updatedSession =
                                                session!!.copy(scannedCodes = newScanned)
                                            session = updatedSession

                                            // save in DB
                                            CoroutineScope(Dispatchers.IO).launch {
                                                try {
                                                    repo.update(updatedSession)
                                                } catch (t: Throwable) {
                                                    Log.e("LogCat", "Can't save session", t)
                                                }
                                            }

                                            showFeedback(
                                                scannedMsg,
                                                Success,
                                                60L,
                                                normalizedCode
                                            )
                                            Log.d("LogCat", "Found new QR: $normalizedCode")
                                        }
                                    } else {
                                        showFeedback(
                                            notFoundMsg,
                                            Error,
                                            120L,
                                            normalizedCode
                                        )
                                    }
                                }
                            )
                        }

                        try {
                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                preview,
                                analyzer
                            )
                        } catch (exc: Exception) {
                            Log.e("LogCat", "Camera launch error", exc)
                        }
                    }, ContextCompat.getMainExecutor(ctx))

                    previewView
                },
                modifier = Modifier.fillMaxSize()
            )

            // title (session name)
            Text(
                session!!.name,
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
                        navController.navigate(Screen.CodesList.createRoute(sessionId, "scanned"))
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
                            Screen.CodesList.createRoute(
                                sessionId,
                                "not_scanned"
                            )
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
 * Analyzer — returns ZXing Result to callback.
 */
class ZxingQrCodeAnalyzer(
    private val onQrCodesDetected: (Result) -> Unit
) : ImageAnalysis.Analyzer {

    companion object {
        private val reader = MultiFormatReader().apply {
            setHints(mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE)))
        }
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
                yData,
                width,
                height,
                0,
                0,
                width,
                height,
                false
            )

            val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
            try {
                val result = reader.decode(binaryBitmap)
                onQrCodesDetected(result)
            } catch (_: NotFoundException) {
                // QR code not found
            }

        } finally {
            imageProxy.close()
        }
    }
}