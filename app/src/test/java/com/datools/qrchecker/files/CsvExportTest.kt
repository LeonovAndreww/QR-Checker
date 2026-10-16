package com.datools.qrchecker.files

import com.datools.qrchecker.codes.GROUP_SEPARATOR
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
    fun `the file starts with a BOM and a separator hint`() {
        val out = csv(listOf("A1"))
        assertTrue(out.startsWith("\uFEFF"))
        assertEquals("\uFEFFsep=;", rows(listOf("A1"))[0])
    }

    @Test
    fun `the title is followed by a row of column names`() {
        assertEquals("Codes", rows(listOf("A1"))[1])
        assertEquals("On the box;Full;When", rows(listOf("A1"))[2])
    }

    @Test
    fun `each code is a row of its short and full value`() {
        val marked = "0104680577333570215,'OfIXCFmCGl${GROUP_SEPARATOR}9180C3${GROUP_SEPARATOR}9212ab"
        val row = rows(listOf(marked)).last()
        // both values contain a comma and are quoted, or the row could not be read back
        assertEquals("\"0104680577333570215,'OfIXCFmCGl\";\"$marked\";", row)
    }

    @Test
    fun `a value with a separator is quoted`() {
        assertEquals("\"box;12\";\"box;12\";", rows(listOf("box;12")).last())
    }

    @Test
    fun `a quote inside a value is doubled`() {
        assertEquals(
            "\"say \"\"hi\"\"\";\"say \"\"hi\"\"\";",
            rows(listOf("say \"hi\"")).last()
        )
    }

    @Test
    fun `every row ends with CRLF`() {
        assertTrue(csv(listOf("A1")).endsWith("\r\n"))
    }

    @Test
    fun `an empty list leaves only the header`() {
        assertEquals(3, rows(emptyList()).size)
    }

    @Test
    fun `scan time goes to the third column and stays empty for unscanned codes`() {
        val out = rows(listOf("A1", "A2"), mapOf("A1" to 1_700_000_000_000L))
        assertTrue(out[3].startsWith("A1;A1;20"))
        assertEquals("A2;A2;", out[4])
    }
}
