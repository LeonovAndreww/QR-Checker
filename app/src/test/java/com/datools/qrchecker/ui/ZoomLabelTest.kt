package com.datools.qrchecker.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Подпись кратности.
 *
 * Проверяется то, из-за чего её вообще писали руками: целое не должно превращаться в
 * «1.0x», а десятая доля не должна теряться - «0.6x» на сверхширокой камере это не то
 * же самое, что «1x».
 */
class ZoomLabelTest {

    @Test
    fun `целые кратности пишутся без дробной части`() {
        assertEquals("1x", formatZoom(1f))
        assertEquals("2x", formatZoom(2f))
        assertEquals("10x", formatZoom(10f))
    }

    @Test
    fun `десятая доля остаётся`() {
        assertEquals("0.6x", formatZoom(0.6f))
        assertEquals("2.5x", formatZoom(2.5f))
    }

    @Test
    fun `лишние знаки округляются до десятых`() {
        assertEquals("1.2x", formatZoom(1.234f))
        assertEquals("3x", formatZoom(2.98f))
    }
}
