package com.datools.qrchecker.util

import java.util.Calendar

/**
 * HIBC LIC - маркировка медицинских изделий по ANSI/HIBC 2.6.
 *
 * Второй после GS1 источник UDI для медизделий в США и ЕС: хирургический инструмент,
 * импланты, больничные расходники. Своя запись: «+», код производителя (LIC) из четырёх
 * знаков, номер изделия, единица упаковки, и в конце контрольный знак по модулю 43.
 * Партия, серийник и срок годности идут во вторичных данных - отдельным кодом или через
 * «/» в том же.
 */
data class Hibc(
    /** Основная часть без контрольного знака: «+», LIC, номер изделия, единица упаковки. */
    val primary: String?,
    /** LIC и номер изделия подряд - то, что называет изделие. */
    val product: String?,
    val lot: String?,
    val serial: String?,
    /** Срок годности, приведённый к ггммдд; нулевой день - «до конца месяца». */
    val expiry: String?
)

/** Значения знаков для контрольного знака, Table B1 стандарта. */
private const val HIBC_CHARSET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ-. $/+%"

/** Значение пробела: им бывает и сам контрольный знак. */
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
 * Разбирает код HIBC LIC или возвращает null.
 *
 * Строго: контрольный знак обязан сойтись. С «+» начинается много чего - телефонный
 * номер, ссылка, формула, - и без проверки любая такая строка выдумывала бы изделие.
 */
fun parseHibc(raw: String): Hibc? {
    val code = raw.trim('*')
    if (code.length < 3 || code[0] != '+') return null

    // Контрольный знак бывает пробелом, а пробел по краям кода срезается ещё при
    // нормализации. Поэтому, если последний знак не сошёлся, проверяется вариант, где
    // пробел был и его отрезали.
    val data = when {
        mod43(code.dropLast(1)) == HIBC_CHARSET.indexOf(code.last()) -> code.dropLast(1)
        mod43(code) == SPACE_VALUE -> code
        else -> return null
    }

    return if (data[1].isLetter()) parsePrimaryAndRest(data) else parseSecondaryAlone(data)
}

/** «+LIC PCN U» и, если код совмещённый, «/вторичные данные». */
private fun parsePrimaryAndRest(data: String): Hibc? {
    val primary = data.substringBefore('/')
    // + (1), LIC (4), номер изделия (1-18), единица упаковки (1)
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

/** Вторичный код отдельным символом: «+», данные и знак связи с основным в конце. */
private fun parseSecondaryAlone(data: String): Hibc? {
    if (data.length < 3) return null
    val body = data.substring(1, data.length - 1)
    val secondary = parseSecondary(body) ?: return null
    return Hibc(null, null, secondary.lot, secondary.serial, secondary.expiry)
}

private data class Secondary(
    val lot: String? = null,
    val serial: String? = null,
    val expiry: String? = null
)

/**
 * Вторичные данные (Table 2 и Appendix F) и дополнительные поля после «/» (раздел 2.3).
 */
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
            // дата изготовления и количество складу на экране не нужны
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
    // устаревшая запись: пять цифр юлианской даты, за ними может идти партия
    core.firstOrNull()?.isDigit() == true -> {
        val date = julianToYymmdd(core.take(5)) ?: return null
        Secondary(lot = core.drop(5).ifEmpty { null }, expiry = date)
    }
    else -> null
}

/**
 * После «$$» или «$$+» первая цифра говорит, в каком виде записан срок (Appendix E1.1):
 * 0 и 1 - это уже первая цифра месяца в ММГГ, 2 - ММДДГГ, 3 - ГГММДД, 4 - ГГММДДЧЧ,
 * 5 - ГГДДД (день года), 6 - ГГДДДЧЧ, 7 - срока нет.
 */
private inline fun withDate(text: String, make: (String?, String?) -> Secondary): Secondary? {
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
        // час в конце даты: только проверить, что он есть и это цифры
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

/** ГГДДД - год и номер дня в году - в ггммдд. */
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
    // цифры латинские при любом языке телефона: строка дальше разбирается как число
    return String.format(
        java.util.Locale.US,
        "%02d%02d%02d",
        yy,
        calendar.get(Calendar.MONTH) + 1,
        calendar.get(Calendar.DAY_OF_MONTH)
    )
}
