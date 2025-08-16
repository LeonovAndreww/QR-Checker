package com.datools.qrchecker.ui

import android.graphics.*
import android.util.Log
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.datools.qrchecker.model.SessionData
import com.datools.qrchecker.util.SessionManager
import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(
    navController: NavController,
    sessionId: String
) {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    var session by remember { mutableStateOf<SessionData?>(null) }
    //var lastScanned by remember { mutableStateOf("") }

    LaunchedEffect(sessionId) {
        session = SessionManager().loadSession(context, sessionId)
    }

    if (session == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("Сессия не найдена")
        }
        return
    }

    val scannedCount = session!!.scannedCodes.size
    val totalCount = session!!.codes.size

    Scaffold { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
        ) {
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
                                    val normalizedCode = code.replace("\n", "").replace("\r", "")
                                        .replace(Regex("\\p{C}"), "")
                                    //lastScanned = normalizedCode

                                    if (normalizedCode in session!!.codes) {
                                        if (normalizedCode in session!!.codes && normalizedCode !in session!!.scannedCodes) {
                                            val newScanned =
                                                (session!!.scannedCodes + normalizedCode).toMutableList()  // создаём новый мутабельный список
                                            session =
                                                session!!.copy(scannedCodes = newScanned)  // обновляем state
                                            SessionManager().saveSession(ctx, session!!)
                                            Log.d(
                                                "LogCat",
                                                "Найден новый QR из PDF: $normalizedCode"
                                            )
                                        } else {
                                            Log.d(
                                                "LogCat",
                                                "QR уже отмечен, но камера его видит: $normalizedCode"
                                            )
                                        }
                                    } else {
                                        Log.d("LogCat", "QR не из PDF: $normalizedCode")
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
                            Log.e("LogCat", "Ошибка запуска камеры", exc)
                        }
                    }, ContextCompat.getMainExecutor(ctx))

                    previewView
                },
                modifier = Modifier
                    .fillMaxSize()
            )
            Text(
                session!!.name, modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = padding.calculateTopPadding() + 4.dp),
                style = MaterialTheme.typography.headlineMedium
            )

            Text(
                text = "Прогресс:\n$scannedCount / $totalCount",
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = padding.calculateBottomPadding() + 4.dp),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge
            )
        }

    }
}

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
        try {
            val image = imageProxy.image ?: return
            if (image.format != ImageFormat.YUV_420_888 || image.planes.size != 3) return

            val yBuffer = image.planes[0].buffer
            val yBytes = ByteArray(yBuffer.remaining())
            yBuffer.get(yBytes)

            val source = PlanarYUVLuminanceSource(
                yBytes,
                image.width,
                image.height,
                0,
                0,
                image.width,
                image.height,
                false
            )

            val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
            try {
                val result = reader.decode(binaryBitmap)
                onQrCodesDetected(result)
            } catch (_: NotFoundException) { /* QR не найден */
            }
        } finally {
            imageProxy.close()
        }
    }
}
