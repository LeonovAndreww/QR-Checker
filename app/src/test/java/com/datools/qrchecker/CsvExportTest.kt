package com.datools.qrchecker

import com.datools.qrchecker.util.GROUP_SEPARATOR
import com.datools.qrchecker.util.buildCsv
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CsvExportTest {

    private fun csv(codes: List<String>, times: Map<String, Long>? = null) =
        buildCsv(
            title = "Codes",
            columnOnBox = "On the box",
            columnFull = "Full",
            columnScannedAt = "When",
            codes = codes,
            scanTimes = times
        )

    private fun rows(codes: List<String>, times: Map<String, Long>?) =
        csv(codes, times).split("\r\n").filter { it.isNotEmpty() }

    private fun rows(codes: List<String>) = csv(codes).split("\r\n").filter { it.isNotEmpty() }

    @Test
    fun `файл начинается с BOM и подсказки про разделитель`() {
        val out = csv(listOf("A1"))
        assertTrue(out.startsWith("\uFEFF"))
        assertEquals("\uFEFFsep=;", rows(listOf("A1"))[0])
    }

    @Test
    fun `после заголовка идёт строка с названиями колонок`() {
        assertEquals("Codes", rows(listOf("A1"))[1])
        assertEquals("On the box;Full;When", rows(listOf("A1"))[2])
    }

    @Test
    fun `каждый код - строка из короткого и полного значения`() {
        val marked = "0104680577333570215,'OfIXCFmCGl${GROUP_SEPARATOR}9180C3${GROUP_SEPARATOR}9212ab"
        val row = rows(listOf(marked)).last()
        // оба значения содержат запятую и потому берутся в кавычки: иначе такую строку
        // нельзя прочитать обратно
        assertEquals("\"0104680577333570215,'OfIXCFmCGl\";\"$marked\";", row)
    }

    @Test
    fun `значение с разделителем берётся в кавычки`() {
        assertEquals("\"box;12\";\"box;12\";", rows(listOf("box;12")).last())
    }

    @Test
    fun `кавычка внутри значения удваивается`() {
        assertEquals(
            "\"say \"\"hi\"\"\";\"say \"\"hi\"\"\";",
            rows(listOf("say \"hi\"")).last()
        )
    }

    @Test
    fun `каждая строка заканчивается CRLF`() {
        assertTrue(csv(listOf("A1")).endsWith("\r\n"))
    }

    @Test
    fun `пустой список оставляет только шапку`() {
        assertEquals(3, rows(emptyList()).size)
    }

    @Test
    fun `время отметки уходит третьей колонкой, а у неотмеченных остаётся пустым`() {
        val out = rows(listOf("A1", "A2"), mapOf("A1" to 1_700_000_000_000L))
        assertTrue(out[3].startsWith("A1;A1;20"))
        assertEquals("A2;A2;", out[4])
    }
}
