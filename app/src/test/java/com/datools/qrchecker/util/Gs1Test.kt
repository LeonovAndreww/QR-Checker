package com.datools.qrchecker.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Раскладки взяты из разных систем прослеживаемости. Символика у всех одна - Data Matrix
 * с данными по GS1, - различается только набор и порядок полей, и разбор обязан
 * справляться со всеми одинаково.
 */
class Gs1Test {

    private val gs = GROUP_SEPARATOR

    @Test
    fun `честный знак - гтин, серийник и криптохвост`() {
        val code = "0104680577333570215,'OfIXCFmCGl${gs}9180C3${gs}922Jxke"
        assertEquals(
            listOf("01" to "04680577333570", "21" to "5,'OfIXCFmCGl", "91" to "80C3", "92" to "2Jxke"),
            parseGs1(code)
        )
    }

    @Test
    fun `лекарства ес - срок годности и партия перед серийником`() {
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
    fun `у такого кода короткая форма - гтин и серийник, а не срок годности`() {
        val code = "010461234567890417250131${gs}10AB-123${gs}21SN00042"
        assertEquals("010461234567890421SN00042", shortCode(code))
    }

    @Test
    fun `поле фиксированной длины не требует разделителя`() {
        // (01) и (17) заданной длины идут подряд, разделитель только перед партией
        val code = "010461234567890417250131${gs}10L1"
        assertEquals(
            listOf("01" to "04612345678904", "17" to "250131", "10" to "L1"),
            parseGs1(code)
        )
    }

    @Test
    fun `четырёхзначный идентификатор веса читается как шесть цифр`() {
        val code = "01046123456789043103000123${gs}21X1"
        assertEquals(
            listOf("01" to "04612345678904", "3103" to "000123", "21" to "X1"),
            parseGs1(code)
        )
    }

    @Test
    fun `неизвестный идентификатор обрывает разбор`() {
        assertNull(parseGs1("8899abcdef"))
    }

    @Test
    fun `обычная ссылка не выдаёт себя за GS1`() {
        assertNull(parseGs1("https://example.com/01234"))
    }

    @Test
    fun `обрезанное значение фиксированной длины не разбирается`() {
        assertNull(parseGs1("010461234"))
    }

    @Test
    fun `код без серийника укорачивается до одного гтина`() {
        val code = "010461234567890417250131${gs}10AB-123"
        assertEquals("0104612345678904", shortCode(code))
    }

    @Test
    fun `неразобравшийся код с разделителем режется по нему, как раньше`() {
        val code = "8899abcdef${gs}хвост"
        assertEquals("8899abcdef", shortCode(code))
    }

    @Test
    fun `поля берутся из GS1 независимо от порядка`() {
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
    fun `немецкий PPN разбирается своим словарём`() {
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
    fun `конверт ISO 15434 снимается`() {
        val code = "[)>\u001E06${gs}9N03752864${gs}D160617\u001E\u0004"
        assertEquals(
            CodeFields(product = "03752864", expiry = "160617"),
            codeFields(code)
        )
    }

    @Test
    fun `дата изготовления не путается со сроком годности`() {
        val code = "9N03752864${gs}16D20240115${gs}D260131"
        assertEquals("260131", codeFields(code)?.expiry)
    }

    @Test
    fun `у обычного QR полей нет`() {
        assertNull(codeFields("https://example.com/01234"))
    }

    // Ровно те строки, что зашиты в тестовый лист Data Matrix: что сканер отдаёт с
    // листа, то здесь и разбирается. Ведущий GS - это FNC1, который часть сканеров
    // возвращает в начале кода; приложение срезает его ещё до разбора.

    @Test
    fun `тестовый лист - лекарство ес`() {
        val raw = "${gs}01095060001343521727063010ABC123${gs}21SN7Q2K9X4P"
        assertEquals(
            CodeFields(product = "09506000134352", serial = "SN7Q2K9X4P", batch = "ABC123", expiry = "270630"),
            codeFields(normalizeCode(raw))
        )
        assertEquals("010950600013435221SN7Q2K9X4P", shortCode(normalizeCode(raw)))
    }

    @Test
    fun `тестовый лист - немецкий ppn`() {
        val raw = "[)>\u001E06${gs}9N111234567842${gs}1TCH42B${gs}D270630${gs}SRX5TT8Q3MZ\u001E\u0004"
        assertEquals(
            CodeFields(product = "111234567842", serial = "RX5TT8Q3MZ", batch = "CH42B", expiry = "270630"),
            codeFields(normalizeCode(raw))
        )
    }

    @Test
    fun `тестовый лист - честный знак без срока и партии`() {
        val raw = "${gs}0104600439931256215'Lk3xFq&Ndps${gs}91EE11${gs}92dGVzdGtleXRlc3RrZXl0ZXN0a2V5dGVzdGtleXRlc3Q="
        val fields = codeFields(normalizeCode(raw))
        assertEquals("04600439931256", fields?.product)
        assertEquals("5'Lk3xFq&Ndps", fields?.serial)
        assertNull(fields?.expiry)
        assertNull(fields?.batch)
    }

    @Test
    fun `digital link - поля из пути и срок из параметров`() {
        // ровно то, что даёт генератор GS1 Digital Link Data Matrix
        val code = "https://dalgiardino.com/01/09506000134376/10/ABC/21/123456?17=221231"
        assertEquals(
            CodeFields(product = "09506000134376", serial = "123456", batch = "ABC", expiry = "221231"),
            codeFields(code)
        )
        assertEquals("0109506000134376" + "21123456", shortCode(code))
    }

    @Test
    fun `digital link - короткие имена и gtin-13 добиваются нулём`() {
        val code = "https://id.example.org/gtin/9506000134376/lot/A%2F1/ser/77?exp=270101&utm=x"
        assertEquals(
            CodeFields(product = "09506000134376", serial = "77", batch = "A/1", expiry = "270101"),
            codeFields(code)
        )
    }

    @Test
    fun `обычная ссылка - не digital link`() {
        assertNull(parseDigitalLink("https://example.com/a/b?c=1"))
        assertNull(parseDigitalLink("https://example.com/01/abc"))
        assertEquals("https://example.com/01/abc", shortCode("https://example.com/01/abc"))
    }

    @Test
    fun `немецкий ppn - короткая форма без конверта`() {
        val raw = "[)>\u001E06${gs}9N111234567842${gs}1TCH42B${gs}D270630${gs}SRX5TT8Q3MZ\u001E\u0004"
        assertEquals("9N111234567842SRX5TT8Q3MZ", shortCode(raw))
    }

    @Test
    fun `ссылка с 01 в адресе - не digital link, если номер не сходится`() {
        // контрольная цифра у 12345678 - 0, а не 8
        assertNull(parseDigitalLink("https://shop.example/catalog/01/12345678"))
        // после номера идёт не идентификатор GS1, а просто путь
        assertNull(parseDigitalLink("https://site.example/01/09506000134376/2024/05"))
        assertEquals("https://shop.example/catalog/01/12345678", shortCode("https://shop.example/catalog/01/12345678"))
    }

    @Test
    fun `digital link - кириллица в процентах и плюс в параметрах`() {
        val code = "https://id.example/01/09506000134376/10/%D0%9F%D0%B0%D1%80%D1%82%D0%B8%D1%8F?21=A+B"
        val fields = codeFields(code)
        assertEquals("Партия", fields?.batch)
        assertEquals("A B", fields?.serial)
    }

    @Test
    fun `логистическая этикетка с sscc и gtin содержимого тоже даёт срок и партию`() {
        val code = "00123456789012345678${gs}0204612345678904${gs}10LOT1${gs}17261231"
        assertEquals(
            CodeFields(product = "04612345678904", batch = "LOT1", expiry = "261231"),
            codeFields(code)
        )
    }
}
