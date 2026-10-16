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
    EAN13(R.string.kind_ean13),
    EAN8(R.string.kind_ean8),
    UPCA(R.string.kind_upca),
    LINK(R.string.kind_link)
}

/** null - ничего определённого сказать нельзя, и подписи не будет. */
fun codeKind(raw: String): CodeKind? {
    val code = normalizeCode(raw)
    if (code.isEmpty()) return null

    if (parseDigitalLink(code) != null) return CodeKind.GS1_DIGITAL_LINK
    if (isPpn(code)) return CodeKind.IFA_PPN

    // без разделителей криптохвост виден только по своей форме (91)xxxx(92)
    if (code.indexOf(GROUP_SEPARATOR) < 0 && GS1_WITHOUT_SEPARATORS.matches(code)) {
        return CodeKind.CHESTNY_ZNAK
    }

    parseGs1(code)?.takeIf { pairs -> pairs.any { it.first == "01" } }?.let { pairs ->
        val ais = pairs.mapTo(HashSet()) { it.first }
        // Примета Честного ЗНАКа - криптохвост: пара (91)+(92) или короткий (93) при
        // серийнике. Сами по себе 91-99 - внутренние поля любой компании и встречаются
        // где угодно. А срока годности и партии в коде Честного ЗНАКа не бывает, и
        // лекарство ЕС с внутренним (91) по ним отличается сразу.
        val crypto = ("91" in ais && "92" in ais) || "93" in ais
        val chestny = crypto && "21" in ais && "10" !in ais && "17" !in ais
        return if (chestny) CodeKind.CHESTNY_ZNAK else CodeKind.GS1
    }
    if (code.all { it.isDigit() } && hasValidCheckDigit(code)) {
        when (code.length) {
            13 -> return CodeKind.EAN13
            8 -> return CodeKind.EAN8
            12 -> return CodeKind.UPCA
        }
    }

    if (isHttpUrl(code)) return CodeKind.LINK

    return null
}

/** Контрольная цифра EAN/UPC: веса 3 и 1 справа налево, без последней цифры. */
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
