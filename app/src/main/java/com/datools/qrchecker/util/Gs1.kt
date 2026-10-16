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

/**
 * Короткие имена, которыми ранние версии GS1 Digital Link называли идентификаторы в
 * адресе вместо цифр: /gtin/.../lot/.../ser/...
 */
private val DIGITAL_LINK_ALIASES = mapOf(
    "gtin" to "01", "cpv" to "22", "lot" to "10", "ser" to "21",
    "exp" to "17", "bestBefore" to "15", "prodDate" to "11"
)

/** Длины GTIN, которые допускает Digital Link: короче 14 знаков - добивается нулями. */
private val GTIN_LENGTHS = setOf(8, 12, 13, 14)

/** Идентификатор GS1 из тех, что разбор знает. */
private fun isKnownAi(ai: String): Boolean =
    ai in FIXED || ai in VARIABLE || fixedLengthOfFourDigitAi(ai) != null

/** Ссылка http или https. */
internal fun isHttpUrl(code: String): Boolean =
    code.startsWith("https://", ignoreCase = true) || code.startsWith("http://", ignoreCase = true)

/**
 * GS1 Digital Link: те же идентификаторы GS1, записанные адресом.
 *
 * https://example.com/01/09506000134376/10/ABC/21/123456?17=221231 - путь несёт номер
 * товара и уточнения к нему (партию, серийник), а параметры запроса - прочие атрибуты,
 * срок годности в том числе. Такой адрес кладут в QR на розничную упаковку, чтобы один
 * код читался и кассой, и телефоном покупателя, поэтому встречается он всё чаще.
 *
 * Возвращает пары «идентификатор - значение», как parseGs1, либо null, если это не
 * Digital Link. Проверка строгая: номер товара обязан сойтись по контрольной цифре, а
 * всё после него в пути - быть известными идентификаторами. Иначе обычная ссылка с
 * «/01/» в адресе показывалась бы выдуманным номером товара вместо себя самой.
 */
fun parseDigitalLink(code: String): List<Pair<String, String>>? {
    if (!isHttpUrl(code)) return null

    val afterScheme = code.substringAfter("://")
    val pathAndQuery = afterScheme.substringAfter('/', "")
    val path = pathAndQuery.substringBefore('?').substringBefore('#')
    val query = pathAndQuery.substringAfter('?', "").substringBefore('#')

    val segments = path.split('/').filter { it.isNotEmpty() }.map { decodeUrlPart(it) }
    val start = segments.indexOfFirst { (DIGITAL_LINK_ALIASES[it] ?: it) == "01" }
    if (start < 0 || start + 1 >= segments.size) return null

    val gtin = segments[start + 1]
    if (gtin.length !in GTIN_LENGTHS || !gtin.all { it.isDigit() } || !hasValidCheckDigit(gtin)) {
        return null
    }

    val parsed = ArrayList<Pair<String, String>>(4)
    parsed += "01" to gtin.padStart(14, '0')

    // после номера товара путь идёт парами «идентификатор / значение»
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
        // прочие параметры адреса к коду отношения не имеют и пропускаются
        if (isKnownAi(ai) && value.isNotEmpty()) parsed += ai to value
    }
    return parsed
}

/**
 * Снимает %XX - это байты UTF-8. Подряд идущие экранирования собираются вместе: один
 * символ бывает закодирован несколькими байтами. В параметрах запроса плюс - это пробел.
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

/** Идентификаторы, которыми код GS1 называет предмет: SSCC, GTIN, GTIN содержимого. */
private val GS1_KEYS = setOf("00", "01", "02")

/** Номер товара и серийник в той записи, что напечатана на коробке: 01…21… */
internal fun gtinSerial(gtin: String, serial: String?): String =
    if (serial != null) "01$gtin" + "21$serial" else "01$gtin"

/** Пары «идентификатор - значение» из кода GS1 любого вида записи. */
private fun gs1Pairs(code: String): List<Pair<String, String>>? =
    parseGs1(code) ?: parseDigitalLink(code)

/** Немецкий аптечный код IFA PPN. */
internal fun isPpn(code: String): Boolean = parsePpn(code) != null

/** Поля кода, каким бы словарём он ни был записан. null - разобрать не удалось. */
fun codeFields(raw: String): CodeFields? {
    val code = normalizeCode(raw)

    // Без номера товара или упаковки строка цифр - не код GS1, а, скорее всего, обычный
    // штрихкод: EAN, начинающийся на 10 или 17, иначе разбирался как «партия» или «срок».
    gs1Pairs(code)?.takeIf { parsed -> parsed.any { it.first in GS1_KEYS } }?.let { parsed ->
        return CodeFields(
            product = (parsed.firstOrNull { it.first == "01" } ?: parsed.firstOrNull { it.first == "02" })
                ?.second,
            serial = parsed.firstOrNull { it.first == "21" }?.second,
            batch = parsed.firstOrNull { it.first == "10" }?.second,
            expiry = parsed.firstOrNull { it.first == "17" }?.second
        ).takeIf { !it.isEmpty }
    }

    return parsePpn(code)
}

/**
 * Опознающая пара для немецкого PPN и для Digital Link, в той же записи, что у GS1:
 * идентификатор и значение подряд. null - код не из них.
 */
internal fun shortCodeOfOther(code: String): String? {
    // проверка по началу строки дешёвая, а сам разбор - нет: зовётся он на каждую
    // строку списка, и большинство кодов ни тем ни другим не являются
    if (isHttpUrl(code)) {
        val parsed = parseDigitalLink(code) ?: return null
        return gtinSerial(
            parsed.first { it.first == "01" }.second,
            parsed.firstOrNull { it.first == "21" }?.second
        )
    }
    if (!code.startsWith("[)>") && !code.startsWith("9N")) return null
    val ppn = parsePpn(code) ?: return null
    return "9N${ppn.product}" + (ppn.serial?.let { "S$it" } ?: "")
}
