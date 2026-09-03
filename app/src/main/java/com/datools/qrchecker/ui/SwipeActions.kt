package com.datools.qrchecker.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.roundToInt

/**
 * Доля ширины, дальше которой плашку не утащить никаким усилием.
 *
 * Плашка не улетает за экран и не исчезает: свайп здесь - способ вызвать действие, а не
 * убрать строку. Копирование в списке вообще ничего не меняет, а удалением управляет сам
 * список - он перерисуется, когда база ответит.
 */
private const val MAX_TRAVEL = 0.42f

/** Где действие считается вызванным. Заметно раньше половины: до половины никто не тянет. */
private const val TRIGGER_TRAVEL = 0.22f

/**
 * Насколько палец обгоняет плашку: чем дальше тянешь, тем туже идёт.
 *
 * Смещение считается как limit * (1 - e^(-x/limit)): у нуля плашка идёт за пальцем один
 * к одному, дальше отстаёт всё сильнее и упирается в limit. Это та самая упругость, по
 * которой чувствуется, что дальше тянуть некуда.
 */
internal fun resistedOffset(raw: Float, limit: Float): Float {
    if (limit <= 0f) return 0f
    val magnitude = limit * (1f - exp(-abs(raw) / limit))
    return if (raw < 0f) -magnitude else magnitude
}

/** Что показать под плашкой, пока её тянут, и что сделать, когда отпустят за порогом. */
data class SwipeAction(
    val background: Color,
    val onTrigger: () -> Unit,
    val icon: @Composable () -> Unit
)

/**
 * Плашка с действиями по свайпу: вправо - [start], влево - [end].
 *
 * Написано руками, а не через SwipeToDismissBox: тот существует, чтобы строку убрать, и
 * договориться с ним «покажи жест, но останься на месте» не выходит - он доигрывает уход
 * до конца и застревает снаружи экрана. Здесь плашка всегда возвращается на место сама.
 *
 * [onThresholdCrossed] зовётся в момент, когда действие взведено, - по нему даётся отдача:
 * отпускать можно уже здесь, доводить до края не нужно.
 */
@Composable
fun SwipeActionRow(
    modifier: Modifier = Modifier,
    start: SwipeAction? = null,
    end: SwipeAction? = null,
    onThresholdCrossed: () -> Unit = {},
    content: @Composable () -> Unit
) {
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    var width by remember { mutableFloatStateOf(0f) }
    var raw by remember { mutableFloatStateOf(0f) }
    // взведено ли действие: порог перешагнули, палец ещё не отпущен
    var armed by remember { mutableStateOf(false) }

    val currentStart by rememberUpdatedState(start)
    val currentEnd by rememberUpdatedState(end)
    val currentCross by rememberUpdatedState(onThresholdCrossed)

    val limit = width * MAX_TRAVEL
    val trigger = width * TRIGGER_TRAVEL

    val dragState = rememberDraggableState { delta ->
        val next = raw + delta
        // в сторону, где действия нет, плашка не едет вовсе
        raw = when {
            next > 0f && currentStart == null -> 0f
            next < 0f && currentEnd == null -> 0f
            else -> next
        }
        val shown = resistedOffset(raw, limit)
        val nowArmed = trigger > 0f && abs(shown) >= trigger
        if (nowArmed != armed) {
            armed = nowArmed
            if (nowArmed) currentCross()
        }
        scope.launch { offset.snapTo(shown) }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .onSizeChanged { width = it.width.toFloat() }
            .draggable(
                state = dragState,
                orientation = Orientation.Horizontal,
                onDragStopped = {
                    val fired = if (armed) {
                        if (offset.value > 0f) currentStart else currentEnd
                    } else {
                        null
                    }
                    raw = 0f
                    armed = false
                    scope.launch {
                        offset.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
                    }
                    fired?.onTrigger?.invoke()
                }
            )
    ) {
        val shown = offset.value
        val action = when {
            shown > 0f -> currentStart
            shown < 0f -> currentEnd
            else -> null
        }

        if (action != null) {
            // иконка проявляется по мере движения и на пороге уже во всю силу:
            // видно, докуда осталось тянуть
            val progress = if (trigger > 0f) (abs(shown) / trigger).coerceIn(0f, 1f) else 0f
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(MaterialTheme.shapes.medium)
                    .background(action.background)
                    .padding(horizontal = 20.dp),
                contentAlignment =
                    if (shown > 0f) Alignment.CenterStart else Alignment.CenterEnd
            ) {
                Box(modifier = Modifier.alpha(progress)) { action.icon() }
            }
        }

        Box(modifier = Modifier.offset { IntOffset(shown.roundToInt(), 0) }) {
            content()
        }
    }
}
