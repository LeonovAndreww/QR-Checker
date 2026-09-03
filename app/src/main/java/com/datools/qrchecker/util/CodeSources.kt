package com.datools.qrchecker.util

/**
 * Достаёт коды из текстового списка.
 *
 * Личный кабинет Честного ЗНАКа отдаёт коды выгрузкой CSV, и это основной вид, в котором
 * они попадают к людям: PDF - лишь один из вариантов, для печати этикеток. Поэтому список
 * читается наравне с документом.
 *
 * Главная сложность в том, что разделители таблицы встречаются внутри самих кодов:
 * серийник состоит из произвольных знаков, и запятая с точкой с запятой попадаются в нём
 * постоянно. Резать строку по ним вслепую - значит молча обрубать коды, поэтому строка
 * разбирается по трём правилам подряд, от самого надёжного к самому шаткому.
 */
private const val SHORTEST_CODE = 8

/**
 * Разделителями считаются только точка с запятой и табуляция. Запятая - нет: в кодах
 * маркировки она встречается, а в выгрузках Честного ЗНАКа колонки разделяет точка с
 * запятой, как принято в русской локали Excel.
 */
private val SEPARATORS = charArrayOf(';', '\t')

/** (01)GTIN(21)серийник - форма кода маркировки, по ней строка узнаётся целиком. */
private val MARKING_CODE = Regex("^01\\d{14}21.+", RegexOption.DOT_MATCHES_ALL)

fun parseCodeList(text: String): List<String> {
    val codes = LinkedHashSet<String>()

    for (rawLine in text.lineSequence()) {
        val line = rawLine.trim().removePrefix("\uFEFF")
        if (line.isEmpty()) continue
        // строка "sep=;", которой начинаются наши же отчёты, кодом не является
        if (line.startsWith("sep=", ignoreCase = true)) continue

        val code = codeFromLine(line) ?: continue
        codes += code
    }

    return codes.toList()
}

private fun codeFromLine(line: String): String? {
    // 1. Кавычек в строке нет, а сама она имеет форму кода маркировки - это голый код.
    //    Точка с запятой внутри него принадлежит серийнику: строка таблицы, в которой
    //    поле содержит разделитель, обязана это поле закавычить, иначе её не разберёт
    //    никто.
    val bare = normalizeCode(line)
    if ('"' !in line && MARKING_CODE.matches(bare)) return bare.asCode()

    val fields = splitCsvRow(line)

    // 2. Поле одно - значит колонок нет, и строка целиком и есть значение.
    if (fields.size == 1) return normalizeCode(unwrapWholeLineQuotes(line)).asCode()

    // 3. Иначе это таблица. Из полей берётся то, что похоже на код маркировки, а если
    //    таких нет - самое длинное: заголовки вроде "код" или "gtin" короче любого кода.
    val values = fields.mapNotNull { normalizeCode(it).asCode() }
    return values.filter { MARKING_CODE.matches(it) }.maxByOrNull { it.length }
        ?: values.maxByOrNull { it.length }
}

private fun String.asCode(): String? =
    takeIf { it.length >= SHORTEST_CODE && !it.contains(' ') }

/** Снимает кавычки, если в них взята вся строка и внутри других кавычек нет. */
private fun unwrapWholeLineQuotes(line: String): String =
    if (line.length >= 2 && line.startsWith('"') && line.endsWith('"') &&
        line.count { it == '"' } == 2
    ) {
        line.substring(1, line.length - 1)
    } else {
        line
    }

/** Режет строку таблицы на поля, не трогая разделители внутри кавычек. */
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
