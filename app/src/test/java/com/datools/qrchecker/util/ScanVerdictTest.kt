package com.datools.qrchecker.util

import com.datools.qrchecker.model.SessionData
import org.junit.Assert.assertEquals
import org.junit.Test

class ScanVerdictTest {

    private fun session(
        codes: List<String>,
        scanned: List<String> = emptyList(),
        times: Map<String, Long>? = null,
        collecting: Boolean = false
    ) = SessionData(
        id = "s",
        name = "n",
        codes = codes,
        scannedCodes = scanned,
        scanTimes = times,
        collecting = collecting
    )

    @Test
    fun `a code from the list is marked`() {
        val s = session(codes = listOf("A", "B"))
        assertEquals(ScanVerdict.Marked("A"), verdictFor(s, "A"))
    }

    @Test
    fun `a foreign code in a checking session stays foreign`() {
        val s = session(codes = listOf("A"))
        assertEquals(ScanVerdict.Foreign("X"), verdictFor(s, "X"))
    }

    @Test
    fun `a foreign code in a collecting session is recorded`() {
        val s = session(codes = emptyList(), collecting = true)
        assertEquals(ScanVerdict.Recorded("X"), verdictFor(s, "X"))
    }

    @Test
    fun `a repeat is caught in both kinds and carries its time`() {
        val checking = session(listOf("A"), scanned = listOf("A"), times = mapOf("A" to 100L))
        val collecting = session(listOf("A"), scanned = listOf("A"), times = mapOf("A" to 100L), collecting = true)
        assertEquals(ScanVerdict.Repeat("A", 100L), verdictFor(checking, "A"))
        assertEquals(ScanVerdict.Repeat("A", 100L), verdictFor(collecting, "A"))
    }

    @Test
    fun `a repeat with an unknown time is still a repeat`() {
        val s = session(listOf("A"), scanned = listOf("A"))
        assertEquals(ScanVerdict.Repeat("A", null), verdictFor(s, "A"))
    }

    @Test
    fun `a collecting session does not record an already marked code`() {
        val s = session(listOf("A"), scanned = listOf("A"), collecting = true)
        assertEquals(ScanVerdict.Repeat("A", null), verdictFor(s, "A"))
    }

    @Test
    fun `a collecting session with a loaded list marks codes from the list`() {
        // a file can be added to a collecting session: an unmarked code from it gets
        // marked rather than recorded a second time
        val s = session(codes = listOf("A", "B"), collecting = true)
        assertEquals(ScanVerdict.Marked("B"), verdictFor(s, "B"))
    }
}
