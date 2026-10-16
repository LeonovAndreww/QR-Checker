package com.datools.qrchecker.util

import com.datools.qrchecker.R
import com.datools.qrchecker.settings.AppSettings
import com.datools.qrchecker.settings.ClockChoice
import android.content.Context
import android.text.format.DateFormat as AndroidDateFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Scan time for exports: year-month-day, which sorts as text and reads the same in any
 * country. The locale is fixed for the same reason: the file leaves the phone.
 */
private val EXPORT_FORMAT = threadLocalFormat("yyyy-MM-dd HH:mm:ss")

fun formatScanTimeForExport(millis: Long?): String =
    if (millis == null) "" else EXPORT_FORMAT.get()!!.format(Date(millis))

/**
 * One formatter per thread: SimpleDateFormat keeps parse state inside, and exports built
 * in parallel off the main thread mixed up times of neighbouring rows or crashed.
 */
internal fun threadLocalFormat(pattern: String): ThreadLocal<SimpleDateFormat> =
    object : ThreadLocal<SimpleDateFormat>() {
        override fun initialValue() = SimpleDateFormat(pattern, Locale.US)
    }

/** 24-hour or 12-hour clock as set in the app, otherwise as on the phone. */
private fun is24Hour(context: Context): Boolean = when (AppSettings.clock(context)) {
    ClockChoice.H24 -> true
    ClockChoice.H12 -> false
    ClockChoice.SYSTEM -> AndroidDateFormat.is24HourFormat(context)
}

private fun timePattern(context: Context) = if (is24Hour(context)) "HH:mm" else "h:mm a"

/** Exact date and time, shown when a code is expanded. */
fun formatScanTimeForScreen(context: Context, millis: Long): String {
    val locale = Locale.getDefault()
    val pattern = AndroidDateFormat.getBestDateTimePattern(
        locale,
        if (is24Hour(context)) "d MMMM yyyy HH:mm:ss" else "d MMMM yyyy h:mm:ss a"
    )
    return SimpleDateFormat(pattern, locale).format(Date(millis))
}

/**
 * How long ago, in words.
 *
 * "12 seconds ago" answers the question asked at the conveyor: did I just scan this box,
 * or has it been here since yesterday. An exact time would need mental subtraction.
 * Past a day words run out: "yesterday at 17:55" is shorter and more precise than
 * "19 hours ago", and after a week the date is needed.
 */
fun formatTimeAgo(context: Context, millis: Long, now: Long = System.currentTimeMillis()): String {
    val res = context.resources
    val elapsed = now - millis

    // the phone clock may have been set back; "in a minute" is no answer
    if (elapsed < 0) return atTime(context, millis)

    val seconds = TimeUnit.MILLISECONDS.toSeconds(elapsed)
    if (seconds < 10) return res.getString(R.string.ago_just_now)
    if (seconds < 60) return res.getQuantityString(R.plurals.ago_seconds, seconds.toInt(), seconds.toInt())

    val minutes = TimeUnit.MILLISECONDS.toMinutes(elapsed)
    if (minutes < 60) return res.getQuantityString(R.plurals.ago_minutes, minutes.toInt(), minutes.toInt())

    val hours = TimeUnit.MILLISECONDS.toHours(elapsed)
    if (hours < 12) return res.getQuantityString(R.plurals.ago_hours, hours.toInt(), hours.toInt())

    // by the calendar from here on: at 00:30 "an hour ago" is yesterday
    return when (daysBetween(millis, now)) {
        0 -> res.getString(R.string.ago_today_at, atTime(context, millis))
        1 -> res.getString(R.string.ago_yesterday_at, atTime(context, millis))
        else -> res.getString(R.string.ago_on_date_at, onDate(millis), atTime(context, millis))
    }
}

private fun atTime(context: Context, millis: Long): String {
    val locale = Locale.getDefault()
    val pattern = AndroidDateFormat.getBestDateTimePattern(locale, timePattern(context))
    return SimpleDateFormat(pattern, locale).format(Date(millis))
}

private fun onDate(millis: Long): String {
    val locale = Locale.getDefault()
    val sameYear = yearOf(millis) == yearOf(System.currentTimeMillis())
    val pattern = AndroidDateFormat.getBestDateTimePattern(
        locale,
        if (sameYear) "d MMMM" else "d MMMM yyyy"
    )
    return SimpleDateFormat(pattern, locale).format(Date(millis))
}

private fun yearOf(millis: Long): Int =
    Calendar.getInstance().apply { timeInMillis = millis }.get(Calendar.YEAR)

/**
 * Calendar days between two moments in local time. The difference of midnights is
 * rounded, not truncated: a DST day has 23 or 25 hours, and truncation turned a spring
 * "yesterday" into "today" with a time in the future.
 */
internal fun daysBetween(from: Long, to: Long): Int {
    val day = TimeUnit.DAYS.toMillis(1)
    return Math.floorDiv(midnightOf(to) - midnightOf(from) + day / 2, day).toInt()
}

private fun midnightOf(millis: Long): Long = Calendar.getInstance().apply {
    timeInMillis = millis
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis
