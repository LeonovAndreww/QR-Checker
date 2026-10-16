package com.datools.qrchecker.ui.scan

import com.datools.qrchecker.scan.LinearReadVote
import com.datools.qrchecker.scan.OneEuroFilter
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
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
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect as GeomRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first


private const val TAG = "Camera"

/** Timing of the ring drawn at a tap, and how long the camera keeps the tapped focus. */
private const val FOCUS_RING_IN_MS = 180
private const val FOCUS_RING_HOLD_MS = 450L
private const val FOCUS_RING_OUT_MS = 260
private const val FOCUS_HOLD_SECONDS = 4L

/**
 * How the accepted code is shown. A build-time design choice, not a user setting.
 */
private enum class ViewfinderStyle {
    /** The viewfinder stays put and the code gets its own outline, so both are visible. */
    FIXED,

    /** The viewfinder corners close in on the code and follow it. */
    LATCHING
}

private val VIEWFINDER_STYLE = ViewfinderStyle.FIXED

/** How far from a code a tap still counts as a tap on it. */
private val TAP_REACH = 24.dp

/** How long after a tap frames look for the code under the finger. */
private const val TAP_WINDOW_MS = 1_500L

/**
 * How long the analyzer runs after a tap in scan-on-tap mode: long enough to find the
 * code, answer and follow it. Then it sleeps, since decoding frames is what costs battery.
 */
private const val TAP_AWAKE_MS = 3_000L

/** Smooths the four edges of a code's box, each through its own One Euro filter. */
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

/** Duration of the viewfinder's move to a code and back to the centre. */
private const val FRAME_SNAP_MS = 260

/** How long the frame waits for a code that left the view before letting it go. */
private const val FRAME_RELEASE_MS = 700L
private const val FRAME_RELEASE_CHECK_MS = 120L

/** Viewfinder side as a fraction of the screen's short side. */
private const val VIEWFINDER_FRACTION = 0.72f

/**
 * Maps analysis frame coordinates to view coordinates.
 *
 * PreviewView scales the frame to fill and crops the rest (FILL_CENTER), so frame and
 * view fractions differ; without this the drawn viewfinder and the area used to pick a
 * code would not match.
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

/** The viewfinder in view coordinates: a centred square. */
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
    /** Colour for a code by its state, used once the answer banner is gone. */
    colorOf: (String) -> Color?,
    torchOn: Boolean,
    onTorchAvailable: (Boolean) -> Unit,
    onTapped: () -> Unit,
    /** Decode only after a tap instead of on every frame. */
    scanOnTap: Boolean,
    /** The current zoom ratio; the screen shows it. */
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
    // the detector finds the box a little differently on every frame
    val boxSmoother = remember { BoxSmoother() }
    // where and until when a tap asks for the code under the finger
    var tapAt by remember { mutableStateOf(Offset.Zero) }
    var tapUntil by remember { mutableLongStateOf(0L) }
    // the analyzer lives in the camera effect; a tap needs it to wake it up
    var analyzerGate by remember { mutableStateOf<MlKitQrCodeAnalyzer?>(null) }
    LaunchedEffect(scanOnTap, analyzerGate) {
        analyzerGate?.activeUntil = if (scanOnTap) 0L else Long.MAX_VALUE
    }
    val currentOnZoomChanged by rememberUpdatedState(onZoomChanged)
    val mainExecutor = remember(context) { ContextCompat.getMainExecutor(context) }

    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    val currentViewSize by rememberUpdatedState(viewSize)
    // bound asynchronously; null until then
    var camera by remember { mutableStateOf<Camera?>(null) }
    // the focus ring is drawn here for a moment, so the tap is visibly taken
    var focusAt by remember { mutableStateOf<Offset?>(null) }
    // The last accepted code and its box, updated on every frame that still sees it.
    // Tracked apart from the banner: the banner fades after a second, but the code
    // stays outlined for as long as it is in view.
    var acceptedCode by remember { mutableStateOf<String?>(null) }
    var acceptedBox by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    var acceptedSeenAt by remember { mutableLongStateOf(0L) }
    var acceptedColor by remember { mutableStateOf<Color?>(null) }
    // a new linear barcode needs several equal reads before it is answered
    val linearVote = remember { LinearReadVote() }

    // one animated value for all four sides, so the frame moves as a whole
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
                // capture time when the camera reports it, so smoothing does not depend
                // on when the main thread got to the result
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

        // The activity handles rotation itself, so the use cases must be told about it,
        // or analysis frames would arrive rotated the old way. The display is watched
        // rather than the configuration: a 180° turn changes no configuration.
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
        // The screen may be closed while the provider is still getting ready: onDispose
        // has then run with nothing to unbind, and binding afterwards would leave the
        // camera running into a closed decoder.
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

    // The banner's colour while it is shown, the code's own colour after that. The
    // answer to a code still in view is not repeated, so without the second source the
    // frame around it would stay white as if nothing had been found.
    LaunchedEffect(highlightColor, acceptedCode) {
        acceptedColor = highlightColor ?: acceptedCode?.let { colorOf(it) }
    }

    // A code that left the view is let go, judged by its last sighting rather than by
    // the answer timer: a box held in front of the camera stays outlined.
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

    // To the code with a spring: the target changes on every frame while the box is
    // held, and a spring keeps its velocity across restarts where a fixed-time tween
    // would jerk on each frame. Back to the centre there is no hurry, so a tween.
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

    // shrinks to the point and fades, like a phone camera's focus ring
    LaunchedEffect(focusAt) {
        val at = focusAt ?: return@LaunchedEffect
        focusScale.snapTo(1.35f)
        focusAlpha.snapTo(1f)
        focusScale.animateTo(1f, tween(FOCUS_RING_IN_MS))
        delay(FOCUS_RING_HOLD_MS)
        focusAlpha.animateTo(0f, tween(FOCUS_RING_OUT_MS))
        if (focusAt == at) focusAt = null
    }

    // the torch can only be switched once the camera is bound
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
            // Pinch to zoom. It comes before the tap detector: once two fingers are
            // down it takes the gesture, so a pinch is never read as a tap to focus.
            // The zoom is set as a ratio, which lets multi-camera phones switch lenses
            // and keep optical zoom optical.
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
            // Tap to focus: autofocus aims at the centre, while the code on a box is often
            // off-centre and close, too fine for a lens that locked onto the background.
            .pointerInput(Unit) {
                detectTapGestures { at ->
                    // a tap also asks again about the code in view
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
                                // back to continuous autofocus afterwards, or the next
                                // box would be out of focus
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

            // dim everything outside the viewfinder; it moves with the frame
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
                // fixed white corners, plus the code's own outline in the answer colour
                ViewfinderStyle.FIXED -> {
                    drawCorners(
                        drawn,
                        Color.White.copy(alpha = 0.9f),
                        3.dp.toPx(),
                        drawn.width * 0.12f
                    )
                    // the same corner style as the viewfinder, around the code
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

                // one set of corners: around the centre at rest, around the code when found
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
 * Where the frame moves when it latches onto a code: the code's box padded a little,
 * no smaller than a third of the viewfinder and no larger than the viewfinder itself.
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

/** Draws only the corners of [rect], so the outline does not cover the code. */
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
