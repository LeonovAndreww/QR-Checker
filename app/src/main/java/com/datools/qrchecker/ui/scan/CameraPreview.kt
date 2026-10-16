package com.datools.qrchecker.ui.scan

import android.util.Log
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.ui.geometry.Rect as GeomRect
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import com.datools.qrchecker.util.LinearReadVote
import com.datools.qrchecker.util.OneEuroFilter
import androidx.compose.ui.platform.LocalDensity
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.delay
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit


private const val TAG = "Camera"

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

/** Как далеко от кода касание ещё считается касанием этого кода. */
private val TAP_REACH = 24.dp

/** Сколько после касания кадры ищут код под пальцем. */
private const val TAP_WINDOW_MS = 1_500L

/**
 * Сколько в режиме «по касанию» камера работает после касания: найти код под пальцем,
 * ответить и проводить его рамкой. Потом засыпает до следующего касания - иначе
 * направленная на коробку камера разбирала бы кадры без конца.
 */
private const val TAP_AWAKE_MS = 3_000L

/** Четыре края рамки кода, каждый через свой фильтр 1€. */
private class BoxSmoother {
    private val left = OneEuroFilter()
    private val top = OneEuroFilter()
    private val right = OneEuroFilter()
    private val bottom = OneEuroFilter()

    fun reset() {
        left.reset(); top.reset(); right.reset(); bottom.reset()
    }

    fun smooth(box: androidx.compose.ui.geometry.Rect, now: Long) = androidx.compose.ui.geometry.Rect(
        left.filter(box.left.toDouble(), now).toFloat(),
        top.filter(box.top.toDouble(), now).toFloat(),
        right.filter(box.right.toDouble(), now).toFloat(),
        bottom.filter(box.bottom.toDouble(), now).toFloat()
    )
}

/** Насколько плавно рамка переезжает к найденному коду и обратно в центр. */
private const val FRAME_SNAP_MS = 260

/** Сколько рамка ждёт код, пропавший из кадра, прежде чем вернуться в центр. */
private const val FRAME_RELEASE_MS = 700L
private const val FRAME_RELEASE_CHECK_MS = 120L

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
internal fun CameraPreview(
    highlightColor: Color?,
    /** Цвет по состоянию кода: он нужен и тогда, когда плашки ответа уже нет. */
    colorOf: (String) -> Color?,
    torchOn: Boolean,
    onTorchAvailable: (Boolean) -> Unit,
    onTapped: () -> Unit,
    /** Читать код только по касанию, а не на каждом кадре. */
    scanOnTap: Boolean,
    /** Текущая кратность - её показывает уже сам экран. */
    onZoomChanged: (Float) -> Unit,
    /**
     * One decoded frame: every code in it, and the one picked for an answer, if any.
     * Whether the pick is answered is up to the caller.
     */
    onFrame: (visible: List<String>, candidate: String?) -> Unit
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
    val currentOnFrame by rememberUpdatedState(onFrame)
    val currentOnTapped by rememberUpdatedState(onTapped)
    val currentScanOnTap by rememberUpdatedState(scanOnTap)
    val currentDensity by rememberUpdatedState(LocalDensity.current)
    // рамка кода без дрожи: распознаватель на каждом кадре находит её чуть по-разному
    val boxSmoother = remember { BoxSmoother() }
    // куда и когда коснулись: ближайшие кадры берут код под пальцем
    var tapAt by remember { mutableStateOf(Offset.Zero) }
    var tapUntil by remember { mutableLongStateOf(0L) }
    // анализатор живёт в эффекте камеры; ссылка нужна, чтобы будить его касанием
    var analyzerGate by remember { mutableStateOf<MlKitQrCodeAnalyzer?>(null) }
    LaunchedEffect(scanOnTap, analyzerGate) {
        analyzerGate?.activeUntil = if (scanOnTap) 0L else Long.MAX_VALUE
    }
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
    // линейный штрихкод отвечает, только набрав несколько одинаковых чтений
    val linearVote = remember { LinearReadVote() }

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
        val analyzer = MlKitQrCodeAnalyzer { codes ->
            mainExecutor.execute {
                val size = currentViewSize
                if (size == IntSize.Zero) return@execute
                val now = System.currentTimeMillis()
                // время съёмки кадра, если камера его даёт: по нему сглаживание не
                // зависит от того, когда главный поток дошёл до результата
                val frameTime = codes.first().frameTimeMs.takeIf { it > 0L } ?: now

                val first = codes.first()
                val mapping = PreviewMapping(first.imageWidth, first.imageHeight, size)
                val placed = codes.map { it to mapping.toView(it.box) }
                val visible = codes.map { it.value }
                val target = viewfinderRect(size)
                val accepted = placed.firstOrNull { it.first.value == acceptedCode }

                // the accepted code's frame follows it while it stays in view, smoothed:
                // the detector finds it a little differently on every frame
                fun follow() {
                    accepted?.let { (_, box) ->
                        acceptedBox = boxSmoother.smooth(box, frameTime)
                        acceptedSeenAt = now
                    }
                }

                fun pick(detected: DetectedCode, onScreen: GeomRect) {
                    if (detected.value != acceptedCode) boxSmoother.reset()
                    acceptedCode = detected.value
                    acceptedBox = boxSmoother.smooth(onScreen, frameTime)
                    acceptedSeenAt = now
                    currentOnFrame(visible, detected.value)
                }

                if (currentScanOnTap) {
                    // only a tap gets an answer, once, for the code under the finger
                    val tap = tapAt.takeIf { now < tapUntil }
                    val underFinger = tap?.let { at ->
                        val reach = with(currentDensity) { TAP_REACH.toPx() }
                        placed.filter { (_, box) -> box.inflate(reach).contains(at) }
                            .minByOrNull { (_, box) -> (box.center - at).getDistanceSquared() }
                    }
                    val readEnough = underFinger != null &&
                        (!underFinger.first.linear || linearVote.offer(underFinger.first.value, now))
                    if (underFinger == null || !readEnough) {
                        follow()
                        currentOnFrame(visible, null)
                        return@execute
                    }
                    tapUntil = 0L
                    pick(underFinger.first, underFinger.second)
                    return@execute
                }

                // Only what the user aimed at counts: a code centred inside the viewfinder.
                // The accepted code keeps the pick while it stays there; with two codes in
                // the viewfinder "nearest to the centre" flips between them on jitter alone.
                val inViewfinder = placed.filter { (_, box) -> target.contains(box.center) }
                val chosen = accepted?.takeIf { it in inViewfinder }
                    ?: inViewfinder.minByOrNull { (_, box) -> (box.center - target.center).getDistanceSquared() }
                // a linear barcode has no error correction: a new one needs several equal reads
                val readEnough = chosen != null && (!chosen.first.linear ||
                    chosen.first.value == acceptedCode || linearVote.offer(chosen.first.value, now))
                if (chosen == null || !readEnough) {
                    follow()
                    currentOnFrame(visible, null)
                    return@execute
                }
                pick(chosen.first, chosen.second)
            }
        }
        analyzer.activeUntil = if (currentScanOnTap) 0L else Long.MAX_VALUE
        analyzerGate = analyzer
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        var boundProvider: ProcessCameraProvider? = null

        // Поворот больше не пересоздаёт экран, и камере надо сказать о нём самим: иначе
        // кадры анализа приходили бы повёрнутыми по-старому, и рамка рисовалась бы не
        // там, где код. Слушается сам экран, а не конфигурация: переворот на 180°
        // конфигурацию не меняет, а кадр переворачивает.
        var boundPreview: Preview? = null
        var boundAnalysis: ImageAnalysis? = null
        val displayManager = context.getSystemService(DisplayManager::class.java)
        val displayListener = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) = Unit
            override fun onDisplayRemoved(displayId: Int) = Unit
            override fun onDisplayChanged(displayId: Int) {
                val display = previewView.display ?: return
                if (display.displayId != displayId) return
                boundPreview?.targetRotation = display.rotation
                boundAnalysis?.targetRotation = display.rotation
            }
        }
        displayManager.registerDisplayListener(displayListener, Handler(Looper.getMainLooper()))
        // Экран могли закрыть, пока провайдер готовился.
        //
        // Тогда onDispose уже прошёл - анализатор закрыт, исполнитель остановлен, а
        // boundProvider ещё пуст, так что отвязывать было нечего. Привязка после этого
        // запускала камеру в закрытый декодер и оставляла её работать за спиной.
        var disposed = false

        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                if (disposed) {
                    cameraProvider.unbindAll()
                    return@addListener
                }
                boundProvider = cameraProvider

                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }

                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { it.setAnalyzer(analysisExecutor, analyzer) }

                previewView.display?.rotation?.let {
                    preview.targetRotation = it
                    analysis.targetRotation = it
                }
                boundPreview = preview
                boundAnalysis = analysis

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
            disposed = true
            displayManager.unregisterDisplayListener(displayListener)
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
                    if (currentScanOnTap) {
                        val now = System.currentTimeMillis()
                        tapAt = at
                        tapUntil = now + TAP_WINDOW_MS
                        analyzerGate?.activeUntil = now + TAP_AWAKE_MS
                    }

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
                    // те же уголки, что и у видоискателя, только вокруг кода и цвета
                    // ответа: две рамки на экране должны быть одного языка
                    acceptedBox?.let { box ->
                        val color = acceptedColor ?: return@let
                        val grown = box.inflate(6.dp.toPx())
                        drawCorners(
                            grown,
                            color,
                            3.dp.toPx(),
                            grown.minDimension * 0.3f
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
