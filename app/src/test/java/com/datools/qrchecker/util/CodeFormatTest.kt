package com.datools.qrchecker.util

import org.junit.Assert.assertEquals
import org.junit.Test

class CodeFormatTest {

    private val codes = listOf("dm1", "ean1", "dm2", "list1")
    private val formats = mapOf(
        "dm1" to CodeFormat.DATA_MATRIX,
        "dm2" to CodeFormat.DATA_MATRIX,
        "ean1" to CodeFormat.LINEAR
    )

    @Test
    fun `виды считаются, самые многочисленные первыми`() {
        assertEquals(
            listOf(CodeFormat.DATA_MATRIX to 2, CodeFormat.LINEAR to 1, CodeFormat.OTHER to 1),
            countFormats(codes, formats)
        )
    }

    @Test
    fun `снятый вид не попадает в сессию, порядок остальных сохраняется`() {
        assertEquals(
            listOf("dm1", "dm2", "list1"),
            keepFormats(codes, formats, setOf(CodeFormat.DATA_MATRIX, CodeFormat.OTHER))
        )
    }
}
