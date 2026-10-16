package com.datools.qrchecker.codes

/**
 * Splits a code's content into its fields.
 *
 * National traceability systems (Russia's Chestny ZNAK, EU medicines verification,
 * Turkey's İTS, Brazil's SNCM) all write the same thing into a Data Matrix: a chain of
 * GS1 application identifiers and values. Only the set and order of identifiers differ,
 * so parsing follows the standard rather than one country's habits.
 *
 * German IFA PPN uses another identifier dictionary and its own envelope, so it has a
 * parser of its own.
 *
 * Some identifiers have a length fixed by the standard; the rest run to a separator or
 * the end. An unknown identifier stops the parse: showing the whole code beats cutting
 * it in the wrong place.
 */

/** Value lengths of GS1 identifiers with a predefined length. */
private val FIXED: Map<String, Int> = buildMap {
    put("00", 18)
    put("01", 14)
    put("02", 14)
    put("20", 2)
    // dates: YYMMDD
    for (ai in listOf("11", "12", "13", "15", "16", "17")) put(ai, 6)
}

/** Variable-length GS1 identifiers that occur in traceability codes. */
private val VARIABLE = setOf(
    "10", // batch
    "21", // serial number
    "22", "30", "37", "240", "241", "242", "243",
    "710", "711", "712", "713", "714", // national drug numbers
    "91", "92", "93", "94", "95", "96", "97", "98", "99" // company internal data
)

/**
 * Four-digit identifiers with a length fixed by the standard: 7003 is expiry with time
 * (YYMMDDhhmm), used by Chestny ZNAK for short-life dairy; 8005 is the price per unit,
 * the maximum retail price on a carton of cigarettes.
 */
private val FOUR_DIGIT_FIXED = mapOf("7003" to 10, "8005" to 6)

/** Value length of a four-digit identifier: weights, volumes and dimensions take six. */
private fun fixedLengthOfFourDigitAi(ai: String): Int? =
    FOUR_DIGIT_FIXED[ai]
        ?: if (ai.length == 4 && ai.take(2) in setOf("31", "32", "33", "34", "35", "36")) 6 else null

/**
 * Returns identifier and value pairs in the order they are written, or null when the
 * string does not parse as GS1.
 */
fun parseGs1(code: String): List<Pair<String, String>>? {
    if (code.isEmpty()) return null

    val parsed = ArrayList<Pair<String, String>>(4)
    var pos = 0

    while (pos < code.length) {
        // separator after a variable-length field
        if (code[pos] == GROUP_SEPARATOR) {
            pos++
            continue
        }
        if (pos + 2 > code.length) return null

        val two = code.substring(pos, pos + 2)
        if (!two.all { it.isDigit() }) return null

        val four = if (pos + 4 <= code.length) code.substring(pos, pos + 4) else null
        val fourFixed = four
            ?.takeIf { it.all { c -> c.isDigit() } }
            ?.let { fixedLengthOfFourDigitAi(it) }

        val ai: String
        val length: Int?
        when {
            fourFixed != null -> {
                ai = four
                length = fourFixed
            }

            two in FIXED -> {
                ai = two
                length = FIXED[two]
            }

            two in VARIABLE -> {
                ai = two
                length = null
            }

            // three-digit variable-length identifiers
            pos + 3 <= code.length && code.substring(pos, pos + 3) in VARIABLE -> {
                ai = code.substring(pos, pos + 3)
                length = null
            }

            else -> return null
        }

        pos += ai.length
        val value = if (length != null) {
            if (pos + length > code.length) return null
            // every fixed-length field known here is numeric (number, date, weight,
            // price), so a letter means this is not GS1 at all
            code.substring(pos, pos + length).also { pos += length }
                .takeIf { v -> v.all { it.isDigit() } } ?: return null
        } else {
            val end = code.indexOf(GROUP_SEPARATOR, pos).let { if (it < 0) code.length else it }
            code.substring(pos, end).also { pos = end }
        }

        if (value.isEmpty()) return null
        parsed += ai to value
    }

    return parsed.ifEmpty { null }
}

/** ISO/IEC 15434 record separator; the PPN envelope starts with it. */
private const val RECORD_SEPARATOR = '\u001E'

/** End of transmission; closes the envelope. */
private const val END_OF_TRANSMISSION = '\u0004'

/**
 * ASC MH10 data identifiers used by IFA PPN. Matched by prefix in this order, so the
 * short "D" must come after "16D".
 */
private val PPN_IDS = listOf("16D", "9N", "1T", "8P", "D", "S")

/**
 * What a code carries besides its number, whether written with GS1 or PPN identifiers:
 * a warehouse needs the same facts either way.
 */
data class CodeFields(
    val product: String? = null,
    val serial: String? = null,
    val batch: String? = null,
    /** Expiry as written: YYMMDD, where DD may be 00. */
    val expiry: String? = null
) {
    val isEmpty: Boolean
        get() = product == null && serial == null && batch == null && expiry == null
}

/**
 * German IFA PPN: ASC MH10 identifiers (ISO/IEC 15418) in an ISO/IEC 15434 format 06
 * envelope. Deliberately strict: anything that does not fit is not PPN, and the code
 * is shown whole.
 */
private fun parsePpn(code: String): CodeFields? {
    var body = code
    if (body.startsWith("[)>")) {
        val start = body.indexOf(GROUP_SEPARATOR)
        if (start < 0) return null
        body = body.substring(start + 1)
    } else if (!body.startsWith("9N")) {
        return null
    }
    body = body.trimEnd(RECORD_SEPARATOR, END_OF_TRANSMISSION)

    var fields = CodeFields()
    for (part in body.split(GROUP_SEPARATOR)) {
        if (part.isEmpty()) continue
        val id = PPN_IDS.firstOrNull { part.startsWith(it) } ?: return null
        val value = part.removePrefix(id)
        if (value.isEmpty()) return null
        fields = when (id) {
            "9N" -> fields.copy(product = value)
            "1T" -> fields.copy(batch = value)
            "S" -> fields.copy(serial = value)
            "D" -> fields.copy(expiry = value)
            // GTIN and production date occur but are not needed on the label
            else -> fields
        }
    }
    return fields.takeIf { it.product != null }
}

/**
 * Short names that early GS1 Digital Link used in the path instead of digits:
 * /gtin/.../lot/.../ser/...
 */
private val DIGITAL_LINK_ALIASES = mapOf(
    "gtin" to "01", "cpv" to "22", "lot" to "10", "ser" to "21",
    "exp" to "17", "bestBefore" to "15", "prodDate" to "11"
)

/** GTIN lengths Digital Link allows; shorter ones are padded with zeros to 14. */
private val GTIN_LENGTHS = setOf(8, 12, 13, 14)

private fun isKnownAi(ai: String): Boolean =
    ai in FIXED || ai in VARIABLE || fixedLengthOfFourDigitAi(ai) != null

internal fun isHttpUrl(code: String): Boolean =
    code.startsWith("https://", ignoreCase = true) || code.startsWith("http://", ignoreCase = true)

/**
 * GS1 Digital Link: the same GS1 identifiers written as a URL.
 *
 * In https://example.com/01/09506000134376/10/ABC/21/123456?17=221231 the path carries
 * the item number and its qualifiers (batch, serial) and the query the other attributes,
 * expiry among them. Retail packs use it so one QR serves both the till and a shopper's
 * phone.
 *
 * Returns pairs like [parseGs1], or null when this is not a Digital Link. Strict on
 * purpose: the GTIN must pass its check digit and every later path segment must be a
 * known identifier, or an ordinary link with "/01/" in it would show a made-up GTIN.
 */
fun parseDigitalLink(code: String): List<Pair<String, String>>? {
    if (!isHttpUrl(code)) return null

    val afterScheme = code.substringAfter("://")
    // a fragment is not part of the address, and a "?" inside it is no query
    val pathAndQuery = afterScheme.substringAfter('/', "").substringBefore('#')
    val path = pathAndQuery.substringBefore('?')
    val query = pathAndQuery.substringAfter('?', "")

    val segments = path.split('/').filter { it.isNotEmpty() }.map { decodeUrlPart(it) }
    val start = segments.indexOfFirst { (DIGITAL_LINK_ALIASES[it] ?: it) == "01" }
    if (start < 0 || start + 1 >= segments.size) return null

    val gtin = segments[start + 1]
    if (gtin.length !in GTIN_LENGTHS || !gtin.all { it.isDigit() } || !hasValidCheckDigit(gtin)) {
        return null
    }

    val parsed = ArrayList<Pair<String, String>>(4)
    parsed += "01" to gtin.padStart(14, '0')

    // after the GTIN the path goes in identifier / value pairs
    var i = start + 2
    while (i + 1 < segments.size) {
        val ai = DIGITAL_LINK_ALIASES[segments[i]] ?: segments[i]
        if (!isKnownAi(ai)) return null
        parsed += ai to segments[i + 1]
        i += 2
    }
    if (i != segments.size) return null

    for (param in query.split('&')) {
        val key = decodeUrlPart(param.substringBefore('=', ""), plusAsSpace = true)
        val value = decodeUrlPart(param.substringAfter('=', ""), plusAsSpace = true)
        val ai = DIGITAL_LINK_ALIASES[key] ?: key
        // other query parameters have nothing to do with the code
        if (isKnownAi(ai) && value.isNotEmpty()) parsed += ai to value
    }
    return parsed
}

/**
 * Decodes %XX escapes as UTF-8 bytes; consecutive escapes are decoded together, since one
 * character may take several bytes. In a query a plus is a space.
 */
private fun decodeUrlPart(part: String, plusAsSpace: Boolean = false): String {
    if ('%' !in part && !(plusAsSpace && '+' in part)) return part
    val out = StringBuilder(part.length)
    val bytes = java.io.ByteArrayOutputStream()
    fun flush() {
        if (bytes.size() > 0) {
            out.append(bytes.toString(Charsets.UTF_8.name()))
            bytes.reset()
        }
    }
    var i = 0
    while (i < part.length) {
        val c = part[i]
        if (c == '%' && i + 2 < part.length &&
            part.substring(i + 1, i + 3).all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }
        ) {
            bytes.write(part.substring(i + 1, i + 3).toInt(16))
            i += 3
        } else {
            flush()
            out.append(if (plusAsSpace && c == '+') ' ' else c)
            i++
        }
    }
    flush()
    return out.toString()
}

/** Identifiers that name the item itself: SSCC, GTIN, GTIN of contained items. */
internal val GS1_KEYS = setOf("00", "01", "02")

/** GTIN and serial in the form printed on the box: 01…21… */
internal fun gtinSerial(gtin: String, serial: String?): String =
    if (serial != null) "01$gtin" + "21$serial" else "01$gtin"

/** The code's fields, whatever dictionary it uses; null when it does not parse. */
fun codeFields(raw: String): CodeFields? = describeCode(raw).fields

/** Fields from GS1 pairs. Expiry from (17), or from expiry with time (7003) without it. */
internal fun fieldsOfGs1(parsed: List<Pair<String, String>>): CodeFields? {
    fun value(ai: String) = parsed.firstOrNull { it.first == ai }?.second
    return CodeFields(
        product = value("01") ?: value("02"),
        serial = value("21"),
        batch = value("10"),
        expiry = value("17") ?: value("7003")?.take(6)
    ).takeIf { !it.isEmpty }
}

internal fun ppnFields(code: String): CodeFields? = parsePpn(code)

/**
 * The identifying pair of a PPN or Digital Link code written GS1-style, identifier and
 * value together; null for other codes.
 */
internal fun shortCodeOfOther(code: String): String? {
    // a cheap prefix check first: this runs for every list row, and most codes are neither
    if (isHttpUrl(code)) {
        val parsed = parseDigitalLink(code) ?: return null
        return gtinSerial(
            parsed.first { it.first == "01" }.second,
            parsed.firstOrNull { it.first == "21" }?.second
        )
    }
    // HIBC: the primary part as printed under the code plus serial or batch; without
    // them two items of one product would look the same in a list
    if (code.startsWith("+") || code.startsWith("*+")) {
        val hibc = parseHibc(code) ?: return null
        val primary = hibc.primary ?: return null
        return when {
            hibc.serial != null -> "$primary/S${hibc.serial}"
            hibc.lot != null -> "$primary/\$${hibc.lot}"
            else -> primary
        }
    }

    if (!code.startsWith("[)>") && !code.startsWith("9N")) return null
    val ppn = parsePpn(code) ?: return null
    return "9N${ppn.product}" + (ppn.serial?.let { "S$it" } ?: "")
}
