package com.datools.qrchecker.codes

import com.datools.qrchecker.files.needsFormulaGuard

/**
 * Codes from a text list.
 *
 * The Chestny ZNAK account exports codes as CSV, and that is how most people get them;
 * PDF is just one option, for printing labels. So lists are read on a par with documents.
 *
 * The hard part is that the table separators occur inside the codes: serials are made
 * of arbitrary characters, commas and semicolons included. Splitting blindly would cut
 * codes silently, so a row goes through a sequence of rules, most reliable first.
 */
private const val SHORTEST_CODE = 8

/**
 * Only semicolon and tab separate columns. A comma does not: it occurs in marking codes,
 * and Chestny ZNAK exports use semicolons, as Excel does in the Russian locale.
 */
private val SEPARATORS = charArrayOf(';', '\t')

/** (01)GTIN(21)serial, the shape of a marking code; a whole row in this shape is a code. */
private val MARKING_CODE = Regex("^01\\d{14}21.+", RegexOption.DOT_MATCHES_ALL)

fun parseCodeList(text: String): List<String> {
    val codes = LinkedHashSet<String>()

    for (rawLine in text.lineSequence()) {
        val line = rawLine.trim().removePrefix("\uFEFF")
        if (line.isEmpty()) continue
        // the "sep=;" line our own reports start with is not a code
        if (line.startsWith("sep=", ignoreCase = true)) continue

        val code = codeFromLine(line) ?: continue
        codes += code
    }

    return codes.toList()
}

private fun codeFromLine(line: String): String? {
    val fields = splitCsvRow(line)

    // 1. The app's own report, recognised by structure rather than headers, which are
    //    translated: the first column is exactly the short form of the second.
    //    Checked before the bare-code rule, since a report row also starts with a marking
    //    code and would otherwise become one "code;code;time" code.
    if (fields.size == 3) {
        val full = normalizeCode(fields[1]).withoutFormulaGuard()
        if (full.isNotEmpty() && normalizeCode(fields[0]).withoutFormulaGuard() == shortCode(full)) {
            return full.asCode()
        }
    }

    // 2. No quotes and the row has the shape of a marking code: a bare code. A semicolon
    //    in it belongs to the serial, since a CSV field containing a separator must be
    //    quoted.
    val bare = normalizeCode(line)
    if ('"' !in line && MARKING_CODE.matches(bare)) return bare.asCode()

    // 3. A single field means no columns: the parsed field is the value. The parsed one,
    //    so an escaped quote inside does not leave the outer quotes behind.
    if (fields.size == 1) return normalizeCode(fields[0]).asCode()

    // 4. A table: take what looks like a marking code, or else the longest field;
    //    headers like "code" or "gtin" are shorter than any code.
    val values = fields.mapNotNull { normalizeCode(it).asCode() }
    return values.filter { MARKING_CODE.matches(it) }.maxByOrNull { it.length }
        ?: values.maxByOrNull { it.length }
}

/** The apostrophe an export puts before a cell starting with a formula sign is not part of the code. */
private fun String.withoutFormulaGuard(): String =
    if (startsWith('\'') && needsFormulaGuard(substring(1))) substring(1) else this

private fun String.asCode(): String? =
    takeIf { it.length >= SHORTEST_CODE && !it.contains(' ') }

/** Splits a CSV row into fields, ignoring separators inside quotes. */
private fun splitCsvRow(line: String): List<String> {
    val fields = ArrayList<String>()
    val field = StringBuilder()
    var inQuotes = false
    var index = 0

    while (index < line.length) {
        val ch = line[index]
        when {
            ch == '"' && inQuotes && index + 1 < line.length && line[index + 1] == '"' -> {
                field.append('"')
                index++
            }

            ch == '"' -> inQuotes = !inQuotes
            !inQuotes && ch in SEPARATORS -> {
                fields += field.toString().trim()
                field.setLength(0)
            }

            else -> field.append(ch)
        }
        index++
    }
    fields += field.toString().trim()

    return fields
}
