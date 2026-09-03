package com.datools.qrchecker.util

import com.datools.qrchecker.R
import com.datools.qrchecker.model.SessionData
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class SessionFileTest {

    private val session = SessionData(
        id = "s-1",
        name = "Партия 12",
        codes = listOf("A1", "A2", "A3"),
        scannedCodes = listOf("A2")
    )

    private fun failsWith(expected: Int, text: String) {
        try {
            readSessionFile(text)
            fail("ожидалась ошибка на: $text")
        } catch (e: SessionFileException) {
            assertEquals(expected, e.messageRes)
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
        failsWith(R.string.session_file_not_json, "не json вовсе {")
        failsWith(R.string.session_file_not_object, "[1, 2, 3]")
        failsWith(R.string.session_file_foreign, """{"format":"something.else","version":1}""")
    }

    @Test
    fun `файл из будущей версии не молчит`() {
        failsWith(
            R.string.session_file_future,
            """{"format":"qrchecker.session","version":99,"id":"s","codes":[{"code":"A"}]}"""
        )
    }

    @Test
    fun `пустой список кодов - ошибка, а не пустая сессия`() {
        failsWith(
            R.string.session_file_empty,
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

    @Test
    fun `время отметки переживает запись и чтение`() {
        val withTimes = session.copy(scanTimes = mapOf("A2" to 1_700_000_000_000L))
        val read = readSessionFile(writeSessionFile(withTimes)).session
        assertEquals(mapOf("A2" to 1_700_000_000_000L), read.scanTimes)
    }

    @Test
    fun `у неотмеченного кода времени нет, а не ноль`() {
        val read = readSessionFile(writeSessionFile(session)).session
        assertEquals(emptyMap<String, Long>(), read.scanTimes)
    }

    @Test
    fun `файл прошлой версии без времени открывается`() {
        val text = """
            {"format":"qrchecker.session","version":1,"id":"s","name":"n",
             "codes":[{"code":"A1","scanned":true}]}
        """.trimIndent()
        val read = readSessionFile(text).session
        assertEquals(listOf("A1"), read.scannedCodes)
        assertEquals(emptyMap<String, Long>(), read.scanTimes)
    }
}
