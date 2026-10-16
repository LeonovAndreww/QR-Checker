package com.datools.qrchecker.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * The swipe resistance curve. Three properties the gesture depends on: the row never
 * passes its limit, moves the same way as the finger, and does not lag noticeably at
 * the start.
 */
class SwipeActionsTest {

    private val limit = 420f

    @Test
    fun `the row never passes the limit however hard it is pulled`() {
        for (raw in listOf(100f, 500f, 2_000f, 100_000f)) {
            assertTrue("сдвиг $raw", resistedOffset(raw, limit) <= limit)
            assertTrue("сдвиг -$raw", resistedOffset(-raw, limit) >= -limit)
        }
    }

    @Test
    fun `the row moves the same way as the finger`() {
        assertTrue(resistedOffset(50f, limit) > 0f)
        assertTrue(resistedOffset(-50f, limit) < 0f)
        assertEquals(0f, resistedOffset(0f, limit), 0f)
    }

    @Test
    fun `at the start the row keeps up with the finger`() {
        // the first pixels are an ordinary touch; lag there would feel like stutter
        val raw = 20f
        assertEquals(raw, resistedOffset(raw, limit), raw * 0.05f)
    }

    @Test
    fun `the further, the stiffer`() {
        val steps = (0..9).map { resistedOffset(it * 60f, limit) }
        val gaps = steps.zipWithNext { a, b -> b - a }
        for ((before, after) in gaps.zipWithNext()) {
            assertTrue("шаг $after не меньше предыдущего $before", after < before)
        }
    }

    @Test
    fun `the threshold is reachable`() {
        // both come from the width; the threshold must lie within the limit, or the
        // action could never be triggered
        val width = 1000f
        val trigger = width * 0.22f
        val reach = resistedOffset(width, width * 0.42f)
        assertTrue("дотягивается до $reach, а нужно $trigger", reach > trigger)
    }

    @Test
    fun `zero width does not break the maths`() {
        assertEquals(0f, resistedOffset(100f, 0f), 0f)
        assertTrue(abs(resistedOffset(-100f, 0f)) == 0f)
    }
}
