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
    fun `kinds are counted, the most numerous first`() {
        assertEquals(
            listOf(CodeFormat.DATA_MATRIX to 2, CodeFormat.LINEAR to 1, CodeFormat.OTHER to 1),
            countFormats(codes, formats)
        )
    }

    @Test
    fun `an unticked kind stays out, the order of the rest is kept`() {
        assertEquals(
            listOf("dm1", "dm2", "list1"),
            keepFormats(codes, formats, setOf(CodeFormat.DATA_MATRIX, CodeFormat.OTHER))
        )
    }
}
