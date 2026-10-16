package com.datools.qrchecker.codes

import java.util.Calendar

/**
 * HIBC LIC, the medical device marking of ANSI/HIBC 2.6.
 *
 * The second UDI source for medical devices in the US and EU after GS1: surgical
 * instruments, implants, hospital supplies. "+", a four-character labeler code (LIC),
 * the product number, the unit of measure and a mod 43 check character. Batch, serial
 * and expiry are secondary data, in a separate symbol or after "/" in the same one.
 */
data class Hibc(
    /** The primary part without the check character: "+", LIC, product number, unit. */
    val primary: String?,
    /** LIC and product number together: what names the product. */
    val product: String?,
    val lot: String?,
    val serial: String?,
    /** Expiry as YYMMDD; day 00 means the end of the month. */
    val expiry: String?
)

/** Character values for the check character, Table B1 of the standard. */
private const val HIBC_CHARSET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ-. $/+%"

/** The value of a space, which can be the check character itself. */
private const val SPACE_VALUE = 38

private fun mod43(data: String): Int? {
    var sum = 0
    for (c in data) {
        val v = HIBC_CHARSET.indexOf(c)
        if (v < 0) return null
        sum += v
    }
    return sum % 43
}

/**
 * Parses an HIBC LIC code, or returns null.
 *
 * Strict: the check character must match. Plenty of things start with "+" (phone
 * numbers, links, formulas), and without the check any of them would invent a device.
 */
fun parseHibc(raw: String): Hibc? {
    val code = raw.trim('*')
    if (code.length < 3 || code[0] != '+') return null

    // The check character can be a space, and normalisation trims spaces at the ends,
    // so a failed check is retried with that space put back.
    val data = when {
        mod43(code.dropLast(1)) == HIBC_CHARSET.indexOf(code.last()) -> code.dropLast(1)
        mod43(code) == SPACE_VALUE -> code
        else -> return null
    }

    return if (data[1].isLetter()) parsePrimaryAndRest(data) else parseSecondaryAlone(data)
}

/** "+LIC PCN U", plus "/secondary data" in a concatenated code. */
private fun parsePrimaryAndRest(data: String): Hibc? {
    val primary = data.substringBefore('/')
    // + (1), LIC (4), product number (1-18), unit of measure (1)
    if (primary.length !in 7..24) return null
    val lic = primary.substring(1, 5)
    val pcn = primary.substring(5, primary.length - 1)
    val unit = primary.last()
    if (!lic[0].isLetter() || !lic.all { it.isLetterOrDigit() }) return null
    if (!pcn.all { it.isLetterOrDigit() } || !unit.isDigit()) return null

    val rest = data.substringAfter('/', "")
    val secondary = if (rest.isEmpty()) Secondary() else parseSecondary(rest) ?: return null
    return Hibc(
        primary = primary,
        product = lic + pcn,
        lot = secondary.lot,
        serial = secondary.serial,
        expiry = secondary.expiry
    )
}

/**
 * A secondary code as its own symbol: "+", the data and a link character to the primary.
 *
 * Only the "$" forms are accepted. The legacy form, "+" and a five-digit date, is exactly
 * what a phone number looks like: about one in fifty +7 9xx numbers passed the check and
 * showed up as a device expiring in 1979.
 */
private fun parseSecondaryAlone(data: String): Hibc? {
    if (data.length < 3 || data[1] != '$') return null
    val body = data.substring(1, data.length - 1)
    val secondary = parseSecondary(body) ?: return null
    return Hibc(null, null, secondary.lot, secondary.serial, secondary.expiry)
}

private data class Secondary(
    val lot: String? = null,
    val serial: String? = null,
    val expiry: String? = null
)

/** Secondary data (Table 2 and Appendix F) and supplementary fields after "/" (2.3). */
private fun parseSecondary(text: String): Secondary? {
    val parts = text.split('/')
    var result = parseSecondaryCore(parts[0]) ?: return null

    for (extra in parts.drop(1)) {
        result = when {
            extra.startsWith("14D") -> {
                val date = extra.substring(3)
                if (date.length != 8 || !date.all { it.isDigit() }) return null
                result.copy(expiry = date.substring(2))
            }
            // manufacture date and quantity are of no use on screen
            extra.startsWith("16D") || extra.startsWith("Q") -> result
            extra.startsWith("S") -> result.copy(serial = extra.substring(1).ifEmpty { null })
            else -> return null
        }
    }
    return result
}

private fun parseSecondaryCore(core: String): Secondary? = when {
    core.startsWith("$$+") -> withDate(core.substring(3)) { id, date -> Secondary(serial = id, expiry = date) }
    core.startsWith("$$") -> withDate(core.substring(2)) { id, date -> Secondary(lot = id, expiry = date) }
    core.startsWith("$+") -> Secondary(serial = core.substring(2).ifEmpty { null })
    core.startsWith("$") -> Secondary(lot = core.substring(1).ifEmpty { null })
    // legacy form: a five-digit Julian date, maybe followed by the batch
    core.firstOrNull()?.isDigit() == true -> {
        val date = julianToYymmdd(core.take(5)) ?: return null
        Secondary(lot = core.drop(5).ifEmpty { null }, expiry = date)
    }
    else -> null
}

/**
 * After "$$" or "$$+" the first digit tells the expiry format (Appendix E1.1): 0 and 1
 * are already the first digit of the month in MMYY, 2 is MMDDYY, 3 YYMMDD, 4 YYMMDDHH,
 * 5 YYJJJ (day of year), 6 YYJJJHH, 7 no expiry.
 */
private fun withDate(text: String, make: (String?, String?) -> Secondary): Secondary? {
    // Before 2.6 a quantity could precede the date format: 8 and two digits, or 9 and
    // five. 2.6 dropped it, but such labels are still around (Note 3).
    when (text.firstOrNull()) {
        '8' -> return text.drop(1).takeIf { it.take(2).allDigits(2) }
            ?.let { withDate(it.drop(2), make) }
        '9' -> return text.drop(1).takeIf { it.take(5).allDigits(5) }
            ?.let { withDate(it.drop(5), make) }
    }
    val format = text.firstOrNull() ?: return null
    val (date, idStart) = when (format) {
        '0', '1' -> mmyy(text.take(4)) to 4
        '2' -> mmddyy(text.substring(1).take(6)) to 7
        '3' -> yymmdd(text.substring(1).take(6)) to 7
        '4' -> yymmdd(text.substring(1).take(6)) to 9
        '5' -> julianToYymmdd(text.substring(1).take(5)) to 6
        '6' -> julianToYymmdd(text.substring(1).take(5)) to 8
        '7' -> "" to 1
        else -> return null
    }
    if (date == null || text.length < idStart) return null
    if (format == '4' || format == '6') {
        // an hour at the end of the date: only checked to be digits
        val hour = text.substring(idStart - 2, idStart)
        if (!hour.all { it.isDigit() }) return null
    }
    return make(text.substring(idStart).ifEmpty { null }, date.ifEmpty { null })
}

private fun String.allDigits(length: Int) = this.length == length && all { it.isDigit() }

private fun mmyy(s: String): String? =
    if (s.allDigits(4)) s.substring(2, 4) + s.substring(0, 2) + "00" else null

private fun mmddyy(s: String): String? =
    if (s.allDigits(6)) s.substring(4, 6) + s.substring(0, 4) else null

private fun yymmdd(s: String): String? = if (s.allDigits(6)) s else null

/** YYJJJ, a year and a day of the year, to YYMMDD. */
private fun julianToYymmdd(s: String): String? {
    if (!s.allDigits(5)) return null
    val yy = s.substring(0, 2).toInt()
    val day = s.substring(2, 5).toInt()
    val year = if (yy <= 49) 2000 + yy else 1900 + yy
    val calendar = Calendar.getInstance().apply {
        clear()
        set(Calendar.YEAR, year)
    }
    if (day !in 1..calendar.getActualMaximum(Calendar.DAY_OF_YEAR)) return null
    calendar.set(Calendar.DAY_OF_YEAR, day)
    // Latin digits whatever the locale: the result is parsed as a number later
    return String.format(
        java.util.Locale.US,
        "%02d%02d%02d",
        yy,
        calendar.get(Calendar.MONTH) + 1,
        calendar.get(Calendar.DAY_OF_MONTH)
    )
}
