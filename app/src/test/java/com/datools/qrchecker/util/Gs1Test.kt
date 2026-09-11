package com.datools.qrchecker.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Раскладки взяты из разных систем прослеживаемости. Символика у всех одна - Data Matrix
 * с данными по GS1, - различается только набор и порядок полей, и разбор обязан
 * справляться со всеми одинаково.
 */
class Gs1Test {

    private val gs = GROUP_SEPARATOR

    @Test
    fun `честный знак - гтин, серийник и криптохвост`() {
        val code = "0104680577333570215,'OfIXCFmCGl${gs}9180C3${gs}922Jxke"
        assertEquals(
            listOf("01" to "04680577333570", "21" to "5,'OfIXCFmCGl", "91" to "80C3", "92" to "2Jxke"),
            parseGs1(code)
        )
    }

    @Test
    fun `лекарства ес - срок годности и партия перед серийником`() {
        val code = "010461234567890417250131${gs}10AB-123${gs}21SN00042"
        assertEquals(
            listOf(
                "01" to "04612345678904",
                "17" to "250131",
                "10" to "AB-123",
                "21" to "SN00042"
            ),
            parseGs1(code)
        )
    }

    @Test
    fun `у такого кода короткая форма - гтин и серийник, а не срок годности`() {
        val code = "010461234567890417250131${gs}10AB-123${gs}21SN00042"
        assertEquals("010461234567890421SN00042", shortCode(code))
    }

    @Test
    fun `поле фиксированной длины не требует разделителя`() {
        // (01) и (17) заданной длины идут подряд, разделитель только перед партией
        val code = "010461234567890417250131${gs}10L1"
        assertEquals(
            listOf("01" to "04612345678904", "17" to "250131", "10" to "L1"),
            parseGs1(code)
        )
    }

    @Test
    fun `четырёхзначный идентификатор веса читается как шесть цифр`() {
        val code = "01046123456789043103000123${gs}21X1"
        assertEquals(
            listOf("01" to "04612345678904", "3103" to "000123", "21" to "X1"),
            parseGs1(code)
        )
    }

    @Test
    fun `неизвестный идентификатор обрывает разбор`() {
        assertNull(parseGs1("8899abcdef"))
    }

    @Test
    fun `обычная ссылка не выдаёт себя за GS1`() {
        assertNull(parseGs1("https://example.com/01234"))
    }

    @Test
    fun `обрезанное значение фиксированной длины не разбирается`() {
        assertNull(parseGs1("010461234"))
    }

    @Test
    fun `код без серийника укорачивается до одного гтина`() {
        val code = "010461234567890417250131${gs}10AB-123"
        assertEquals("0104612345678904", shortCode(code))
    }

    @Test
    fun `неразобравшийся код с разделителем режется по нему, как раньше`() {
        val code = "8899abcdef${gs}хвост"
        assertEquals("8899abcdef", shortCode(code))
    }
}
