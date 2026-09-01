package com.datools.qrchecker

import com.datools.qrchecker.util.buildCsv
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CsvExportTest {

    @Test
    fun startsWithBomAndSeparatorHint() {
        val csv = buildCsv("Codes", listOf("A1"))
        assertTrue("Excel reads UTF-8 as ANSI without a BOM", csv.startsWith("﻿"))
        assertEquals("sep=;", csv.lineSequence().first().removePrefix("﻿"))
    }

    @Test
    fun writesHeaderThenOneCodePerLine() {
        val csv = buildCsv("Codes", listOf("A1", "A2", "A3"))
        val lines = csv.removePrefix("﻿").split("\r\n").filter { it.isNotEmpty() }
        assertEquals(listOf("sep=;", "Codes", "A1", "A2", "A3"), lines)
    }

    @Test
    fun quotesValuesHoldingTheSeparator() {
        val csv = buildCsv("Codes", listOf("box;12"))
        assertTrue(csv.contains("\"box;12\""))
    }

    @Test
    fun doublesEmbeddedQuotes() {
        val csv = buildCsv("Codes", listOf("""say "hi""""))
        assertTrue(csv.contains("\"say \"\"hi\"\"\""))
    }

    @Test
    fun leavesPlainValuesUnquoted() {
        val csv = buildCsv("Codes", listOf("A1"))
        assertTrue(csv.contains("\r\nA1\r\n") || csv.endsWith("A1\r\n"))
    }

    @Test
    fun handlesAnEmptyList() {
        val csv = buildCsv("Codes", emptyList())
        val lines = csv.removePrefix("﻿").split("\r\n").filter { it.isNotEmpty() }
        assertEquals(listOf("sep=;", "Codes"), lines)
    }
}
