package com.datools.qrchecker.files

import com.datools.qrchecker.codes.GROUP_SEPARATOR
import com.datools.qrchecker.codes.normalizeCode
import com.datools.qrchecker.codes.parseCodeList
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * An export must read back: a report sent on is turned into a session by the same app on
 * the other end, and a code lost on the way is lost silently.
 */
class CsvRoundTripTest {

    private val gs = GROUP_SEPARATOR

    private fun roundTrip(codes: List<String>, scanned: Boolean = true): List<String> {
        val csv = buildCsv(
            title = "Отсканированные коды",
            columnOnBox = "Код на коробке",
            columnFull = "Полный код",
            columnScannedAt = "Когда отсканирован",
            codes = codes,
            scanTimes = if (scanned) codes.associateWith { 1_700_000_000_000L } else null
        )
        return parseCodeList(csv)
    }

    @Test
    fun `a marking code without a comma comes back whole`() {
        // the case that broke: a report row starts with a marking code itself, and the
        // bare-code rule took it together with the other columns
        val code = "010468057733357021ABC123"
        assertEquals(listOf(code), roundTrip(listOf(code)))
    }

    @Test
    fun `an unscanned code comes back the same`() {
        val code = "010468057733357021ABC123"
        assertEquals(listOf(code), roundTrip(listOf(code), scanned = false))
    }

    @Test
    fun `a marking code with its crypto tail comes back whole`() {
        val code = "0104680577333570215,'OfIXCFmCGl${gs}9180C3${gs}922Jxke"
        assertEquals(listOf(code), roundTrip(listOf(code)))
    }

    @Test
    fun `a linear barcode comes back`() {
        assertEquals(listOf("4601234567890"), roundTrip(listOf("4601234567890")))
    }

    @Test
    fun `a link from a plain QR comes back`() {
        val code = "https://example.com/a/b?c=1"
        assertEquals(listOf(code), roundTrip(listOf(code)))
    }

    @Test
    fun `a code with a semicolon comes back`() {
        assertEquals(listOf("ABC;12345678"), roundTrip(listOf("ABC;12345678")))
    }

    @Test
    fun `several different codes all come back`() {
        val codes = listOf(
            "010468057733357021ABC123",
            "4601234567890",
            "https://example.com/x"
        )
        assertEquals(codes, roundTrip(codes))
    }

    @Test
    fun `a bare marking code with a separator in its serial is not split`() {
        // the reason the bare-code rule exists
        val code = "010468057733357021AB;CD"
        assertEquals(listOf(code), parseCodeList(code))
    }

    @Test
    fun `a code with a formula sign is exported defused`() {
        // Excel evaluates a formula even when quoted; only a leading apostrophe helps
        val code = "=HYPERLINK(\"https://x/\",\"Open\")"
        val csv = buildCsv("t", "a", "b", "c", listOf(code))
        val row = csv.lines().first { it.contains("HYPERLINK") }
        row.split(';').take(2).forEach { cell ->
            assertEquals("ячейка: $cell", true, cell.trimStart('"').startsWith("'="))
        }
    }

    @Test
    fun `a code with a formula sign comes back without the apostrophe`() {
        for (code in listOf("=1+2;HYPERLINK", "+79991234567", "-abcdefgh", "@SUM(A1:A9)")) {
            assertEquals(listOf(code), roundTrip(listOf(code)))
        }
    }

    @Test
    fun `a code starting with an apostrophe keeps it`() {
        // a leading apostrophe is guarded like a formula, or reading would take it for ours
        for (code in listOf("'+380501234567", "'abcdefgh", "''=SUM(A1)")) {
            assertEquals(listOf(code), roundTrip(listOf(code)))
        }
    }

    @Test
    fun `german ppn and digital link come back whole`() {
        val ppn = normalizeCode("[)>\u001E06${gs}9N111234567842${gs}1TCH42B${gs}D270630${gs}SRX5TT8Q3MZ\u001E\u0004")
        val link = "https://dalgiardino.com/01/09506000134376/10/ABC/21/123456?17=221231"
        assertEquals(listOf(ppn, link), roundTrip(listOf(ppn, link)))
    }

    @Test
    fun `a value with an escaped quote loses only the enclosing quotes`() {
        assertEquals(listOf("ABC\"DEF12345"), parseCodeList("\"ABC\"\"DEF12345\""))
    }
}
