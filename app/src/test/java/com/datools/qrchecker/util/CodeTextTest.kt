package com.datools.qrchecker.util

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Payloads from a real customer document, pages 1 and 2. Under each code the PDF prints
 * exactly the string shortCode() must return.
 */
class CodeTextTest {

    private val gs = GROUP_SEPARATOR

    private val page1 = "0104680577333570215,'OfIXCFmCGl${gs}9180C3${gs}922Jxke" +
        "ScSv1TQyk7squIZl/ks8mvx0/w5wgFwY/N4ZNk13MmfLPUoLuM1FqM+kHaPwg1FS0ewWvbK+dXmC657oQ=="

    private val page2 = "01046805773335702153vt-1ZQae;wC${gs}9180C3${gs}925MJRA" +
        "Zrt6xkAQ5lK3wcmFh/H9SXKGKWNsEA+dwaOz5ZKF0m2SzaNeTY8j/jRzu26YvH72mfoGZhZBwgJYIushQ=="

    @Test
    fun `the short code matches the text on the box`() {
        assertEquals("0104680577333570215,'OfIXCFmCGl", shortCode(page1))
        assertEquals("01046805773335702153vt-1ZQae;wC", shortCode(page2))
    }

    @Test
    fun `the boundary is found without separators`() {
        assertEquals(
            "0104680577333570215,'OfIXCFmCGl",
            shortCode(page1.replace(gs.toString(), ""))
        )
        assertEquals(
            "01046805773335702153vt-1ZQae;wC",
            shortCode(page2.replace(gs.toString(), ""))
        )
    }

    @Test
    fun `the separator survives normalisation, the noise around it does not`() {
        assertEquals(page1, normalizeCode("​$page1\n"))
    }

    @Test
    fun `a code without a crypto tail stays whole`() {
        val plain = "0104680577333570215,'OfIXCFmCGl"
        assertEquals(plain, shortCode(plain))
    }

    @Test
    fun `a plain QR is not cut`() {
        assertEquals("https://example.com/91abcd92", shortCode("https://example.com/91abcd92"))
    }
}
