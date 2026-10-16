package com.datools.qrchecker.util

import kotlin.math.PI
import kotlin.math.abs

/**
 * The One Euro filter (Casiez, Roussel, Vogel, 2012): smoothing a jittery signal without
 * noticeable lag.
 *
 * The detector finds a code's box a little differently on every frame, so the outline
 * around a still box trembled. Plain averaging cures the jitter but drags behind a
 * moving box. Here the smoothing depends on speed: strong when the code is still, weak
 * when it moves. A few multiplications per frame.
 */
class OneEuroFilter(
    /** Cutoff frequency at rest, Hz: lower is calmer for a still box. */
    private val minCutoff: Double = 0.5,
    /** How fast smoothing weakens as speed grows. */
    private val beta: Double = 0.02,
    /** Cutoff frequency for the speed estimate, Hz. */
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
        // not a new frame: a repeated call must not reset the filter and return the raw value
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
