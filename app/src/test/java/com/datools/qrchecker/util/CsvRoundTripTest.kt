package com.datools.qrchecker.util

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Выгрузка обязана читаться обратно.
 *
 * Файл из приложения человек отправляет дальше, а получатель заводит по нему сессию тем
 * же приложением. Код, который ушёл в отчёт и не вернулся из него, теряется молча.
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
    fun `код маркировки без запятой возвращается целиком`() {
        // именно он ломался: строка отчёта сама начинается с кода маркировки, и правило
        // про голый код забирало её вместе с колонками
        val code = "010468057733357021ABC123"
        assertEquals(listOf(code), roundTrip(listOf(code)))
    }

    @Test
    fun `неотсканированный код возвращается так же`() {
        val code = "010468057733357021ABC123"
        assertEquals(listOf(code), roundTrip(listOf(code), scanned = false))
    }

    @Test
    fun `код маркировки с криптохвостом возвращается целиком`() {
        val code = "0104680577333570215,'OfIXCFmCGl${gs}9180C3${gs}922Jxke"
        assertEquals(listOf(code), roundTrip(listOf(code)))
    }

    @Test
    fun `линейный штрихкод возвращается`() {
        assertEquals(listOf("4601234567890"), roundTrip(listOf("4601234567890")))
    }

    @Test
    fun `ссылка из обычного QR возвращается`() {
        val code = "https://example.com/a/b?c=1"
        assertEquals(listOf(code), roundTrip(listOf(code)))
    }

    @Test
    fun `код с точкой с запятой возвращается`() {
        assertEquals(listOf("ABC;12345678"), roundTrip(listOf("ABC;12345678")))
    }

    @Test
    fun `несколько разных кодов возвращаются все`() {
        val codes = listOf(
            "010468057733357021ABC123",
            "4601234567890",
            "https://example.com/x"
        )
        assertEquals(codes, roundTrip(codes))
    }

    @Test
    fun `голый код маркировки с разделителем внутри серийника не режется`() {
        // ради этого случая правило про голый код и существует
        val code = "010468057733357021AB;CD"
        assertEquals(listOf(code), parseCodeList(code))
    }

    @Test
    fun `код со знаком формулы уходит в отчёт обезвреженным`() {
        // Excel вычисляет формулу и в кавычках - спасает только апостроф впереди
        val code = "=HYPERLINK(\"https://x/\",\"Open\")"
        val csv = buildCsv("t", "a", "b", "c", listOf(code))
        val row = csv.lines().first { it.contains("HYPERLINK") }
        row.split(';').take(2).forEach { cell ->
            assertEquals("ячейка: $cell", true, cell.trimStart('"').startsWith("'="))
        }
    }

    @Test
    fun `код со знаком формулы возвращается без апострофа`() {
        for (code in listOf("=1+2;HYPERLINK", "+79991234567", "-abcdefgh", "@SUM(A1:A9)")) {
            assertEquals(listOf(code), roundTrip(listOf(code)))
        }
    }

    @Test
    fun `код, который сам начинается с апострофа, возвращается с ним`() {
        // апостроф впереди прикрывается так же, как формула: иначе чтение принимало
        // его за наш и срезало
        for (code in listOf("'+380501234567", "'abcdefgh", "''=SUM(A1)")) {
            assertEquals(listOf(code), roundTrip(listOf(code)))
        }
    }

    @Test
    fun `значение с экранированной кавычкой теряет только обрамление`() {
        assertEquals(listOf("ABC\"DEF12345"), parseCodeList("\"ABC\"\"DEF12345\""))
    }
}
