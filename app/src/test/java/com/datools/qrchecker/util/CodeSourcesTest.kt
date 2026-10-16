package com.datools.qrchecker.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CodeSourcesTest {

    private val code1 = "0104680577333570215,'OfIXCFmCGl"
    private val code2 = "01046805773335702153vt-1ZQae;wC"

    @Test
    fun `a plain list with one code per line`() {
        assertEquals(listOf(code1, code2), parseCodeList("$code1\n$code2\n"))
    }

    @Test
    fun `blank lines are skipped`() {
        assertEquals(listOf(code1), parseCodeList("\n\n  \n$code1\n\n"))
    }

    @Test
    fun `the code is taken from its column, not the row number`() {
        val text = "1;\"$code1\";шт\n2;\"$code2\";шт\n"
        assertEquals(listOf(code1, code2), parseCodeList(text))
    }

    @Test
    fun `separators inside a code do not split it`() {
        // serials are arbitrary characters, commas and semicolons included; cutting
        // there is the most dangerous mistake such a parser can make
        assertEquals(listOf(code1), parseCodeList(code1))
        assertEquals(listOf(code2), parseCodeList(code2))
    }

    @Test
    fun `a table header is filtered out`() {
        // no header field is as long as a real code
        val text = "n;code;unit\n1;\"$code1\";шт\n"
        assertEquals(listOf(code1), parseCodeList(text))
    }

    @Test
    fun `quotes and BOM do no harm`() {
        assertEquals(listOf(code1), parseCodeList("\uFEFF\"$code1\"\n"))
    }

    @Test
    fun `the sep line is not a code`() {
        assertEquals(listOf(code1), parseCodeList("sep=;\n$code1\n"))
    }

    @Test
    fun `repeats collapse, first occurrence order is kept`() {
        assertEquals(listOf(code1, code2), parseCodeList("$code1\n$code2\n$code1\n"))
    }

    @Test
    fun `a GS1 separator inside a code survives parsing`() {
        val marked = "0104680577333570215,'OfIXCFmCGl${GROUP_SEPARATOR}9180C3"
        assertEquals(listOf(marked), parseCodeList("$marked\n"))
    }

    @Test
    fun `our own report reads back`() {
        val report = buildCsv(
            title = "Не отсканировано",
            columnOnBox = "Код на коробке",
            columnFull = "Полный код",
            columnScannedAt = "Когда",
            codes = listOf(code1, code2)
        )
        // the report has two code columns; the longer, full one is taken
        assertEquals(listOf(code1, code2), parseCodeList(report))
    }

    @Test
    fun `short junk is not a code`() {
        assertTrue(parseCodeList("ok\n12\nда\n").isEmpty())
    }
}
