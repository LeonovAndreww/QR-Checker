package com.datools.qrchecker.util

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class OneEuroFilterTest {

    @Test
    fun `дрожь неподвижного кода гасится`() {
        // рамка стоит на 500 и прыгает на ±6 пикселей от кадра к кадру, 15 кадров в секунду
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
    fun `движущийся код догоняется без большого отставания`() {
        // коробку ведут со скоростью 600 пикселей в секунду
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
    fun `повторный вызов с тем же временем не сбрасывает сглаживание`() {
        val filter = OneEuroFilter()
        filter.filter(500.0, 0)
        val smoothed = filter.filter(520.0, 66)
        // тот же кадр ещё раз - ответ прежний, а не сырое 520
        assertTrue(abs(filter.filter(520.0, 66) - smoothed) < 1e-9)
        assertTrue(smoothed < 519.0)
    }
}
