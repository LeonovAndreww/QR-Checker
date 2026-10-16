package com.datools.qrchecker.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Все примеры - из самого стандарта ANSI/HIBC 2.6, с его контрольными знаками. */
class HibcTest {

    @Test
    fun `основной код - раздел 4,3,1 и приложение B`() {
        val hibc = parseHibc("+A123BJC5D6E71G")
        assertEquals("+A123BJC5D6E71", hibc?.primary)
        assertEquals("A123BJC5D6E7", hibc?.product)
        assertNull(hibc?.lot)
        assertNull(hibc?.expiry)
    }

    @Test
    fun `совмещённый код - срок днём года и партия, раздел 2,2,1,1`() {
        val hibc = parseHibc("+A99912345/\$\$52001510X33")
        assertEquals("A9991234", hibc?.product)
        assertEquals("10X3", hibc?.lot)
        // 15-й день 2020 года - 15 января
        assertEquals("200115", hibc?.expiry)
    }

    @Test
    fun `дополнительные поля - дата изготовления и серийник, раздел 2,3,2`() {
        val hibc = parseHibc("*+A99912345/\$\$52001510X3/16D20111212/S77DEFG457*")
        assertEquals("10X3", hibc?.lot)
        assertEquals("77DEFG45", hibc?.serial)
        assertEquals("200115", hibc?.expiry)
    }

    @Test
    fun `срок годности полной датой в поле 14D, раздел 2,3,2,3`() {
        val hibc = parseHibc("+A99912345/\$10X3/16D20111231/14D202001313")
        assertEquals("10X3", hibc?.lot)
        assertEquals("200131", hibc?.expiry)
    }

    @Test
    fun `отдельный вторичный код только с партией, приложение E1,1`() {
        val hibc = parseHibc("+\$A1234GU")
        assertNull(hibc?.product)
        assertEquals("A1234", hibc?.lot)
    }

    @Test
    fun `форматы даты из таблицы F1`() {
        // контрольных знаков в таблице нет - досчитываются здесь же
        fun withCheck(body: String): String {
            val charset = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ-. \$/+%"
            return body + charset[body.sumOf { charset.indexOf(it) } % 43]
        }
        // 28 сентября 2005, 22 часа; партия 3C001, серийник 0001, знак связи G
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
    fun `контрольный знак-пробел, срезанный при нормализации`() {
        // подбирается код, у которого контрольный знак выходит пробелом
        val charset = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ-. \$/+%"
        val alnum = charset.take(36)
        val body = alnum.asSequence()
            .flatMap { a -> alnum.asSequence().map { b -> "+A999$a${b}0" } }
            .first { b -> b.sumOf { charset.indexOf(it) } % 43 == 38 }
        assertEquals(body, parseHibc(normalizeCode("$body "))?.primary)
    }

    @Test
    fun `телефон и формула - не hibc`() {
        assertNull(parseHibc("+79991234567"))
        assertNull(parseHibc("+SUM(A1:A9)"))
        assertNull(codeKind("+79991234567"))
    }

    @Test
    fun `в общих полях и короткой записи`() {
        val code = "+A99912345/\$\$52001510X33"
        assertEquals(CodeFields(product = "A9991234", batch = "10X3", expiry = "200115"), codeFields(code))
        assertEquals("+A99912345", shortCode(code))
        assertEquals(CodeKind.HIBC, codeKind(code))
    }

    @Test
    fun `переживает выгрузку в csv`() {
        val code = "+A99912345/\$\$52001510X33"
        val csv = buildCsv("t", "a", "b", "c", listOf(code))
        assertEquals(listOf(code), parseCodeList(csv))
    }
}
