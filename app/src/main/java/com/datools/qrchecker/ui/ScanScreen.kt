package com.datools.qrchecker.ui

import android.Manifest
import android.content.pm.PackageManager
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
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
import com.datools.qrchecker.navigateOnce
import com.datools.qrchecker.R
import com.datools.qrchecker.Screen
import com.datools.qrchecker.TYPE_NOT_SCANNED
import com.datools.qrchecker.TYPE_SCANNED
import com.datools.qrchecker.data.SessionRepository
import com.datools.qrchecker.model.SessionData
import androidx.compose.material3.MaterialTheme
import com.datools.qrchecker.ui.theme.OnColor
import com.datools.qrchecker.ui.theme.accents
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.HorizontalDivider
import com.datools.qrchecker.util.shortCode
import com.datools.qrchecker.util.normalizeCode
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.datools.qrchecker.util.SessionBackup
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

/** Сколько подсказок показывать: экран телефона всё равно не вместит больше. */
private const val MANUAL_SUGGESTIONS = 30

private const val BACKUP_DEBOUNCE_MS = 5_000L

private const val TAG = "QRChecker"

private data class UiFeedback(
    val message: String,
    val color: OnColor,
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
    val accents = MaterialTheme.accents
    val view = LocalView.current

    var session by remember { mutableStateOf<SessionData?>(null) }

    // ----- feedback state (declared before any early return so the composition
    // ----- keeps a stable set of remembered slots) -----
    var feedback by remember { mutableStateOf<UiFeedback?>(null) }
    var lastFeedbackAt by remember { mutableLongStateOf(0L) }
    var lastShownCode by remember { mutableStateOf<String?>(null) }

    // a torn or smudged label is otherwise a dead end
    var manualCode by remember { mutableStateOf<String?>(null) }
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
        // по времени открытия упорядочен список на главной: то, чем заняты сейчас,
        // должно быть сверху, а не то, что завели раньше всех
        repo.touchOpened(sessionId)
    }

    // Копия пишется через паузу после последней отметки, а не на каждую: партия бывает
    // на тысячи коробок, и файл на каждый скан положил бы сканирование.
    val scannedCount = session?.scannedCodes?.size
    LaunchedEffect(scannedCount) {
        val current = session ?: return@LaunchedEffect
        if (scannedCount == null || scannedCount == 0) return@LaunchedEffect
        delay(BACKUP_DEBOUNCE_MS)
        SessionBackup.autoSave(context, current)
    }

    // ...и ещё раз при уходе с экрана, чтобы отметки последних секунд не остались только
    // в базе. Область композиции здесь уже отменена, поэтому запись идёт на области
    // приложения.
    val sessionAtDispose by rememberUpdatedState(session)
    DisposableEffect(Unit) {
        onDispose {
            sessionAtDispose?.let { SessionBackup.scheduleSave(context, it) }
        }
    }

    BackHandler {
        navController.navigateOnce(Screen.Home.route) {
            popUpTo(Screen.Home.route) { inclusive = true }
        }
    }

    val cooldownMs = 1000L
    val displayMs = 1200L
    var hideFeedbackJob by remember { mutableStateOf<Job?>(null) }

    // localized strings
    val alreadyScannedMsg = stringResource(id = R.string.msg_already_scanned)
    val scannedMsg = stringResource(id = R.string.msg_scanned)
    val notFoundMsg = stringResource(id = R.string.msg_not_in_list)
    val scannedButtonText = stringResource(id = R.string.btn_scanned)
    val notScannedButtonText = stringResource(id = R.string.btn_not_scanned)
    val noCameraPermissionText = stringResource(id = R.string.no_camera_permission)
    val manualEntryCd = stringResource(id = R.string.cd_manual_entry)
    val manualEntryButton = stringResource(id = R.string.manual_entry_button)
    val manualEntryTitle = stringResource(id = R.string.manual_entry_title)
    val manualEntryLabel = stringResource(id = R.string.manual_entry_label)
    val manualEntryConfirm = stringResource(id = R.string.manual_entry_confirm)
    val manualEntryHint = stringResource(id = R.string.manual_entry_hint)
    val manualEntryNoMatches = stringResource(id = R.string.manual_entry_no_matches)
    val alreadyScannedLabel = stringResource(id = R.string.manual_entry_already_scanned)
    val cancelText = stringResource(id = R.string.delete_cancel)

    fun showFeedback(message: String, color: OnColor, vibrMs: Long, code: String?) {
        val now = System.currentTimeMillis()

        // Гасится повтор одного и того же кода, а не любое сообщение подряд.
        //
        // Раньше здесь стояло «висит плашка - новую не показываем», и получалось вот что:
        // камера продолжает видеть уже отмеченный код и шлёт его снова, человек переводит
        // её на следующий, тот отмечается, но его плашку глушит ещё висящая предыдущая, а
        // когда та гаснет - выскакивает оранжевая от очередного повтора первого кода.
        // Приложение показывало ответ не на то действие, которое человек только что сделал.
        if (code != null && code == lastShownCode && (now - lastFeedbackAt) < cooldownMs) return
        if (code == null && (now - lastFeedbackAt) < cooldownMs) return

        lastShownCode = code
        lastFeedbackAt = now

        // новая плашка вытесняет прежнюю, и её таймер отменяется вместе с ней
        hideFeedbackJob?.cancel()
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

        hideFeedbackJob = scope.launch {
            delay(displayMs)
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
                showFeedback(notFoundMsg, accents.danger, 120L, code)

            code in current.scannedCodes ->
                showFeedback(alreadyScannedMsg, accents.warning, 30L, code)

            else -> {
                // одно и то же время идёт и в базу, и в состояние экрана: с него потом
                // снимается копия сессии, и разъехавшись они увезли бы в файл отметки
                // без времени
                val at = System.currentTimeMillis()
                session = current.copy(
                    scannedCodes = current.scannedCodes + code,
                    scanTimes = current.scanTimes.orEmpty() + (code to at)
                )
                showFeedback(scannedMsg, accents.success, 60L, code)
                scope.launch {
                    try {
                        // a single UPDATE of one row; two scans cannot overwrite each other
                        repo.markScanned(sessionId, code, at)
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

            // Подписанная кнопка, а не голый карандаш: по значку невозможно догадаться,
            // что за ним ручной ввод кода, и его принимали за правку сессии
            Button(
                onClick = { manualCode = "" },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = padding.calculateTopPadding() + 4.dp, end = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = manualEntryCd,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(manualEntryButton, style = MaterialTheme.typography.labelLarge)
            }

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
                        navController.navigateOnce(Screen.CodesList.createRoute(sessionId, TYPE_SCANNED))
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
                        navController.navigateOnce(
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

            manualCode?.let { typed ->
                // Набрать 31 знак кода маркировки руками невозможно, поэтому ввод здесь -
                // это поиск по кодам сессии, а не набор. Неотмеченные идут первыми: их и
                // ищут. Отмеченные остаются в списке, потому что коробка могла приехать
                // дважды, и человеку нужно это увидеть, а не гадать, куда делся код.
                val query = typed.trim()
                val suggestions = remember(loaded, query) {
                    val scanned = loaded.scannedCodes.toHashSet()
                    loaded.codes
                        .asSequence()
                        .filter { query.isEmpty() || it.contains(query, ignoreCase = true) }
                        .sortedBy { it in scanned }
                        .take(MANUAL_SUGGESTIONS)
                        .map { it to (it in scanned) }
                        .toList()
                }

                AlertDialog(
                    onDismissRequest = { manualCode = null },
                    title = { Text(manualEntryTitle) },
                    text = {
                        Column {
                            OutlinedTextField(
                                value = typed,
                                onValueChange = { manualCode = it.filterNot { ch -> ch == '\n' } },
                                label = { Text(manualEntryLabel) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = manualEntryHint,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            if (suggestions.isEmpty()) {
                                Text(
                                    text = manualEntryNoMatches,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                LazyColumn(modifier = Modifier.heightIn(max = 240.dp)) {
                                    items(suggestions, key = { it.first }) { (code, isScanned) ->
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    manualCode = null
                                                    // тот же путь, что у кадра с камеры,
                                                    // чтобы отклик был тем же самым
                                                    onCodeScanned(code)
                                                }
                                                .padding(vertical = 8.dp)
                                        ) {
                                            Text(
                                                text = shortCode(code),
                                                style = MaterialTheme.typography.bodyLarge,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                color = if (isScanned) {
                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                                } else {
                                                    MaterialTheme.colorScheme.onSurface
                                                }
                                            )
                                            if (isScanned) {
                                                Text(
                                                    text = alreadyScannedLabel,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                        HorizontalDivider()
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(
                            enabled = typed.isNotBlank(),
                            onClick = {
                                manualCode = null
                                onCodeScanned(typed)
                            }
                        ) { Text(manualEntryConfirm) }
                    },
                    dismissButton = {
                        TextButton(onClick = { manualCode = null }) { Text(cancelText) }
                    }
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
                            .background(color = f.color.container, shape = MaterialTheme.shapes.small)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = f.message,
                            color = f.color.content,
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
        val analyzer = MlKitQrCodeAnalyzer { text ->
            mainExecutor.execute { currentOnCodeScanned(text) }
        }
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
                    .also { it.setAnalyzer(analysisExecutor, analyzer) }

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
            analyzer.close()
            analysisExecutor.shutdown()
        }
    }

    AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
}

/**
 * Decodes QR codes from camera frames with ML Kit.
 *
 * The frame is handed over as-is together with its rotation, so codes are read at any
 * orientation without copying and rebuilding the luminance plane by hand.
 */
class MlKitQrCodeAnalyzer(
    private val onQrCodeDetected: (String) -> Unit
) : ImageAnalysis.Analyzer, AutoCloseable {

    private val scanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            // must match what the PDF parser reads, or a scanned label never
            // matches its entry in the session
            .setBarcodeFormats(Barcode.FORMAT_DATA_MATRIX, Barcode.FORMAT_QR_CODE)
            .build()
    )

    @androidx.annotation.OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                barcodes.firstNotNullOfOrNull { it.rawValue }?.let(onQrCodeDetected)
            }
            .addOnFailureListener { Log.w(TAG, "Frame analysis failed", it) }
            // the frame must stay open until ML Kit is done with it
            .addOnCompleteListener { imageProxy.close() }
    }

    override fun close() = scanner.close()
}
