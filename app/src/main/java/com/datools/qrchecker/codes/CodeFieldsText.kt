package com.datools.qrchecker.codes

import android.content.Context
import com.datools.qrchecker.R
import java.util.Calendar

/**
 * Formats a YYMMDD expiry. The century follows the GS1 rule: 00-49 is the 21st, 50-99
 * the 20th. Day 00 means the end of the month, so no day is shown; otherwise labels
 * would read February 30.
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
