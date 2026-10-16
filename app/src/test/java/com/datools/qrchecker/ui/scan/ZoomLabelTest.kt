package com.datools.qrchecker.ui.scan

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The zoom label: a whole ratio must not become "1.0x", and the tenth must not be lost,
 * since "0.6x" on an ultra-wide camera is not "1x".
 */
class ZoomLabelTest {

    @Test
    fun `whole ratios have no fraction`() {
        assertEquals("1x", formatZoom(1f))
        assertEquals("2x", formatZoom(2f))
        assertEquals("10x", formatZoom(10f))
    }

    @Test
    fun `the tenth is kept`() {
        assertEquals("0.6x", formatZoom(0.6f))
        assertEquals("2.5x", formatZoom(2.5f))
    }

    @Test
    fun `extra digits round to tenths`() {
        assertEquals("1.2x", formatZoom(1.234f))
        assertEquals("3x", formatZoom(2.98f))
    }
}
