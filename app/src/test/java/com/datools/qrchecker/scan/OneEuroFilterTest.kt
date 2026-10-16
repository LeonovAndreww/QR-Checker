package com.datools.qrchecker.scan

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class OneEuroFilterTest {

    @Test
    fun `jitter of a still code is damped`() {
        // the box sits at 500 and jumps by ±6 pixels from frame to frame, at 15 fps
        val filter = OneEuroFilter()
        var worst = 0.0
        for (i in 0 until 60) {
            val raw = 500.0 + if (i % 2 == 0) 6.0 else -6.0
            val out = filter.filter(raw, i * 66L)
            if (i > 10) worst = maxOf(worst, abs(out - 500.0))
        }
        assertTrue("осталось дрожи: $worst", worst < 2.0)
    }

    @Test
    fun `a moving code is followed without much lag`() {
        // the box moves at 600 pixels per second
        val filter = OneEuroFilter()
        var lag = 0.0
        for (i in 0 until 30) {
            val t = i * 66L
            val raw = 100.0 + 0.6 * t
            lag = raw - filter.filter(raw, t)
        }
        assertTrue("отставание: $lag", lag < 60.0)
    }

    @Test
    fun `a repeated call with the same time does not reset smoothing`() {
        val filter = OneEuroFilter()
        filter.filter(500.0, 0)
        val smoothed = filter.filter(520.0, 66)
        // the same frame again gives the previous answer, not the raw 520
        assertTrue(abs(filter.filter(520.0, 66) - smoothed) < 1e-9)
        assertTrue(smoothed < 519.0)
    }
}
