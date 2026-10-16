package com.datools.qrchecker.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CodeKindTest {

    private val gs = GROUP_SEPARATOR

    @Test
    fun `честный знак по криптохвосту`() {
        assertEquals(CodeKind.CHESTNY_ZNAK, codeKind("0104600439931256215'Lk3xFq&Ndps${gs}91EE11${gs}92abc"))
        // и без разделителей
        assertEquals(CodeKind.CHESTNY_ZNAK, codeKind("0104600439931256215'Lk3xFq&Ndps91EE1192abc"))
    }

    @Test
    fun `лекарство ес - просто gs1`() {
        assertEquals(CodeKind.GS1, codeKind("01095060001343521727063010ABC123${gs}21SN7Q2K9X4P"))
    }

    @Test
    fun `ppn и digital link`() {
        assertEquals(CodeKind.IFA_PPN, codeKind("[)>\u001E06${gs}9N111234567842${gs}SRX5TT8Q3MZ\u001E\u0004"))
        assertEquals(
            CodeKind.GS1_DIGITAL_LINK,
            codeKind("https://dalgiardino.com/01/09506000134376/10/ABC/21/123456?17=221231")
        )
    }

    @Test
    fun `штрихкоды по длине и контрольной цифре`() {
        assertEquals(CodeKind.EAN13, codeKind("4601234567893"))
        assertEquals(CodeKind.EAN8, codeKind("96385074"))
        assertEquals(CodeKind.UPCA, codeKind("717523212765"))
        // контрольная цифра не сошлась - это не EAN, а просто цифры
        assertNull(codeKind("4601234567890"))
    }

    @Test
    fun `ean на 10 не разбирается как партия gs1`() {
        // GS1-префиксы 100-139 - это США, такие EAN существуют
        val ean = "1012345678904"
        assertEquals(CodeKind.EAN13, codeKind(ean))
        assertNull(codeFields(ean))
    }

    @Test
    fun `ссылка и текст`() {
        assertEquals(CodeKind.LINK, codeKind("https://example.com/a"))
        assertNull(codeKind("просто текст"))
    }

    @Test
    fun `внутреннее поле 91 у лекарства ес - не честный знак`() {
        assertEquals(CodeKind.GS1, codeKind("0109506000134352${gs}10ABC${gs}91INTERNAL"))
        assertEquals(CodeKind.GS1, codeKind("010950600013435217270630${gs}21SN1${gs}91AB${gs}92CD"))
    }
}
