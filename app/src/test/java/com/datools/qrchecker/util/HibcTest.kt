package com.datools.qrchecker.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** All examples are from the ANSI/HIBC 2.6 standard itself, with its check characters. */
class HibcTest {

    @Test
    fun `primary code - section 4,3,1 and appendix B`() {
        val hibc = parseHibc("+A123BJC5D6E71G")
        assertEquals("+A123BJC5D6E71", hibc?.primary)
        assertEquals("A123BJC5D6E7", hibc?.product)
        assertNull(hibc?.lot)
        assertNull(hibc?.expiry)
    }

    @Test
    fun `concatenated code - expiry as day of year and batch, section 2,2,1,1`() {
        val hibc = parseHibc("+A99912345/\$\$52001510X33")
        assertEquals("A9991234", hibc?.product)
        assertEquals("10X3", hibc?.lot)
        // day 15 of 2020 is January 15
        assertEquals("200115", hibc?.expiry)
    }

    @Test
    fun `supplementary fields - manufacture date and serial, section 2,3,2`() {
        val hibc = parseHibc("*+A99912345/\$\$52001510X3/16D20111212/S77DEFG457*")
        assertEquals("10X3", hibc?.lot)
        assertEquals("77DEFG45", hibc?.serial)
        assertEquals("200115", hibc?.expiry)
    }

    @Test
    fun `full expiry date in field 14D, section 2,3,2,3`() {
        val hibc = parseHibc("+A99912345/\$10X3/16D20111231/14D202001313")
        assertEquals("10X3", hibc?.lot)
        assertEquals("200131", hibc?.expiry)
    }

    @Test
    fun `separate secondary code with a batch only, appendix E1,1`() {
        val hibc = parseHibc("+\$A1234GU")
        assertNull(hibc?.product)
        assertEquals("A1234", hibc?.lot)
    }

    @Test
    fun `date formats from table F1`() {
        // the table has no check characters, so they are computed here
        fun withCheck(body: String): String {
            val charset = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ-. \$/+%"
            return body + charset[body.sumOf { charset.indexOf(it) } % 43]
        }
        // September 28, 2005, 22:00; batch 3C001, serial 0001, link character G
        assertEquals("050900", parseHibc(withCheck("+\$\$09053C001G"))?.expiry)
        assertEquals("050928", parseHibc(withCheck("+\$\$20928053C001G"))?.expiry)
        assertEquals("050928", parseHibc(withCheck("+\$\$30509283C001G"))?.expiry)
        assertEquals("050928", parseHibc(withCheck("+\$\$4050928223C001G"))?.expiry)
        assertEquals("050928", parseHibc(withCheck("+\$\$5052713C001G"))?.expiry)
        assertEquals("050928", parseHibc(withCheck("+\$\$605271223C001G"))?.expiry)
        assertEquals("3C001", parseHibc(withCheck("+\$\$605271223C001G"))?.lot)
        assertEquals("0001", parseHibc(withCheck("+\$\$+5052710001G"))?.serial)
        assertNull(parseHibc(withCheck("+\$\$73C001G"))?.expiry)
    }

    @Test
    fun `a space check character trimmed by normalisation`() {
        // a code is chosen whose check character comes out as a space
        val charset = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ-. \$/+%"
        val alnum = charset.take(36)
        val body = alnum.asSequence()
            .flatMap { a -> alnum.asSequence().map { b -> "+A999$a${b}0" } }
            .first { b -> b.sumOf { charset.indexOf(it) } % 43 == 38 }
        assertEquals(body, parseHibc(normalizeCode("$body "))?.primary)
    }

    @Test
    fun `phone numbers and formulas are not hibc`() {
        assertNull(parseHibc("+79991234567"))
        // this number passes the check; only rejecting the legacy secondary form
        // without "$" rules it out
        assertNull(parseHibc("+79007081940"))
        assertNull(parseHibc("+SUM(A1:A9)"))
        assertNull(codeKind("+79991234567"))
    }

    @Test
    fun `no russian mobile number is taken for hibc`() {
        val random = java.util.Random(42)
        repeat(50_000) {
            val number = "+79" + (0 until 9).joinToString("") { random.nextInt(10).toString() }
            assertNull(number, parseHibc(number))
        }
    }

    @Test
    fun `quantity before the date from versions before 2,6`() {
        fun withCheck(body: String): String {
            val charset = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ-. \$/+%"
            return body + charset[body.sumOf { charset.indexOf(it) } % 43]
        }
        // 8 and two quantity digits, 9 and five; then the usual date format
        val two = parseHibc(withCheck("+\$\$8053050928LOT1G"))
        assertEquals("050928", two?.expiry)
        assertEquals("LOT1", two?.lot)
        assertEquals("050928", parseHibc(withCheck("+\$\$9000253050928LOT1G"))?.expiry)
    }

    @Test
    fun `the list shows the serial, not just the product`() {
        val a = "+A99912345/\$\$52001510X3/S0001"
        val charset = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ-. \$/+%"
        fun withCheck(body: String) = body + charset[body.sumOf { charset.indexOf(it) } % 43]
        assertEquals("+A99912345/S0001", shortCode(withCheck(a)))
        assertEquals("+A99912345/\$10X3", shortCode("+A99912345/\$\$52001510X33"))
    }

    @Test
    fun `code 39 asterisks do not hide the kind`() {
        assertEquals(CodeKind.HIBC, codeKind("*+A99912345/\$\$52001510X3/16D20111212/S77DEFG457*"))
    }

    @Test
    fun `in shared fields and the short form`() {
        val code = "+A99912345/\$\$52001510X33"
        assertEquals(CodeFields(product = "A9991234", batch = "10X3", expiry = "200115"), codeFields(code))
        assertEquals("+A99912345/\$10X3", shortCode(code))
        assertEquals(CodeKind.HIBC, codeKind(code))
    }

    @Test
    fun `survives a csv export`() {
        val code = "+A99912345/\$\$52001510X33"
        val csv = buildCsv("t", "a", "b", "c", listOf(code))
        assertEquals(listOf(code), parseCodeList(csv))
    }
}
