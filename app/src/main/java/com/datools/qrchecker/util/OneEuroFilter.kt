package com.datools.qrchecker.util

import kotlin.math.PI
import kotlin.math.abs

/**
 * Фильтр «1€» (Casiez, Roussel, Vogel, 2012) - сглаживание дрожащего сигнала без
 * заметного запаздывания.
 *
 * Рамку кода распознаватель на каждом кадре находит чуть по-разному, и обводка вокруг
 * неподвижной коробки дрожала. Обычное усреднение лечит дрожь, но тянет рамку за
 * движущейся коробкой с опозданием. Здесь сила сглаживания зависит от скорости: стоит
 * код - сглаживается сильно, едет - почти не сглаживается и не отстаёт. Тем же приёмом
 * гасят дрожание в трекинге рук и лиц.
 *
 * Стоит несколько умножений на кадр.
 */
class OneEuroFilter(
    /** Частота среза в покое, Гц: чем ниже, тем спокойнее неподвижная рамка. */
    private val minCutoff: Double = 0.5,
    /** Насколько быстро сглаживание ослабевает с ростом скорости. */
    private val beta: Double = 0.02,
    /** Частота среза для оценки скорости, Гц. */
    private val derivativeCutoff: Double = 1.0
) {
    private var lastValue = 0.0
    private var lastDerivative = 0.0
    private var lastTimeMs = 0L
    private var started = false

    fun reset() {
        started = false
    }

    fun filter(value: Double, timeMs: Long): Double {
        if (!started) {
            started = true
            lastValue = value
            lastDerivative = 0.0
            lastTimeMs = timeMs
            return value
        }
        // тот же или более ранний момент - не новый кадр: состояние не трогаем, иначе
        // повторный вызов сбрасывал бы сглаживание и отдавал сырое значение
        if (timeMs <= lastTimeMs) return lastValue
        val dt = (timeMs - lastTimeMs) / 1000.0
        lastTimeMs = timeMs

        val derivative = (value - lastValue) / dt
        lastDerivative = smooth(derivative, lastDerivative, alpha(derivativeCutoff, dt))

        val cutoff = minCutoff + beta * abs(lastDerivative)
        lastValue = smooth(value, lastValue, alpha(cutoff, dt))
        return lastValue
    }

    private fun alpha(cutoff: Double, dt: Double): Double {
        val tau = 1.0 / (2 * PI * cutoff)
        return 1.0 / (1.0 + tau / dt)
    }

    private fun smooth(value: Double, previous: Double, alpha: Double) =
        alpha * value + (1 - alpha) * previous
}
