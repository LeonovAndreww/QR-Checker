package com.datools.qrchecker.codes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CodeKindTest {

    private val gs = GROUP_SEPARATOR

    @Test
    fun `chestny znak by its crypto tail`() {
        assertEquals(CodeKind.CHESTNY_ZNAK, codeKind("0104600439931256215'Lk3xFq&Ndps${gs}91EE11${gs}92abc"))
        // also without separators
        assertEquals(CodeKind.CHESTNY_ZNAK, codeKind("0104600439931256215'Lk3xFq&Ndps91EE1192abc"))
    }

    @Test
    fun `eu medicine is plain gs1`() {
        assertEquals(CodeKind.GS1, codeKind("01095060001343521727063010ABC123${gs}21SN7Q2K9X4P"))
    }

    @Test
    fun `ppn and digital link`() {
        assertEquals(CodeKind.IFA_PPN, codeKind("[)>\u001E06${gs}9N111234567842${gs}SRX5TT8Q3MZ\u001E\u0004"))
        assertEquals(
            CodeKind.GS1_DIGITAL_LINK,
            codeKind("https://dalgiardino.com/01/09506000134376/10/ABC/21/123456?17=221231")
        )
    }

    @Test
    fun `barcodes by length and check digit`() {
        assertEquals(CodeKind.EAN13, codeKind("4601234567893"))
        assertEquals(CodeKind.EAN8, codeKind("96385074"))
        assertEquals(CodeKind.UPCA, codeKind("717523212765"))
        // a failed check digit means plain digits, not an EAN
        assertNull(codeKind("4601234567890"))
    }

    @Test
    fun `an ean starting with 10 is not parsed as a gs1 batch`() {
        // GS1 prefixes 100-139 are the US; such EANs exist
        val ean = "1012345678904"
        assertEquals(CodeKind.EAN13, codeKind(ean))
        assertNull(codeFields(ean))
    }

    @Test
    fun `link and text`() {
        assertEquals(CodeKind.LINK, codeKind("https://example.com/a"))
        assertNull(codeKind("просто текст"))
    }

    @Test
    fun `an internal field 91 on an eu medicine is not chestny znak`() {
        assertEquals(CodeKind.GS1, codeKind("0109506000134352${gs}10ABC${gs}91INTERNAL"))
        assertEquals(CodeKind.GS1, codeKind("010950600013435217270630${gs}21SN1${gs}91AB${gs}92CD"))
    }
}
