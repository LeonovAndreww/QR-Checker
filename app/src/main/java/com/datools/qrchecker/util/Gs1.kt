package com.datools.qrchecker.util

/**
 * Разбор строки GS1 на составляющие.
 *
 * Национальные системы прослеживаемости - российский «Честный ЗНАК», европейская
 * маркировка лекарств, турецкая İTS, бразильская SNCM - пишут в Data Matrix одно и то
 * же: цепочку «идентификатор применения + значение» по правилам GS1. Отличается только
 * набор идентификаторов и их порядок, поэтому разбирать надо по стандарту, а не по
 * приметам конкретной страны.
 *
 * У части идентификаторов длина значения задана стандартом, остальные тянутся до
 * разделителя или до конца строки. Неизвестный идентификатор обрывает разбор: лучше
 * вернуть «не разобралось» и показать код целиком, чем отрезать не в том месте.
 */

/** Длина значения у идентификаторов с предопределённой длиной. */
private val FIXED: Map<String, Int> = buildMap {
    put("00", 18)
    put("01", 14)
    put("02", 14)
    put("20", 2)
    // даты и им подобные: ггммдд
    for (ai in listOf("11", "12", "13", "15", "16", "17")) put(ai, 6)
}

/** Идентификаторы переменной длины, которые встречаются в кодах прослеживаемости. */
private val VARIABLE = setOf(
    "10", // партия
    "21", // серийный номер
    "22", "30", "37", "240", "241", "242", "243",
    "710", "711", "712", "713", "714", // национальные номера лекарств
    "91", "92", "93", "94", "95", "96", "97", "98", "99" // внутренние данные компании
)

/** Четырёхзначные идентификаторы веса, объёма и размеров: значение всегда шесть цифр. */
private fun fixedLengthOfFourDigitAi(ai: String): Int? =
    if (ai.length == 4 && ai.take(2) in setOf("31", "32", "33", "34", "35", "36")) 6 else null

/**
 * Возвращает пары «идентификатор - значение» в том порядке, в каком они записаны, либо
 * null, если строка не разбирается как GS1.
 */
fun parseGs1(code: String): List<Pair<String, String>>? {
    if (code.isEmpty()) return null

    val parsed = ArrayList<Pair<String, String>>(4)
    var pos = 0

    while (pos < code.length) {
        // разделитель между полями переменной длины
        if (code[pos] == GROUP_SEPARATOR) {
            pos++
            continue
        }
        if (pos + 2 > code.length) return null

        val two = code.substring(pos, pos + 2)
        if (!two.all { it.isDigit() }) return null

        val four = if (pos + 4 <= code.length) code.substring(pos, pos + 4) else null
        val fourFixed = four?.takeIf { it.all { c -> c.isDigit() } }?.let { fixedLengthOfFourDigitAi(it) }

        val ai: String
        val length: Int?
        when {
            fourFixed != null -> {
                ai = four!!
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
            // трёхзначные идентификаторы переменной длины
            pos + 3 <= code.length && code.substring(pos, pos + 3) in VARIABLE -> {
                ai = code.substring(pos, pos + 3)
                length = null
            }
            else -> return null
        }

        pos += ai.length
        val value = if (length != null) {
            if (pos + length > code.length) return null
            code.substring(pos, pos + length).also { pos += length }
        } else {
            val end = code.indexOf(GROUP_SEPARATOR, pos).let { if (it < 0) code.length else it }
            code.substring(pos, end).also { pos = end }
        }

        if (value.isEmpty()) return null
        parsed += ai to value
    }

    return parsed.ifEmpty { null }
}
