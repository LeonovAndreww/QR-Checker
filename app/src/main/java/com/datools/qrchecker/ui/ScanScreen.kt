package com.datools.qrchecker.ui

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.ui.geometry.Rect as GeomRect
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import android.graphics.Rect
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.HorizontalDivider
import com.datools.qrchecker.util.formatTimeAgo
import com.datools.qrchecker.util.shortCode
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import com.datools.qrchecker.util.shareSessionFile
import com.datools.qrchecker.popBackStackOnce
import com.datools.qrchecker.util.normalizeCode
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.datools.qrchecker.util.Outcome
import com.datools.qrchecker.util.rememberFeedback
import com.datools.qrchecker.util.SessionBackup
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/** Сколько подсказок показывать: экран телефона всё равно не вместит больше. */
private const val MANUAL_SUGGESTIONS = 30

private const val BACKUP_DEBOUNCE_MS = 5_000L

/** Как ведёт себя колечко на месте нажатия и сколько камера держит наведённый фокус. */
private const val FOCUS_RING_IN_MS = 180
private const val FOCUS_RING_HOLD_MS = 450L
private const val FOCUS_RING_OUT_MS = 260
private const val FOCUS_HOLD_SECONDS = 4L

/**
 * Как показывать найденный код.
 *
 * Переключается здесь, а не в настройках: это выбор оформления, который делается один
 * раз при сборке, а не то, в чём человек у ленты станет разбираться.
 */
private enum class ViewfinderStyle {
    /**
     * Рамка стоит на месте, у кода своя обводка. Видно и то, куда наводить, и что
     * именно приняли, - обе рамки в кадре одновременно.
     */
    FIXED,

    /** Уголки рамки съезжаются к коду и едут за ним, пока он в кадре. */
    LATCHING
}

private val VIEWFINDER_STYLE = ViewfinderStyle.FIXED

/** Сколько висит пузырёк с кратностью после того, как щипок закончился. */
private const val ZOOM_BUBBLE_MS = 1200L

/** «1x», «2.5x», «0.6x» - так их подписывают в камерах. */
internal fun formatZoom(ratio: Float): String {
    val rounded = (ratio * 10f).roundToInt() / 10f
    val text = if (rounded % 1f == 0f) rounded.toInt().toString() else rounded.toString()
    return "${text}x"
}

/** Как часто проверяется, держат ли ту же коробку перед камерой. */
private const val FEEDBACK_HOLD_CHECK_MS = 150L

/** Насколько плавно рамка переезжает к найденному коду и обратно в центр. */
private const val FRAME_SNAP_MS = 260

/** Сколько рамка ждёт код, пропавший из кадра, прежде чем вернуться в центр. */
private const val FRAME_RELEASE_MS = 700L
private const val FRAME_RELEASE_CHECK_MS = 120L

/** Сколько места занимает самая широкая группа кнопок в верхней полосе. */
private val TOP_BAR_SIDE = 112.dp

/** Высота затемнения под верхней и нижней полосами управления. */
private val CONTROL_SCRIM = 140.dp

/**
 * Подписи на двух нижних кнопках подбирают размер под ширину.
 *
 * «Неотсканированные» - слово из семнадцати букв, и на узком экране оно не влезает ни
 * при каком фиксированном кегле; обрезать его нельзя - обе кнопки перестают читаться.
 */
private val BUTTON_TEXT_SIZE = TextAutoSize.StepBased(
    minFontSize = 11.sp,
    maxFontSize = 16.sp,
    stepSize = 0.5.sp
)

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
    // какой код камера видит прямо сейчас и когда она его видела в последний раз
    var presentCode by remember { mutableStateOf<String?>(null) }
    var presentSeenAt by remember { mutableLongStateOf(0L) }

    // a torn or smudged label is otherwise a dead end
    var manualCode by remember { mutableStateOf<String?>(null) }
    var torchOn by remember { mutableStateOf(false) }
    // кратность и когда её последний раз меняли: пузырёк живёт секунду после щипка
    var zoomRatio by remember { mutableFloatStateOf(0f) }
    var zoomShownAt by remember { mutableLongStateOf(0L) }
    var zoomVisible by remember { mutableStateOf(false) }
    // одна прокрутка вбок на весь список подсказок ручного ввода
    val suggestionScroll = rememberScrollState()
    var hasTorch by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val feel = rememberFeedback(withSound = true)

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

    val awayMs = 2500L
    val displayMs = 1200L
    var hideFeedbackJob by remember { mutableStateOf<Job?>(null) }

    // localized strings
    val alreadyScannedMsg = stringResource(id = R.string.msg_already_scanned)
    val alreadyScannedAgoTemplate = stringResource(id = R.string.msg_already_scanned_ago)
    val scannedMsg = stringResource(id = R.string.msg_scanned)
    val notFoundMsg = stringResource(id = R.string.msg_not_in_list)
    val scannedButtonText = stringResource(id = R.string.btn_scanned)
    val notScannedButtonText = stringResource(id = R.string.btn_not_scanned)
    val noCameraPermissionText = stringResource(id = R.string.no_camera_permission)
    val manualEntryCd = stringResource(id = R.string.cd_manual_entry)
    val manualEntryButton = stringResource(id = R.string.manual_entry_button)
    val backCd = stringResource(id = R.string.cd_back)
    val shareSessionCd = stringResource(id = R.string.share_session)
    val manualEntryTitle = stringResource(id = R.string.manual_entry_title)
    val manualEntryLabel = stringResource(id = R.string.manual_entry_label)
    val manualEntryConfirm = stringResource(id = R.string.manual_entry_confirm)
    val manualEntryHint = stringResource(id = R.string.manual_entry_hint)
    val manualEntryNoMatches = stringResource(id = R.string.manual_entry_no_matches)
    val alreadyScannedLabel = stringResource(id = R.string.manual_entry_already_scanned)
    val cancelText = stringResource(id = R.string.delete_cancel)
    val shareFailedText = stringResource(id = R.string.session_share_failed)
    val torchOnCd = stringResource(id = R.string.cd_torch_on)
    val torchOffCd = stringResource(id = R.string.cd_torch_off)

    fun showFeedback(message: String, color: OnColor, outcome: Outcome, code: String?) {
        // новая плашка вытесняет прежнюю, и её таймер отменяется вместе с ней
        hideFeedbackJob?.cancel()
        feedback = UiFeedback(message, color, code)
        feel(outcome)

        hideFeedbackJob = scope.launch {
            // Сначала показать положенное время, а дальше держать, пока код в кадре.
            //
            // Плашка - ответ про ту коробку, что человек сейчас держит перед камерой;
            // пока она там, ответ остаётся верным, и убирать его незачем.
            delay(displayMs)
            while (
                code != null &&
                presentCode == code &&
                System.currentTimeMillis() - presentSeenAt < awayMs
            ) {
                delay(FEEDBACK_HOLD_CHECK_MS)
            }
            if (feedback?.code == code) feedback = null
        }
    }

    // Runs on the main thread (posted from the analyzer), so reading and updating
    // `session` here cannot interleave with another decoded frame.
    fun onCodeScanned(rawCode: String, fromCamera: Boolean = true) {
        val current = session ?: return
        val code = normalizeCode(rawCode)
        if (code.isEmpty()) return

        // Пока камера смотрит на ту же коробку, ответ даётся один раз.
        //
        // Раньше здесь стоял таймер от последнего показа, и выходило так: код отметился
        // зелёным, через секунду тот же кадр приезжал снова - и уже отмеченный код
        // отвечал оранжевым «был отсканирован», и так по кругу с вибрацией, пока камеру
        // не уведёшь. Считается не время с показа, а время с последнего кадра: коробку
        // убрали из кадра и вернули - это новое предъявление, и на него надо ответить.
        val now = System.currentTimeMillis()
        val stillHere = fromCamera && code == presentCode && (now - presentSeenAt) < awayMs
        presentCode = if (fromCamera) code else null
        presentSeenAt = now
        if (stillHere) return

        when {
            code !in current.codes ->
                showFeedback(notFoundMsg, accents.danger, Outcome.FAILURE, code)

            code in current.scannedCodes -> {
                // не просто «уже был», а когда именно: между «пять секунд назад» и
                // «вчера в 17:55» разница в том, пересчитывает человек ту же коробку
                // прямо сейчас или наткнулся на позавчерашнюю
                val at = current.scanTimes?.get(code)
                val message = if (at == null) {
                    alreadyScannedMsg
                } else {
                    alreadyScannedAgoTemplate.format(formatTimeAgo(context, at))
                }
                showFeedback(message, accents.warning, Outcome.REPEAT, code)
            }

            else -> {
                // одно и то же время идёт и в базу, и в состояние экрана: с него потом
                // снимается копия сессии, и разъехавшись они увезли бы в файл отметки
                // без времени
                val at = System.currentTimeMillis()
                session = current.copy(
                    scannedCodes = current.scannedCodes + code,
                    scanTimes = current.scanTimes.orEmpty() + (code to at)
                )
                showFeedback(scannedMsg, accents.success, Outcome.SUCCESS, code)
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

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            if (hasPermission) {
                CameraPreview(
                    highlightColor = feedback?.color?.container,
                    // состояние кода, а не ответ на действие: отмеченный - зелёный,
                    // чужой - красный, а тот, что в списке и ещё не отмечен, цвета
                    // не имеет, потому что сказать о нём пока нечего
                    colorOf = { code ->
                        val current = session
                        when {
                            current == null -> null
                            code !in current.codes -> accents.danger.container
                            code in current.scannedCodes -> accents.success.container
                            else -> null
                        }
                    },
                    torchOn = torchOn,
                    onTorchAvailable = { hasTorch = it },
                    // нажали по кадру - спрашиваем заново даже про тот же код
                    onTapped = { presentCode = null },
                    onZoomChanged = { ratio ->
                        zoomRatio = ratio
                        zoomShownAt = System.currentTimeMillis()
                    },
                    onCodeSeen = { code ->
                        if (code == presentCode) presentSeenAt = System.currentTimeMillis()
                    },
                    onCodeScanned = { code -> onCodeScanned(code) }
                )

                // Затемнение под полосами с кнопками.
                //
                // Управление лежит поверх живого кадра, а кадр бывает любой яркости, так
                // что цвет из темы на нём то читается, то нет: в светлой теме чёрные
                // буквы пропадали на тёмном кадре, в тёмной - белые на светлом. Здесь
                // фон делается предсказуемым, а буквы ниже - всегда белыми.
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .height(CONTROL_SCRIM)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent)
                            )
                        )
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(CONTROL_SCRIM)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f))
                            )
                        )
                )
            }

            // Одна полоса поверх камеры: слева «назад», справа действия, название -
            // ровно по центру экрана.
            //
            // Раньше название стояло в общем ряду и делило место с кнопками: слева от
            // него была одна стрелка, справа - значок и кнопка, и центр названия уезжал
            // влево тем сильнее, чем шире кнопка. Здесь кнопки лежат поверх, а название
            // центрируется по всей ширине и отступает от краёв на ширину самой широкой
            // стороны - так оно и по центру, и ни на что не налезает.
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(top = padding.calculateTopPadding() + 4.dp)
                    .padding(horizontal = 4.dp)
            ) {
                Text(
                    loaded.name,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = TOP_BAR_SIDE),
                    color = Color.White,
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                IconButton(
                    onClick = { navController.popBackStackOnce() },
                    modifier = Modifier.align(Alignment.CenterStart)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = backCd,
                        tint = Color.White
                    )
                }

                Row(
                    modifier = Modifier.align(Alignment.CenterEnd),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Фонарь - первым: на складе между стеллажами и в кузове темно, а
                    // без света Data Matrix не читается вообще никак. Кнопка есть
                    // только там, где вспышка есть у камеры.
                    if (hasTorch) {
                        IconButton(onClick = { torchOn = !torchOn }) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_torch_on),
                                contentDescription = if (torchOn) torchOffCd else torchOnCd,
                                tint = if (torchOn) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    Color.White
                                }
                            )
                        }
                    }

                    // «Поделиться» живёт здесь, а не в правке сессии: это действие над
                    // той сессией, с которой человек сейчас работает, и искать его в
                    // настройках никто не станет
                    IconButton(onClick = {
                        try {
                            context.startActivity(shareSessionFile(context, loaded))
                        } catch (t: Throwable) {
                            // молчать нельзя: нажатие без единого следа читается как
                            // сломанная кнопка
                            Log.e(TAG, "Can't share the session", t)
                            scope.launch { snackbarHostState.showSnackbar(shareFailedText) }
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = shareSessionCd,
                            tint = Color.White
                        )
                    }

                    // клавиатура вместо карандаша и подписи: карандаш - это «поправить
                    // то, что есть», а здесь код набирают с нуля, и по клавиатуре это
                    // видно без слов. Заодно с экрана уходит синяя плашка, которая
                    // закрывала кадр и спорила со всем вокруг
                    IconButton(onClick = { manualCode = "" }) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_keyboard),
                            contentDescription = manualEntryCd,
                            tint = Color.White
                        )
                    }
                }
            }

            // Кратность показывается пузырьком, а не полосой с делениями: пальцы уже
            // заняты щипком, а знать нужно ровно одно - на какой объектив попал.
            LaunchedEffect(zoomShownAt) {
                if (zoomShownAt == 0L) return@LaunchedEffect
                zoomVisible = true
                delay(ZOOM_BUBBLE_MS)
                if (System.currentTimeMillis() - zoomShownAt >= ZOOM_BUBBLE_MS) {
                    zoomVisible = false
                }
            }

            AnimatedVisibility(
                visible = zoomVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = padding.calculateBottomPadding() + 88.dp)
            ) {
                Box(
                    modifier = Modifier
                        .background(
                            color = Color.Black.copy(alpha = 0.55f),
                            shape = MaterialTheme.shapes.large
                        )
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = formatZoom(zoomRatio),
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
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
                        // подпись занимает столько, сколько влезло, а не обрезается:
                        // «Неотсканирован...» - это не название кнопки
                        autoSize = BUTTON_TEXT_SIZE,
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Text(
                    text = progressText,
                    textAlign = TextAlign.Center,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
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
                        autoSize = BUTTON_TEXT_SIZE,
                        style = MaterialTheme.typography.titleMedium,
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
                                                    onCodeScanned(code, fromCamera = false)
                                                }
                                                .padding(vertical = 8.dp)
                                        ) {
                                            // Подсказки листаются вбок все разом, одним
                                            // состоянием на список: код длиннее экрана,
                                            // и по первым двум десяткам символов от
                                            // соседа его не отличить. У каждой строки
                                            // своя прокрутка означала бы, что строки
                                            // разъезжаются и сравнивать их уже нельзя.
                                            Text(
                                                text = shortCode(code),
                                                style = MaterialTheme.typography.bodyLarge,
                                                maxLines = 1,
                                                softWrap = false,
                                                modifier = Modifier.horizontalScroll(
                                                    suggestionScroll
                                                ),
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
                                onCodeScanned(typed, fromCamera = false)
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
 * Какой код найден и где он лежал в кадре.
 *
 * Прямоугольник нужен для двух вещей сразу: отсеять коды, не попавшие в рамку, и
 * подсветить тот, который приняли. Координаты - уже повёрнутого кадра, того самого,
 * который человек видит на экране.
 */
data class DetectedCode(
    val value: String,
    val box: Rect,
    val imageWidth: Int,
    val imageHeight: Int
)

/** Доля короткой стороны экрана, которую занимает сторона рамки видоискателя. */
private const val VIEWFINDER_FRACTION = 0.72f

/**
 * Пересчёт координат кадра в координаты экрана.
 *
 * PreviewView растягивает кадр по большей стороне и обрезает лишнее (FILL_CENTER), так
 * что доли кадра и доли экрана друг другу не равны. Без этого рамка на экране и область,
 * по которой идёт отбор, разъехались бы, и приложение принимало бы не то, что показывает.
 */
private class PreviewMapping(imageWidth: Int, imageHeight: Int, viewSize: IntSize) {
    private val scale = maxOf(
        viewSize.width.toFloat() / imageWidth,
        viewSize.height.toFloat() / imageHeight
    )
    private val dx = (viewSize.width - imageWidth * scale) / 2f
    private val dy = (viewSize.height - imageHeight * scale) / 2f

    fun toView(x: Float, y: Float) = Offset(x * scale + dx, y * scale + dy)

    fun toView(box: Rect) = ComposeRect(
        toView(box.left.toFloat(), box.top.toFloat()),
        toView(box.right.toFloat(), box.bottom.toFloat())
    )
}

private fun ComposeRect(topLeft: Offset, bottomRight: Offset) =
    androidx.compose.ui.geometry.Rect(topLeft, bottomRight)

/** Рамка видоискателя в координатах экрана: квадрат по центру. */
private fun viewfinderRect(viewSize: IntSize): androidx.compose.ui.geometry.Rect {
    val side = minOf(viewSize.width, viewSize.height) * VIEWFINDER_FRACTION
    val left = (viewSize.width - side) / 2f
    val top = (viewSize.height - side) / 2f
    return androidx.compose.ui.geometry.Rect(left, top, left + side, top + side)
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
private fun CameraPreview(
    highlightColor: Color?,
    /** Цвет по состоянию кода: он нужен и тогда, когда плашки ответа уже нет. */
    colorOf: (String) -> Color?,
    torchOn: Boolean,
    onTorchAvailable: (Boolean) -> Unit,
    onTapped: () -> Unit,
    /** Текущая кратность - её показывает уже сам экран. */
    onZoomChanged: (Float) -> Unit,
    /** Код виден в кадре - неважно, попал он в рамку или нет. */
    onCodeSeen: (String) -> Unit,
    onCodeScanned: (String) -> Unit
) {
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
    val currentOnTapped by rememberUpdatedState(onTapped)
    val currentOnCodeSeen by rememberUpdatedState(onCodeSeen)
    val currentOnZoomChanged by rememberUpdatedState(onZoomChanged)
    val mainExecutor = remember(context) { ContextCompat.getMainExecutor(context) }

    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    val currentViewSize by rememberUpdatedState(viewSize)
    // камера появляется не сразу: пока её нет, наводить нечего
    var camera by remember { mutableStateOf<Camera?>(null) }
    // куда ткнули: колечко живёт секунду, чтобы было видно, что нажатие приняли
    var focusAt by remember { mutableStateOf<Offset?>(null) }
    // Последний принятый код и его место в кадре.
    //
    // Пока камера продолжает его видеть, прямоугольник обновляется на каждом кадре -
    // рамка едет за коробкой. Время последнего появления нужно отдельно от плашки
    // ответа: плашка гаснет через секунду, а помеченным код остаётся всё то время, что
    // он в кадре.
    var acceptedCode by remember { mutableStateOf<String?>(null) }
    var acceptedBox by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    var acceptedSeenAt by remember { mutableLongStateOf(0L) }
    var acceptedColor by remember { mutableStateOf<Color?>(null) }

    // рамка видоискателя: одно анимируемое значение на все четыре стороны, чтобы она
    // переезжала к коду целиком, а не рывками по каждой границе отдельно
    val frame = remember {
        Animatable(GeomRect.Zero, GeomRect.VectorConverter)
    }
    val cornerColor by animateColorAsState(
        targetValue = acceptedColor ?: Color.White.copy(alpha = 0.9f),
        animationSpec = tween(FRAME_SNAP_MS),
        label = "corners"
    )
    val focusScale = remember { Animatable(1f) }
    val focusAlpha = remember { Animatable(0f) }

    DisposableEffect(lifecycleOwner) {
        // owned by this effect: restarting it must not reuse an executor already shut down
        val analysisExecutor = Executors.newSingleThreadExecutor()
        val analyzer = MlKitQrCodeAnalyzer { detected ->
            mainExecutor.execute {
                val size = currentViewSize
                if (size == IntSize.Zero) return@execute

                val mapping = PreviewMapping(detected.imageWidth, detected.imageHeight, size)
                // отбор идёт по неподвижному квадрату в центре, а не по нарисованной
                // рамке: та переезжает к коду, и вместе с ней уезжала бы область,
                // в которой приложение вообще что-либо принимает
                val target = viewfinderRect(size)
                val onScreen = mapping.toView(detected.box)

                // принимается только то, что человек навёл: центр кода внутри рамки.
                // Иначе соседняя коробка в кадре отмечалась бы молча и незаметно
                // Код в кадре - этого довольно, чтобы считать его тем же самым.
                //
                // Раньше это считалось только по кодам внутри рамки, и стоило коробке
                // чуть сместиться, как приложение решало, что её унесли и принесли
                // обратно, - и отвечало заново. Отсюда и попискивание над неподвижной
                // коробкой.
                currentOnCodeSeen(detected.value)

                // рамка уже принятого кода едет за ним, пока он в кадре
                if (detected.value == acceptedCode) {
                    acceptedBox = onScreen
                    acceptedSeenAt = System.currentTimeMillis()
                }

                if (!target.contains(onScreen.center)) return@execute

                acceptedCode = detected.value
                acceptedBox = onScreen
                acceptedSeenAt = System.currentTimeMillis()
                currentOnCodeScanned(detected.value)
            }
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
                camera = cameraProvider.bindToLifecycle(
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
            camera = null
            analyzer.close()
            analysisExecutor.shutdown()
        }
    }

    // Цвет рамки берётся у плашки, пока она висит, и у самого кода, когда её уже нет.
    //
    // Пока он брался только у плашки, выходило вот что: наводишь на код, ответ на него
    // придержан (та же коробка всё ещё в кадре, отвечать второй раз незачем) - и рамка
    // сжимается вокруг кода, оставаясь белой, будто ничего не нашла.
    LaunchedEffect(highlightColor, acceptedCode) {
        acceptedColor = highlightColor ?: acceptedCode?.let { colorOf(it) }
    }

    // Код ушёл из кадра - рамка отпускает его и возвращается в центр.
    //
    // Считается по последнему кадру с этим кодом, а не по таймеру ответа: пока коробка
    // перед камерой, она помечена, сколько бы ни прошло времени.
    LaunchedEffect(acceptedCode) {
        while (acceptedCode != null) {
            delay(FRAME_RELEASE_CHECK_MS)
            if (System.currentTimeMillis() - acceptedSeenAt > FRAME_RELEASE_MS) {
                acceptedCode = null
                acceptedBox = null
                acceptedColor = null
            }
        }
    }

    // Рамка переезжает к коду и обратно.
    //
    // К коду - пружиной, а не за фиксированное время: цель меняется на каждом кадре,
    // пока коробку держат в руках, и анимация запускается заново по многу раз в
    // секунду. Пружина при перезапуске подхватывает набранную скорость, поэтому рамка
    // едет за кодом плавно; постоянное время дёргало бы её с каждого кадра заново.
    // Обратно в центр возвращаться некуда спешить - там обычный переход.
    LaunchedEffect(acceptedBox, viewSize) {
        if (viewSize == IntSize.Zero) return@LaunchedEffect
        val home = viewfinderRect(viewSize)
        if (frame.value == GeomRect.Zero) frame.snapTo(home)

        val box = acceptedBox.takeIf { VIEWFINDER_STYLE == ViewfinderStyle.LATCHING }
        if (box == null) {
            frame.animateTo(home, tween(FRAME_SNAP_MS))
        } else {
            frame.animateTo(
                snapTarget(box, home),
                spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow)
            )
        }
    }

    // колечко фокуса: сжимается к точке и тает - так это показывают в камере телефона
    LaunchedEffect(focusAt) {
        val at = focusAt ?: return@LaunchedEffect
        focusScale.snapTo(1.35f)
        focusAlpha.snapTo(1f)
        focusScale.animateTo(1f, tween(FOCUS_RING_IN_MS))
        delay(FOCUS_RING_HOLD_MS)
        focusAlpha.animateTo(0f, tween(FOCUS_RING_OUT_MS))
        if (focusAt == at) focusAt = null
    }

    // фонарь включается только когда камера уже привязана
    LaunchedEffect(camera, torchOn) {
        val control = camera ?: return@LaunchedEffect
        onTorchAvailable(camera?.cameraInfo?.hasFlashUnit() == true)
        try {
            control.cameraControl.enableTorch(torchOn)
        } catch (t: Throwable) {
            Log.w(TAG, "Can't switch the torch", t)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { viewSize = it }
            // Автофокус ведёт по центру кадра, а код на коробке нередко сбоку и вплотную:
            // объектив цепляется за фон и не может собрать мелкую сетку Data Matrix.
            // Нажатие говорит камере, куда смотреть, - ровно так же, как в любой камере
            // телефона, поэтому этому и учить никого не надо.
            // Щипок - до нажатия в цепочке: он забирает событие себе, как только
            // пальцев становится двое, и одиночное нажатие после этого не считается
            // за наведение фокуса.
            //
            // Кратность отдаётся камере как отношение, а не как «цифровой зум»: на
            // телефонах с несколькими объективами система сама переключается между
            // ними на нужных значениях, и оптический зум работает как оптический.
            .pointerInput(camera) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        if (event.changes.size < 2) continue

                        val change = event.calculateZoom()
                        if (change == 1f) continue

                        val state = camera?.cameraInfo?.zoomState?.value
                        val control = camera?.cameraControl
                        if (state != null && control != null) {
                            val next = (state.zoomRatio * change)
                                .coerceIn(state.minZoomRatio, state.maxZoomRatio)
                            try {
                                control.setZoomRatio(next)
                                currentOnZoomChanged(next)
                            } catch (t: Throwable) {
                                Log.w(TAG, "Can't set the zoom ratio", t)
                            }
                        }
                        event.changes.forEach { it.consume() }
                    } while (event.changes.any { it.pressed })
                }
            }
            .pointerInput(Unit) {
                detectTapGestures { at ->
                    // нажатие - это ещё и «спроси заново»: человек наводится на ту же
                    // коробку, чтобы убедиться в её состоянии, и молчание в ответ
                    // означало бы, что приложение его не услышало
                    currentOnTapped()
                    focusAt = at

                    val control = camera?.cameraControl ?: return@detectTapGestures
                    val point = previewView.meteringPointFactory.createPoint(at.x, at.y)
                    try {
                        control.startFocusAndMetering(
                            FocusMeteringAction.Builder(point)
                                // фокус сам возвращается к автоматическому: иначе
                                // наведённая на одну коробку камера так и осталась бы
                                // сфокусированной на ней и на следующей
                                .setAutoCancelDuration(FOCUS_HOLD_SECONDS, TimeUnit.SECONDS)
                                .build()
                        )
                    } catch (t: Throwable) {
                        Log.w(TAG, "Can't focus at the tapped point", t)
                    }
                }
            }
    ) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

        Canvas(modifier = Modifier.fillMaxSize()) {
            if (size.width <= 0f || size.height <= 0f) return@Canvas
            val drawn = frame.value.takeIf { it.width > 0f && it.height > 0f }
                ?: viewfinderRect(IntSize(size.width.toInt(), size.height.toInt()))

            // затемнение вокруг рамки: показывает, где приложение смотрит, и заодно
            // подсказывает, куда наводить. Переезжает вместе с рамкой, поэтому светлое
            // окно всегда там же, где уголки
            val scrim = Color.Black.copy(alpha = 0.45f)
            drawRect(scrim, size = Size(size.width, drawn.top))
            drawRect(
                scrim,
                topLeft = Offset(0f, drawn.bottom),
                size = Size(size.width, size.height - drawn.bottom)
            )
            drawRect(
                scrim,
                topLeft = Offset(0f, drawn.top),
                size = Size(drawn.left, drawn.height)
            )
            drawRect(
                scrim,
                topLeft = Offset(drawn.right, drawn.top),
                size = Size(size.width - drawn.right, drawn.height)
            )

            when (VIEWFINDER_STYLE) {
                // уголки стоят на месте и остаются белыми, а найденный код получает
                // собственную обводку цвета ответа - в кадре видно и то, и другое
                ViewfinderStyle.FIXED -> {
                    drawCorners(
                        drawn,
                        Color.White.copy(alpha = 0.9f),
                        3.dp.toPx(),
                        drawn.width * 0.12f
                    )
                    acceptedBox?.let { box ->
                        val color = acceptedColor ?: return@let
                        val grown = box.inflate(6.dp.toPx())
                        drawRoundRect(
                            color = color.copy(alpha = 0.16f),
                            topLeft = grown.topLeft,
                            size = grown.size,
                            cornerRadius = CornerRadius(10.dp.toPx())
                        )
                        drawRoundRect(
                            color = color,
                            topLeft = grown.topLeft,
                            size = grown.size,
                            cornerRadius = CornerRadius(10.dp.toPx()),
                            style = Stroke(width = 3.dp.toPx())
                        )
                    }
                }

                // уголки одни и те же: в покое они очерчивают центр кадра, на найденном
                // коде - сам код, и по дороге между положениями просто едут
                ViewfinderStyle.LATCHING ->
                    drawCorners(drawn, cornerColor, 3.dp.toPx(), drawn.width * 0.22f)
            }

            focusAt?.let { at ->
                val alpha = focusAlpha.value
                if (alpha > 0f) {
                    drawCircle(
                        color = Color.White.copy(alpha = 0.95f * alpha),
                        radius = 26.dp.toPx() * focusScale.value,
                        center = at,
                        style = Stroke(width = 1.5.dp.toPx())
                    )
                }
            }
        }
    }
}

/**
 * Куда именно переезжает рамка, найдя код.
 *
 * Вплотную к коду она смотрится тесно, а совсем маленький код превратил бы её в точку,
 * поэтому прямоугольник немного раздувается и не даёт себе стать меньше трети исходной
 * рамки. И не вылезает за неё же: рамка не должна оказаться больше того окна, в котором
 * приложение вообще смотрит.
 */
private fun snapTarget(
    box: androidx.compose.ui.geometry.Rect,
    home: androidx.compose.ui.geometry.Rect
): androidx.compose.ui.geometry.Rect {
    val minSide = home.width * 0.33f
    val padded = box.inflate(box.minDimension * 0.12f)
    val side = maxOf(padded.width, padded.height, minSide).coerceAtMost(home.width)
    val half = side / 2f
    val c = box.center
    return androidx.compose.ui.geometry.Rect(
        c.x - half,
        c.y - half,
        c.x + half,
        c.y + half
    )
}

/** Уголки прямоугольника: так рисуют видоискатель, чтобы рамка не закрывала сам код. */
private fun DrawScope.drawCorners(
    rect: androidx.compose.ui.geometry.Rect,
    color: Color,
    strokeWidth: Float,
    armLength: Float
) {
    val arm = armLength.coerceAtMost(minOf(rect.width, rect.height) / 2f)
    val stroke = Stroke(width = strokeWidth, cap = StrokeCap.Round)

    fun line(from: Offset, to: Offset) =
        drawLine(color, from, to, strokeWidth = stroke.width, cap = stroke.cap)

    line(Offset(rect.left, rect.top), Offset(rect.left + arm, rect.top))
    line(Offset(rect.left, rect.top), Offset(rect.left, rect.top + arm))

    line(Offset(rect.right, rect.top), Offset(rect.right - arm, rect.top))
    line(Offset(rect.right, rect.top), Offset(rect.right, rect.top + arm))

    line(Offset(rect.left, rect.bottom), Offset(rect.left + arm, rect.bottom))
    line(Offset(rect.left, rect.bottom), Offset(rect.left, rect.bottom - arm))

    line(Offset(rect.right, rect.bottom), Offset(rect.right - arm, rect.bottom))
    line(Offset(rect.right, rect.bottom), Offset(rect.right, rect.bottom - arm))
}

/**
 * Decodes QR codes from camera frames with ML Kit.
 *
 * The frame is handed over as-is together with its rotation, so codes are read at any
 * orientation without copying and rebuilding the luminance plane by hand.
 */
class MlKitQrCodeAnalyzer(
    private val onQrCodeDetected: (DetectedCode) -> Unit
) : ImageAnalysis.Analyzer, AutoCloseable {

    private val scanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            // Камера читает всё, что умеет распознаватель. Ограничение двумя форматами
            // осталось с перехода на ML Kit и было лишним: код не из списка получает
            // честный ответ «нет в этой сессии», а вот молчание на поднесённый штрихкод
            // читается как сломанная камера.
            .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
            .build()
    )

    @androidx.annotation.OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        val rotation = imageProxy.imageInfo.rotationDegrees
        // ML Kit отдаёт координаты уже повёрнутого кадра - того, что видно на экране,
        // поэтому при повороте на четверть стороны меняются местами
        val upright = rotation == 90 || rotation == 270
        val width = if (upright) mediaImage.height else mediaImage.width
        val height = if (upright) mediaImage.width else mediaImage.height

        val image = InputImage.fromMediaImage(mediaImage, rotation)
        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                // из нескольких кодов в кадре берётся ближайший к центру: человек
                // целится серединой экрана, а не краем
                barcodes
                    .mapNotNull { barcode ->
                        val value = barcode.rawValue ?: return@mapNotNull null
                        val box = barcode.boundingBox ?: return@mapNotNull null
                        DetectedCode(value, box, width, height)
                    }
                    .minByOrNull { detected ->
                        val dx = detected.box.exactCenterX() - width / 2f
                        val dy = detected.box.exactCenterY() - height / 2f
                        dx * dx + dy * dy
                    }
                    ?.let(onQrCodeDetected)
            }
            .addOnFailureListener { Log.w(TAG, "Frame analysis failed", it) }
            // the frame must stay open until ML Kit is done with it
            .addOnCompleteListener { imageProxy.close() }
    }

    override fun close() = scanner.close()
}
