package com.datools.qrchecker.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CodeSourcesTest {

    private val code1 = "0104680577333570215,'OfIXCFmCGl"
    private val code2 = "01046805773335702153vt-1ZQae;wC"

    @Test
    fun `простой список по коду в строке`() {
        assertEquals(listOf(code1, code2), parseCodeList("$code1\n$code2\n"))
    }

    @Test
    fun `пустые строки пропускаются`() {
        assertEquals(listOf(code1), parseCodeList("\n\n  \n$code1\n\n"))
    }

    @Test
    fun `код берётся из колонки, а не из номера строки`() {
        val text = "1;\"$code1\";шт\n2;\"$code2\";шт\n"
        assertEquals(listOf(code1, code2), parseCodeList(text))
    }

    @Test
    fun `разделители внутри кода не режут его на куски`() {
        // серийник состоит из произвольных знаков, и запятая с точкой с запятой в нём
        // встречаются постоянно - это самая опасная ошибка такого разбора
        assertEquals(listOf(code1), parseCodeList(code1))
        assertEquals(listOf(code2), parseCodeList(code2))
    }

    @Test
    fun `заголовок таблицы отсеивается`() {
        // ни одно поле шапки не дотягивает до длины настоящего кода
        val text = "n;code;unit\n1;\"$code1\";шт\n"
        assertEquals(listOf(code1), parseCodeList(text))
    }

    @Test
    fun `кавычки и BOM не мешают`() {
        assertEquals(listOf(code1), parseCodeList("\uFEFF\"$code1\"\n"))
    }

    @Test
    fun `строка sep не считается кодом`() {
        assertEquals(listOf(code1), parseCodeList("sep=;\n$code1\n"))
    }

    @Test
    fun `повторы схлопываются, порядок первого вхождения сохраняется`() {
        assertEquals(listOf(code1, code2), parseCodeList("$code1\n$code2\n$code1\n"))
    }

    @Test
    fun `разделитель GS1 внутри кода переживает разбор`() {
        val marked = "0104680577333570215,'OfIXCFmCGl${GROUP_SEPARATOR}9180C3"
        assertEquals(listOf(marked), parseCodeList("$marked\n"))
    }

    @Test
    fun `наш собственный отчёт читается обратно`() {
        val report = buildCsv(
            title = "Не отсканировано",
            columnOnBox = "Код на коробке",
            columnFull = "Полный код",
            columnScannedAt = "Когда",
            codes = listOf(code1, code2)
        )
        // в отчёте две колонки с кодом, длиннее - полная, её и берём
        assertEquals(listOf(code1, code2), parseCodeList(report))
    }

    @Test
    fun `короткий мусор кодом не считается`() {
        assertTrue(parseCodeList("ok\n12\nда\n").isEmpty())
    }
}
