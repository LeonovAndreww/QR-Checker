package com.datools.qrchecker.codes

import androidx.annotation.StringRes
import com.datools.qrchecker.R

/**
 * What kind of code this is, judged by its content rather than its symbology: only the
 * text is stored, the same code arrives from the camera, a PDF or an export, and a
 * warehouse cares about "Chestny ZNAK" or "medicine with a batch", not "Data Matrix".
 */
enum class CodeKind(@get:StringRes val label: Int) {
    CHESTNY_ZNAK(R.string.kind_chestny_znak),
    GS1(R.string.kind_gs1),
    GS1_DIGITAL_LINK(R.string.kind_gs1_digital_link),
    IFA_PPN(R.string.kind_ifa_ppn),
    HIBC(R.string.kind_hibc),
    EAN13(R.string.kind_ean13),
    EAN8(R.string.kind_ean8),
    UPCA(R.string.kind_upca),
    LINK(R.string.kind_link)
}

/** The kind of a code and its fields, from a single parse. */
data class CodeDescription(val kind: CodeKind?, val fields: CodeFields?)

private val NOTHING = CodeDescription(null, null)

/**
 * Parses a code once and answers both questions: what it is and what it carries. Two
 * separate parses doubled the work per list row and could disagree with each other.
 */
fun describeCode(raw: String): CodeDescription {
    val code = normalizeCode(raw)
    if (code.isEmpty()) return NOTHING

    if (isHttpUrl(code)) {
        val link = parseDigitalLink(code) ?: return CodeDescription(CodeKind.LINK, null)
        return CodeDescription(CodeKind.GS1_DIGITAL_LINK, fieldsOfGs1(link))
    }

    if (code.startsWith("[)>") || code.startsWith("9N")) {
        ppnFields(code)?.let { return CodeDescription(CodeKind.IFA_PPN, it) }
    }

    if (code.trimStart('*').startsWith("+")) {
        parseHibc(code)?.let {
            return CodeDescription(
                CodeKind.HIBC,
                CodeFields(product = it.product, serial = it.serial, batch = it.lot, expiry = it.expiry)
            )
        }
    }

    // Without an item number a string of digits is more likely a plain barcode: an EAN
    // starting with 10 or 17 would otherwise parse as a batch or an expiry.
    parseGs1(code)?.takeIf { pairs -> pairs.any { it.first in GS1_KEYS } }?.let { pairs ->
        return CodeDescription(gs1Kind(code, pairs), fieldsOfGs1(pairs))
    }

    if (code.all { it.isDigit() } && hasValidCheckDigit(code)) {
        when (code.length) {
            13 -> return CodeDescription(CodeKind.EAN13, null)
            8 -> return CodeDescription(CodeKind.EAN8, null)
            12 -> return CodeDescription(CodeKind.UPCA, null)
        }
    }
    return NOTHING
}

/** null when nothing definite can be said; no label is shown then. */
fun codeKind(raw: String): CodeKind? = describeCode(raw).kind

private fun gs1Kind(code: String, pairs: List<Pair<String, String>>): CodeKind {
    // without separators the crypto tail is recognised by its (91)xxxx(92) shape
    if (code.indexOf(GROUP_SEPARATOR) < 0 && GS1_WITHOUT_SEPARATORS.matches(code)) {
        return CodeKind.CHESTNY_ZNAK
    }
    val ais = pairs.mapTo(HashSet()) { it.first }
    // Chestny ZNAK is told by its crypto tail: (91)+(92), or a short (93) after the serial.
    // 91-99 alone are company-internal fields found anywhere. A Chestny ZNAK code has no
    // batch and puts expiry in (7003), so an EU medicine with an internal (91) is told
    // apart by its (10) and (17).
    val crypto = ("91" in ais && "92" in ais) || "93" in ais
    val chestny = crypto && "21" in ais && "10" !in ais && "17" !in ais
    return if (chestny) CodeKind.CHESTNY_ZNAK else CodeKind.GS1
}

/** EAN/UPC/GTIN check digit: weights 3 and 1 from the right, last digit excluded. */
internal fun hasValidCheckDigit(digits: String): Boolean {
    if (digits.length < 2) return false
    var sum = 0
    val body = digits.dropLast(1)
    for (i in body.indices) {
        val d = body[body.length - 1 - i] - '0'
        sum += if (i % 2 == 0) d * 3 else d
    }
    return (10 - sum % 10) % 10 == digits.last() - '0'
}
