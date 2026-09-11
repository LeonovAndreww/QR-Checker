package com.datools.qrchecker.util

/**
 * Разбор содержимого кода на составляющие.
 *
 * Национальные системы прослеживаемости - российский «Честный ЗНАК», европейская
 * маркировка лекарств, турецкая İTS, бразильская SNCM - пишут в Data Matrix одно и то
 * же: цепочку «идентификатор применения + значение» по правилам GS1. Отличается только
 * набор идентификаторов и их порядок, поэтому разбирать надо по стандарту, а не по
 * приметам конкретной страны.
 *
 * Особняком стоит немецкая аптечная маркировка IFA PPN: она пользуется другим словарём
 * идентификаторов и своим конвертом, поэтому разбирается отдельно.
 *
 * У части идентификаторов длина значения задана стандартом, остальные тянутся до
 * разделителя или до конца строки. Неизвестный идентификатор обрывает разбор: лучше
 * вернуть «не разобралось» и показать код целиком, чем отрезать не в том месте.
 */

/** Длина значения у идентификаторов GS1 с предопределённой длиной. */
private val FIXED: Map<String, Int> = buildMap {
    put("00", 18)
    put("01", 14)
    put("02", 14)
    put("20", 2)
    // даты и им подобные: ггммдд
    for (ai in listOf("11", "12", "13", "15", "16", "17")) put(ai, 6)
}

/** Идентификаторы GS1 переменной длины, которые встречаются в кодах прослеживаемости. */
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
        val fourFixed = four
            ?.takeIf { it.all { c -> c.isDigit() } }
            ?.let { fixedLengthOfFourDigitAi(it) }

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

/** Разделитель записей ISO/IEC 15434, с него начинается конверт PPN. */
private const val RECORD_SEPARATOR = '\u001E'

/** Конец передачи, им конверт закрывается. */
private const val END_OF_TRANSMISSION = '\u0004'

/**
 * Идентификаторы данных ASC MH10, которыми пользуется IFA PPN.
 *
 * Порядок важен: проверяются по очереди на совпадение начала, и короткий «D» перехватил
 * бы поле «16D», если бы стоял раньше него.
 */
private val PPN_IDS = listOf("16D", "9N", "1T", "8P", "D", "S")

/**
 * То, что записано в коде помимо самого номера.
 *
 * Собирается одинаково и из GS1, и из немецкого PPN: складу нужны одни и те же факты, а
 * каким словарём они записаны - его дело десятое.
 */
data class CodeFields(
    val product: String? = null,
    val serial: String? = null,
    val batch: String? = null,
    /** Срок годности в том виде, в каком записан: ггммдд, где дд может быть нулевым. */
    val expiry: String? = null
) {
    val isEmpty: Boolean
        get() = product == null && serial == null && batch == null && expiry == null
}

/**
 * Немецкая аптечная маркировка IFA PPN.
 *
 * Единственная из встреченных, которая не пользуется словарём GS1: поля помечены
 * идентификаторами ASC MH10 (ISO/IEC 15418) и завёрнуты в конверт ISO/IEC 15434 формата
 * 06. Разбор намеренно строгий - что не сошлось, то не PPN, и код показывается целиком.
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
            // GTIN и дата изготовления в коде встречаются, но на этикетке не нужны
            else -> fields
        }
    }
    return fields.takeIf { it.product != null }
}

/** Поля кода, каким бы словарём он ни был записан. null - разобрать не удалось. */
fun codeFields(raw: String): CodeFields? {
    val code = normalizeCode(raw)

    parseGs1(code)?.let { parsed ->
        return CodeFields(
            product = parsed.firstOrNull { it.first == "01" }?.second,
            serial = parsed.firstOrNull { it.first == "21" }?.second,
            batch = parsed.firstOrNull { it.first == "10" }?.second,
            expiry = parsed.firstOrNull { it.first == "17" }?.second
        ).takeIf { !it.isEmpty }
    }

    return parsePpn(code)
}
