package com.datools.qrchecker.files

import com.datools.qrchecker.codes.GROUP_SEPARATOR
import com.datools.qrchecker.R
import com.datools.qrchecker.model.SessionData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    private fun failsWith(expected: Int, text: String) {
        try {
            readSessionFile(text)
            fail("ожидалась ошибка на: $text")
        } catch (e: SessionFileException) {
            assertEquals(expected, e.messageRes)
        }
    }

    @Test
    fun `a session survives writing and reading intact`() {
        val read = readSessionFile(writeSessionFile(session, savedAt = 1_700_000_000_000)).session
        assertEquals(session.id, read.id)
        assertEquals(session.name, read.name)
        assertEquals(session.codes, read.codes)
        assertEquals(session.scannedCodes, read.scannedCodes)
    }

    @Test
    fun `code order is kept`() {
        val reversed = session.copy(codes = listOf("A3", "A1", "A2"))
        assertEquals(listOf("A3", "A1", "A2"), readSessionFile(writeSessionFile(reversed)).session.codes)
    }

    @Test
    fun `save time reads back as written`() {
        assertEquals(1_700_000_000_000, readSessionFile(writeSessionFile(session, 1_700_000_000_000)).savedAt)
    }

    @Test
    fun `a GS1 separator inside a code is kept`() {
        val marked = "0104680577333570215,'OfIXCFmCGl${GROUP_SEPARATOR}9180C3"
        val one = SessionData("s", "n", listOf(marked), emptyList())
        assertEquals(marked, readSessionFile(writeSessionFile(one)).session.codes.single())
    }

    @Test
    fun `a foreign file is rejected with a readable message`() {
        failsWith(R.string.session_file_not_json, "не json вовсе {")
        failsWith(R.string.session_file_not_object, "[1, 2, 3]")
        failsWith(R.string.session_file_foreign, """{"format":"something.else","version":1}""")
    }

    @Test
    fun `a file from a newer version is not silent`() {
        failsWith(
            R.string.session_file_future,
            """{"format":"qrchecker.session","version":99,"id":"s","codes":[{"code":"A"}]}"""
        )
    }

    @Test
    fun `an empty code list is an error, not an empty session`() {
        failsWith(
            R.string.session_file_empty,
            """{"format":"qrchecker.session","version":1,"id":"s","codes":[]}"""
        )
    }

    @Test
    fun `unknown fields do not prevent opening`() {
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
    fun `broken entries are skipped, intact ones kept`() {
        val text = """
            {"format":"qrchecker.session","version":1,"id":"s",
             "codes":[{"code":"A1"},{"noCode":true},"строка",{"code":""},{"code":"A2","scanned":true}]}
        """.trimIndent()
        val read = readSessionFile(text).session
        assertEquals(listOf("A1", "A2"), read.codes)
        assertEquals(listOf("A2"), read.scannedCodes)
    }

    @Test
    fun `a repeated code is not duplicated`() {
        val text = """
            {"format":"qrchecker.session","version":1,"id":"s",
             "codes":[{"code":"A1"},{"code":"A1","scanned":true}]}
        """.trimIndent()
        assertEquals(listOf("A1"), readSessionFile(text).session.codes)
    }

    @Test
    fun `scan time survives writing and reading`() {
        val withTimes = session.copy(scanTimes = mapOf("A2" to 1_700_000_000_000L))
        val read = readSessionFile(writeSessionFile(withTimes)).session
        assertEquals(mapOf("A2" to 1_700_000_000_000L), read.scanTimes)
    }

    @Test
    fun `an unscanned code has no time rather than zero`() {
        val read = readSessionFile(writeSessionFile(session)).session
        assertEquals(emptyMap<String, Long>(), read.scanTimes)
    }

    @Test
    fun `a file without scan times opens`() {
        val text = """
            {"format":"qrchecker.session","version":1,"id":"s","name":"n",
             "codes":[{"code":"A1","scanned":true}]}
        """.trimIndent()
        val read = readSessionFile(text).session
        assertEquals(listOf("A1"), read.scannedCodes)
        assertEquals(emptyMap<String, Long>(), read.scanTimes)
    }

    @Test
    fun `a collecting session survives writing and reading`() {
        val session = SessionData(
            id = "s1",
            name = "Приёмка",
            codes = listOf("A", "B"),
            scannedCodes = listOf("A"),
            scanTimes = mapOf("A" to 100L),
            collecting = true
        )
        val back = readSessionFile(writeSessionFile(session, savedAt = 7L)).session
        assertTrue(back.collecting)
        assertEquals(listOf("A", "B"), back.codes)
    }

    @Test
    fun `an empty collecting session is a valid file`() {
        val session = SessionData(
            id = "s2",
            name = "Пустая",
            codes = emptyList(),
            scannedCodes = emptyList(),
            collecting = true
        )
        val back = readSessionFile(writeSessionFile(session, savedAt = 0L)).session
        assertTrue(back.collecting)
        assertTrue(back.codes.isEmpty())
    }

    @Test
    fun `a regular session file has no collecting flag`() {
        val session = SessionData(
            id = "s3",
            name = "Обычная",
            codes = listOf("A"),
            scannedCodes = emptyList()
        )
        val text = writeSessionFile(session, savedAt = 0L)
        assertFalse(text.contains("collecting"))
        assertFalse(readSessionFile(text).session.collecting)
    }
}
