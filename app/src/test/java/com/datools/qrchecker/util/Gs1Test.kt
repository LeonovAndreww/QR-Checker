package com.datools.qrchecker.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Layouts come from different traceability systems. All use a GS1 Data Matrix and differ
 * only in the set and order of fields, so the parser must handle them all alike.
 */
class Gs1Test {

    private val gs = GROUP_SEPARATOR

    @Test
    fun `chestny znak - gtin, serial and crypto tail`() {
        val code = "0104680577333570215,'OfIXCFmCGl${gs}9180C3${gs}922Jxke"
        assertEquals(
            listOf("01" to "04680577333570", "21" to "5,'OfIXCFmCGl", "91" to "80C3", "92" to "2Jxke"),
            parseGs1(code)
        )
    }

    @Test
    fun `eu medicine - expiry and batch before the serial`() {
        val code = "010461234567890417250131${gs}10AB-123${gs}21SN00042"
        assertEquals(
            listOf(
                "01" to "04612345678904",
                "17" to "250131",
                "10" to "AB-123",
                "21" to "SN00042"
            ),
            parseGs1(code)
        )
    }

    @Test
    fun `its short form is gtin and serial, not the expiry`() {
        val code = "010461234567890417250131${gs}10AB-123${gs}21SN00042"
        assertEquals("010461234567890421SN00042", shortCode(code))
    }

    @Test
    fun `a fixed-length field needs no separator`() {
        // (01) and (17) have fixed lengths and follow each other; only the batch has a separator
        val code = "010461234567890417250131${gs}10L1"
        assertEquals(
            listOf("01" to "04612345678904", "17" to "250131", "10" to "L1"),
            parseGs1(code)
        )
    }

    @Test
    fun `a four-digit weight identifier takes six digits`() {
        val code = "01046123456789043103000123${gs}21X1"
        assertEquals(
            listOf("01" to "04612345678904", "3103" to "000123", "21" to "X1"),
            parseGs1(code)
        )
    }

    @Test
    fun `an unknown identifier stops the parse`() {
        assertNull(parseGs1("8899abcdef"))
    }

    @Test
    fun `a plain link does not pass for GS1`() {
        assertNull(parseGs1("https://example.com/01234"))
    }

    @Test
    fun `a truncated fixed-length value does not parse`() {
        assertNull(parseGs1("010461234"))
    }

    @Test
    fun `a code without a serial shortens to the gtin alone`() {
        val code = "010461234567890417250131${gs}10AB-123"
        assertEquals("0104612345678904", shortCode(code))
    }

    @Test
    fun `an unparsed code with a separator is cut at it`() {
        val code = "8899abcdef${gs}хвост"
        assertEquals("8899abcdef", shortCode(code))
    }

    @Test
    fun `fields come from GS1 whatever the order`() {
        val code = "010461234567890417250131${gs}10AB-123${gs}21SN00042"
        assertEquals(
            CodeFields(
                product = "04612345678904",
                serial = "SN00042",
                batch = "AB-123",
                expiry = "250131"
            ),
            codeFields(code)
        )
    }

    @Test
    fun `german PPN parses with its own dictionary`() {
        val code = "9N03752864${gs}1T12345ABCDE${gs}D160617${gs}S12345ABCDEF98765"
        assertEquals(
            CodeFields(
                product = "03752864",
                serial = "12345ABCDEF98765",
                batch = "12345ABCDE",
                expiry = "160617"
            ),
            codeFields(code)
        )
    }

    @Test
    fun `the ISO 15434 envelope is removed`() {
        val code = "[)>\u001E06${gs}9N03752864${gs}D160617\u001E\u0004"
        assertEquals(
            CodeFields(product = "03752864", expiry = "160617"),
            codeFields(code)
        )
    }

    @Test
    fun `production date is not taken for expiry`() {
        val code = "9N03752864${gs}16D20240115${gs}D260131"
        assertEquals("260131", codeFields(code)?.expiry)
    }

    @Test
    fun `a plain QR has no fields`() {
        assertNull(codeFields("https://example.com/01234"))
    }

    // Exactly the strings on the test Data Matrix sheet, as a scanner returns them. The
    // leading GS is FNC1, which some scanners put at the start; the app trims it first.

    @Test
    fun `test sheet - eu medicine`() {
        val raw = "${gs}01095060001343521727063010ABC123${gs}21SN7Q2K9X4P"
        assertEquals(
            CodeFields(product = "09506000134352", serial = "SN7Q2K9X4P", batch = "ABC123", expiry = "270630"),
            codeFields(normalizeCode(raw))
        )
        assertEquals("010950600013435221SN7Q2K9X4P", shortCode(normalizeCode(raw)))
    }

    @Test
    fun `test sheet - german ppn`() {
        val raw = "[)>\u001E06${gs}9N111234567842${gs}1TCH42B${gs}D270630${gs}SRX5TT8Q3MZ\u001E\u0004"
        assertEquals(
            CodeFields(product = "111234567842", serial = "RX5TT8Q3MZ", batch = "CH42B", expiry = "270630"),
            codeFields(normalizeCode(raw))
        )
    }

    @Test
    fun `test sheet - chestny znak without expiry and batch`() {
        val raw = "${gs}0104600439931256215'Lk3xFq&Ndps${gs}91EE11${gs}92dGVzdGtleXRlc3RrZXl0ZXN0a2V5dGVzdGtleXRlc3Q="
        val fields = codeFields(normalizeCode(raw))
        assertEquals("04600439931256", fields?.product)
        assertEquals("5'Lk3xFq&Ndps", fields?.serial)
        assertNull(fields?.expiry)
        assertNull(fields?.batch)
    }

    @Test
    fun `digital link - fields from the path and expiry from the query`() {
        // exactly what the GS1 Digital Link Data Matrix generator produces
        val code = "https://dalgiardino.com/01/09506000134376/10/ABC/21/123456?17=221231"
        assertEquals(
            CodeFields(product = "09506000134376", serial = "123456", batch = "ABC", expiry = "221231"),
            codeFields(code)
        )
        assertEquals("0109506000134376" + "21123456", shortCode(code))
    }

    @Test
    fun `digital link - short names, and gtin-13 is padded with a zero`() {
        val code = "https://id.example.org/gtin/9506000134376/lot/A%2F1/ser/77?exp=270101&utm=x"
        assertEquals(
            CodeFields(product = "09506000134376", serial = "77", batch = "A/1", expiry = "270101"),
            codeFields(code)
        )
    }

    @Test
    fun `a plain link is not a digital link`() {
        assertNull(parseDigitalLink("https://example.com/a/b?c=1"))
        assertNull(parseDigitalLink("https://example.com/01/abc"))
        assertEquals("https://example.com/01/abc", shortCode("https://example.com/01/abc"))
    }

    @Test
    fun `german ppn - short form without the envelope`() {
        val raw = "[)>\u001E06${gs}9N111234567842${gs}1TCH42B${gs}D270630${gs}SRX5TT8Q3MZ\u001E\u0004"
        assertEquals("9N111234567842SRX5TT8Q3MZ", shortCode(raw))
    }

    @Test
    fun `a link with 01 in the path is not a digital link if the gtin fails`() {
        // the check digit of 12345678 is 0, not 8
        assertNull(parseDigitalLink("https://shop.example/catalog/01/12345678"))
        // what follows the gtin is not a GS1 identifier but a plain path
        assertNull(parseDigitalLink("https://site.example/01/09506000134376/2024/05"))
        assertEquals("https://shop.example/catalog/01/12345678", shortCode("https://shop.example/catalog/01/12345678"))
    }

    @Test
    fun `digital link - percent-encoded cyrillic and plus in the query`() {
        val code = "https://id.example/01/09506000134376/10/%D0%9F%D0%B0%D1%80%D1%82%D0%B8%D1%8F?21=A+B"
        val fields = codeFields(code)
        assertEquals("Партия", fields?.batch)
        assertEquals("A B", fields?.serial)
    }

    @Test
    fun `a logistics label with sscc and content gtin gives expiry and batch`() {
        val code = "00123456789012345678${gs}0204612345678904${gs}10LOT1${gs}17261231"
        assertEquals(
            CodeFields(product = "04612345678904", batch = "LOT1", expiry = "261231"),
            codeFields(code)
        )
    }

    @Test
    fun `chestny znak dairy - expiry with time in 7003`() {
        val code = "0104600439931256215ABCDE${gs}937a8b${gs}7003261231120${gs}"
        // (7003) is YYMMDDhhmm; the expiry is its first six digits
        val fixed = "0104600439931256215ABCDE${gs}937a8b${gs}70032612311200"
        assertEquals("261231", codeFields(fixed)?.expiry)
        assertEquals(CodeKind.CHESTNY_ZNAK, codeKind(fixed))
        assertNull(codeFields(code)?.expiry)
    }

    @Test
    fun `a cigarette carton with price 8005 parses`() {
        val code = "0104600439931256215ABCDE${gs}8005012500${gs}93abcd"
        assertEquals(CodeKind.CHESTNY_ZNAK, codeKind(code))
        assertEquals("04600439931256", codeFields(code)?.product)
    }

    @Test
    fun `letters in a fixed-length field mean not gs1`() {
        assertNull(parseGs1("01ABCDEFGHIJKLMN"))
        assertNull(codeKind("01ABCDEFGHIJKLMN"))
    }

    @Test
    fun `a logistics label with content gtin is gs1`() {
        assertEquals(CodeKind.GS1, codeKind("0204612345678904${gs}10LOT1${gs}17261231"))
    }

    @Test
    fun `query in a digital link fragment is not read`() {
        assertNull(codeFields("https://id.example/01/09506000134376#x?17=251231")?.expiry)
    }
}
