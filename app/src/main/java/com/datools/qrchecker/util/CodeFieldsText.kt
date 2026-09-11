package com.datools.qrchecker.util

import android.content.Context
import com.datools.qrchecker.R
import java.util.Calendar

/**
 * Срок годности из кода в читаемый вид.
 *
 * Записан он как ггммдд. Век восстанавливается по правилу GS1: 00-49 - двадцать первый,
 * 50-99 - двадцатый. Нулевой день означает «до конца месяца», и тогда день не пишется,
 * иначе на этикетке появилось бы тридцатое февраля.
 */
fun formatExpiry(context: Context, raw: String): String? {
    if (raw.length != 6 || !raw.all { it.isDigit() }) return null

    val yy = raw.substring(0, 2).toInt()
    val month = raw.substring(2, 4).toInt()
    val day = raw.substring(4, 6).toInt()
    if (month !in 1..12 || day !in 0..31) return null

    val year = if (yy <= 49) 2000 + yy else 1900 + yy
    if (day == 0) return context.getString(R.string.expiry_month_only, month, year)

    val calendar = Calendar.getInstance().apply {
        isLenient = false
        clear()
        set(year, month - 1, day)
    }
    return runCatching { calendar.time }
        .map { context.getString(R.string.expiry_full, day, month, year) }
        .getOrNull()
}
