package com.datools.qrchecker.util

import com.datools.qrchecker.model.SessionData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SessionFileTest {

    private val session = SessionData(
        id = "s-1",
        name = "Партия 12",
        codes = listOf("A1", "A2", "A3"),
        scannedCodes = listOf("A2")
    )

    private fun failsWith(part: String, text: String) {
        try {
            readSessionFile(text)
            fail("ожидалась ошибка на: $text")
        } catch (e: SessionFileException) {
            assertTrue("сообщение было: ${e.message}", e.message.orEmpty().contains(part))
        }
    }

    @Test
    fun `сессия переживает запись и чтение без потерь`() {
        val read = readSessionFile(writeSessionFile(session, savedAt = 1_700_000_000_000)).session
        assertEquals(session.id, read.id)
        assertEquals(session.name, read.name)
        assertEquals(session.codes, read.codes)
        assertEquals(session.scannedCodes, read.scannedCodes)
    }

    @Test
    fun `порядок кодов сохраняется`() {
        val reversed = session.copy(codes = listOf("A3", "A1", "A2"))
        assertEquals(listOf("A3", "A1", "A2"), readSessionFile(writeSessionFile(reversed)).session.codes)
    }

    @Test
    fun `время сохранения возвращается как записано`() {
        assertEquals(1_700_000_000_000, readSessionFile(writeSessionFile(session, 1_700_000_000_000)).savedAt)
    }

    @Test
    fun `разделитель GS1 внутри кода не теряется`() {
        val marked = "0104680577333570215,'OfIXCFmCGl${GROUP_SEPARATOR}9180C3"
        val one = SessionData("s", "n", listOf(marked), emptyList())
        assertEquals(marked, readSessionFile(writeSessionFile(one)).session.codes.single())
    }

    @Test
    fun `чужой файл отвергается с внятным текстом`() {
        failsWith("не читается", "не json вовсе {")
        failsWith("не похож", "[1, 2, 3]")
        failsWith("не файл сессии", """{"format":"something.else","version":1}""")
    }

    @Test
    fun `файл из будущей версии не молчит`() {
        failsWith(
            "новой версией",
            """{"format":"qrchecker.session","version":99,"id":"s","codes":[{"code":"A"}]}"""
        )
    }

    @Test
    fun `пустой список кодов - ошибка, а не пустая сессия`() {
        failsWith(
            "ни одного кода",
            """{"format":"qrchecker.session","version":1,"id":"s","codes":[]}"""
        )
    }

    @Test
    fun `незнакомые поля не мешают открыть файл`() {
        val text = """
            {"format":"qrchecker.session","version":1,"id":"s","name":"n",
             "somethingNew":{"a":1},
             "codes":[{"code":"A1","scanned":true,"note":"x"}]}
        """.trimIndent()
        val read = readSessionFile(text).session
        assertEquals(listOf("A1"), read.codes)
        assertEquals(listOf("A1"), read.scannedCodes)
    }

    @Test
    fun `битые записи пропускаются, целые остаются`() {
        val text = """
            {"format":"qrchecker.session","version":1,"id":"s",
             "codes":[{"code":"A1"},{"noCode":true},"строка",{"code":""},{"code":"A2","scanned":true}]}
        """.trimIndent()
        val read = readSessionFile(text).session
        assertEquals(listOf("A1", "A2"), read.codes)
        assertEquals(listOf("A2"), read.scannedCodes)
    }

    @Test
    fun `повтор кода не задваивается`() {
        val text = """
            {"format":"qrchecker.session","version":1,"id":"s",
             "codes":[{"code":"A1"},{"code":"A1","scanned":true}]}
        """.trimIndent()
        assertEquals(listOf("A1"), readSessionFile(text).session.codes)
    }
}
