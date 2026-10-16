package com.datools.qrchecker.util

import androidx.annotation.StringRes
import com.datools.qrchecker.R

/**
 * Что это за код - по его содержимому.
 *
 * Не по символу, которым он напечатан: в базе хранится только текст, и один и тот же
 * код маркировки приезжает и с камеры, и из PDF, и из выгрузки. Да и складу интересно не
 * «Data Matrix», а «Честный ЗНАК» или «лекарство с партией».
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

/** Вид кода и то, что в нём записано, - из одного разбора. */
data class CodeDescription(val kind: CodeKind?, val fields: CodeFields?)

private val NOTHING = CodeDescription(null, null)

/**
 * Разбирает код один раз и отвечает сразу на оба вопроса: что это и что в нём.
 *
 * Пока вид и поля определялись по отдельности, каждая строка списка разбиралась дважды,
 * а две цепочки проверок вдобавок расходились: поля у кода находились, а вида - нет.
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

    // Без номера товара или упаковки строка цифр - не код GS1, а, скорее всего, обычный
    // штрихкод: EAN, начинающийся на 10 или 17, иначе разбирался как «партия» или «срок».
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

/** null - ничего определённого сказать нельзя, и подписи не будет. */
fun codeKind(raw: String): CodeKind? = describeCode(raw).kind

private fun gs1Kind(code: String, pairs: List<Pair<String, String>>): CodeKind {
    // без разделителей криптохвост виден только по своей форме (91)xxxx(92)
    if (code.indexOf(GROUP_SEPARATOR) < 0 && GS1_WITHOUT_SEPARATORS.matches(code)) {
        return CodeKind.CHESTNY_ZNAK
    }
    val ais = pairs.mapTo(HashSet()) { it.first }
    // Примета Честного ЗНАКа - криптохвост: пара (91)+(92) или короткий (93) при
    // серийнике. Сами по себе 91-99 - внутренние поля любой компании и встречаются где
    // угодно. Партии в коде Честного ЗНАКа не бывает, а срок если и есть, то в (7003),
    // так что лекарство ЕС с внутренним (91) по (10) и (17) отличается сразу.
    val crypto = ("91" in ais && "92" in ais) || "93" in ais
    val chestny = crypto && "21" in ais && "10" !in ais && "17" !in ais
    return if (chestny) CodeKind.CHESTNY_ZNAK else CodeKind.GS1
}

/** Контрольная цифра EAN/UPC/GTIN: веса 3 и 1 справа налево, без последней цифры. */
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
