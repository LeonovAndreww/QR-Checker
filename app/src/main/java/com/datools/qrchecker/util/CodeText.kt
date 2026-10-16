package com.datools.qrchecker.util

/** The GS1 field separator (FNC1); significant inside marking codes, never stripped. */
const val GROUP_SEPARATOR = '\u001D'

/**
 * Characters with no meaning (line breaks, zero-width, DEL) that some scanners append.
 * GROUP_SEPARATOR is excluded: it is part of the code.
 */
private val NOISE = Regex("[\\p{C}&&[^\\u001D]]")

/**
 * Brings raw code content to the form it is stored and compared in. Codes from documents
 * and from the camera are matched against each other, so both paths must clean the text
 * the same way, or a scanned code silently misses its row.
 */
fun normalizeCode(raw: String): String = NOISE.replace(raw, "").trim()

/** A marking code whose encoder left out the separators. */
internal val GS1_WITHOUT_SEPARATORS = Regex("^(01\\d{14}21.+?)91.{4}92.+$")

/**
 * (01)GTIN(21)serial, exactly what is printed under the code on the box.
 *
 * The rest is not shown on labels: for Chestny ZNAK it is the (91)(92) crypto tail of a
 * hundred characters, for medicines expiry and batch. Lists keep only the identifying
 * pair, so the row on screen matches the box.
 *
 * The pair comes from a GS1 parse, not from the separator position: field order varies
 * between systems, and for an EU code like (01)(17)(10)(21) cutting at the first
 * separator would leave the expiry instead of the serial.
 *
 * A code without separators is split by its structure instead: there a variable-length
 * field has no boundary, and a standard parse would pull the whole tail into the serial.
 *
 * Anything that does not parse is returned whole: a long string beats one cut wrongly.
 */
fun shortCode(raw: String): String {
    val code = normalizeCode(raw)

    // PPN and Digital Link: otherwise the "[)>06" envelope or the URL would come first
    shortCodeOfOther(code)?.let { return it }

    if (code.indexOf(GROUP_SEPARATOR) < 0) {
        return GS1_WITHOUT_SEPARATORS.matchEntire(code)?.groupValues?.get(1) ?: code
    }

    val parsed = parseGs1(code) ?: return code.substringBefore(GROUP_SEPARATOR)
    val gtin = parsed.firstOrNull { it.first == "01" }?.second ?: return code.substringBefore(GROUP_SEPARATOR)
    val serial = parsed.firstOrNull { it.first == "21" }?.second

    return gtinSerial(gtin, serial)
}
