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
    fun `код из списка отмечается`() {
        val s = session(codes = listOf("A", "B"))
        assertEquals(ScanVerdict.Marked("A"), verdictFor(s, "A"))
    }

    @Test
    fun `чужой код в сверяющей сессии остаётся чужим`() {
        val s = session(codes = listOf("A"))
        assertEquals(ScanVerdict.Foreign("X"), verdictFor(s, "X"))
    }

    @Test
    fun `чужой код в собирающей сессии дописывается`() {
        val s = session(codes = emptyList(), collecting = true)
        assertEquals(ScanVerdict.Recorded("X"), verdictFor(s, "X"))
    }

    @Test
    fun `повтор ловится в обеих сессиях и несёт время`() {
        val checking = session(listOf("A"), scanned = listOf("A"), times = mapOf("A" to 100L))
        val collecting = session(listOf("A"), scanned = listOf("A"), times = mapOf("A" to 100L), collecting = true)
        assertEquals(ScanVerdict.Repeat("A", 100L), verdictFor(checking, "A"))
        assertEquals(ScanVerdict.Repeat("A", 100L), verdictFor(collecting, "A"))
    }

    @Test
    fun `повтор без известного времени - всё равно повтор`() {
        val s = session(listOf("A"), scanned = listOf("A"))
        assertEquals(ScanVerdict.Repeat("A", null), verdictFor(s, "A"))
    }

    @Test
    fun `собирающая сессия не дописывает то, что уже отмечено`() {
        val s = session(listOf("A"), scanned = listOf("A"), collecting = true)
        assertEquals(ScanVerdict.Repeat("A", null), verdictFor(s, "A"))
    }

    @Test
    fun `собирающая сессия с подгруженным списком ведёт себя как сверяющая, пока код в списке`() {
        // файл в собирающую сессию добавить можно: неотмеченный код из него отмечается,
        // а не дописывается второй раз
        val s = session(codes = listOf("A", "B"), collecting = true)
        assertEquals(ScanVerdict.Marked("B"), verdictFor(s, "B"))
    }
}
