package com.datools.qrchecker.util

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Полезные нагрузки взяты из настоящего документа заказчика, страницы 1 и 2. Под каждым
 * кодом в PDF напечатана ровно та строка, которую обязан вернуть shortCode().
 */
class CodeTextTest {

    private val gs = GROUP_SEPARATOR

    private val page1 = "0104680577333570215,'OfIXCFmCGl${gs}9180C3${gs}922Jxke" +
        "ScSv1TQyk7squIZl/ks8mvx0/w5wgFwY/N4ZNk13MmfLPUoLuM1FqM+kHaPwg1FS0ewWvbK+dXmC657oQ=="

    private val page2 = "01046805773335702153vt-1ZQae;wC${gs}9180C3${gs}925MJRA" +
        "Zrt6xkAQ5lK3wcmFh/H9SXKGKWNsEA+dwaOz5ZKF0m2SzaNeTY8j/jRzu26YvH72mfoGZhZBwgJYIushQ=="

    @Test
    fun `короткий код повторяет надпись на коробке`() {
        assertEquals("0104680577333570215,'OfIXCFmCGl", shortCode(page1))
        assertEquals("01046805773335702153vt-1ZQae;wC", shortCode(page2))
    }

    @Test
    fun `граница находится и без разделителей`() {
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
    fun `разделитель переживает нормализацию, мусор вокруг - нет`() {
        assertEquals(page1, normalizeCode("​$page1\n"))
    }

    @Test
    fun `код без криптохвоста остаётся целым`() {
        val plain = "0104680577333570215,'OfIXCFmCGl"
        assertEquals(plain, shortCode(plain))
    }

    @Test
    fun `обычный QR не режется`() {
        assertEquals("https://example.com/91abcd92", shortCode("https://example.com/91abcd92"))
    }
}
