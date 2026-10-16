package com.datools.qrchecker.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** How long the scrollbar stays visible after the list stops. */
private const val SCROLLBAR_LINGER_MS = 1_500L

/**
 * A list with a scrollbar at the right edge.
 *
 * Compose lists have none, and in a batch of thousands there is no telling where you
 * are or how long the list is. The thumb's length is the visible share, its position
 * the place in the list, and it can be dragged to cross a batch in one move.
 *
 * At rest the bar hides and takes no touches: rows have buttons on the right that an
 * invisible thumb would swallow.
 */
@Composable
fun ScrollbarBox(
    state: LazyListState,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(modifier = modifier) {
        content()
        if (enabled) Scrollbar(state)
    }
}

@Composable
private fun BoxScope.Scrollbar(state: LazyListState) {
    val scope = rememberCoroutineScope()
    var dragging by remember { mutableStateOf(false) }
    var lingering by remember { mutableStateOf(true) }

    val metrics by remember(state) {
        derivedStateOf {
            val info = state.layoutInfo
            val total = info.totalItemsCount
            val visible = info.visibleItemsInfo
            if (total == 0 || visible.isEmpty()) return@derivedStateOf null
            // visible share: whole rows plus the visible part of the first one
            val first = visible.first()
            val firstFraction = if (first.size > 0) {
                (-first.offset).coerceAtLeast(0).toFloat() / first.size
            } else 0f
            val shown = visible.size.toFloat()
            if (shown >= total && !state.canScrollForward && !state.canScrollBackward) {
                return@derivedStateOf null
            }
            // exactly at the bottom at the end, so the bar shows the list is over
            val position = if (!state.canScrollForward) 1f
            else (first.index + firstFraction) / (total - shown).coerceAtLeast(1f)
            Metrics(
                size = (shown / total).coerceIn(0.05f, 1f),
                position = position.coerceIn(0f, 1f),
                scrollable = (total - shown).coerceAtLeast(0f)
            )
        }
    }
    val m = metrics ?: return

    // visible while the list moves, and a little after
    LaunchedEffect(state.isScrollInProgress, dragging) {
        if (state.isScrollInProgress || dragging) {
            lingering = true
        } else {
            delay(SCROLLBAR_LINGER_MS)
            lingering = false
        }
    }
    val alpha by animateFloatAsState(
        targetValue = if (lingering || dragging) 1f else 0f,
        animationSpec = tween(250),
        label = "scrollbar"
    )
    if (alpha == 0f) return

    BoxWithConstraints(
        modifier = Modifier
            .align(Alignment.CenterEnd)
            .fillMaxHeight()
            .width(24.dp)
            .padding(vertical = 4.dp)
    ) {
        val trackPx = constraints.maxHeight.toFloat()
        val density = LocalDensity.current
        val minThumbPx = with(density) { 40.dp.toPx() }
        val thumbPx = (trackPx * m.size).coerceAtLeast(minThumbPx).coerceAtMost(trackPx)
        val travel = (trackPx - thumbPx).coerceAtLeast(0f)
        val top = travel * m.position
        // the thumb size changes on the fly with row heights; restarting the gesture on
        // each change would break the drag
        val currentTop by rememberUpdatedState(top)
        val currentTravel by rememberUpdatedState(travel)
        val currentScrollable by rememberUpdatedState(m.scrollable)

        Box(
            modifier = Modifier
                .offset { IntOffset(0, top.roundToInt()) }
                .width(24.dp)
                .height(with(density) { thumbPx.toDp() })
                .alpha(alpha)
                .pointerInput(state) {
                    var startTop = 0f
                    var moved = 0f
                    detectVerticalDragGestures(
                        onDragStart = {
                            dragging = true
                            startTop = currentTop
                            moved = 0f
                        },
                        onDragEnd = { dragging = false },
                        onDragCancel = { dragging = false },
                        onVerticalDrag = { change, delta ->
                            change.consume()
                            moved += delta
                            val travelNow = currentTravel
                            val fraction =
                                if (travelNow > 0f) ((startTop + moved) / travelNow).coerceIn(0f, 1f) else 0f
                            // the same scale the thumb is drawn with, or the list jumps on grab
                            val index = (fraction * currentScrollable).roundToInt()
                            scope.launch { state.scrollToItem(index) }
                        }
                    )
                },
            contentAlignment = Alignment.CenterEnd
        ) {
            Box(
                modifier = Modifier
                    .padding(end = 2.dp)
                    .width(if (dragging) 8.dp else 5.dp)
                    .fillMaxSize()
                    .background(
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(50)
                    )
            )
        }
    }
}

private data class Metrics(
    val size: Float,
    val position: Float,
    /** Rows that can be scrolled: total minus visible. */
    val scrollable: Float
)
