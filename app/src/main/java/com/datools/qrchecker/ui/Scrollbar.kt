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

/** Сколько полоса остаётся на виду после того, как список остановился. */
private const val SCROLLBAR_LINGER_MS = 1_500L

/**
 * Список с полосой прокрутки у правого края.
 *
 * В Compose своей полосы у списков нет, а без неё в партии на тысячи коробок не видно ни
 * где ты, ни какой длины список. Длина ползунка - доля видимого, положение - где ты в
 * списке. Ползунок можно тянуть: так до середины партии добираются за одно движение.
 *
 * Пока список стоит, полоса прячется и ничего под собой не перекрывает: справа в строках
 * стоят кнопки, и невидимый ползунок съедал бы нажатия на них.
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
            // доля видимого: целые строки плюс видимая часть первой
            val first = visible.first()
            val firstFraction = if (first.size > 0) {
                (-first.offset).coerceAtLeast(0).toFloat() / first.size
            } else 0f
            val shown = visible.size.toFloat()
            if (shown >= total && !state.canScrollForward && !state.canScrollBackward) {
                return@derivedStateOf null
            }
            // в самом конце - ровно внизу, а не «почти»: иначе по полосе не видно, что
            // список кончился
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

    // полоса видна, пока список движется, и ещё немного после
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
        // размер ползунка меняется на ходу - строки разной высоты, - и перезапуск жеста
        // от каждого такого изменения обрывал бы перетаскивание
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
                            // та же шкала, по которой ползунок рисуется: иначе список
                            // прыгал в первое же мгновение захвата
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
    /** Сколько строк можно прокрутить: всего минус видимые. */
    val scrollable: Float
)
