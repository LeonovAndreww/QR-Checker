package com.datools.qrchecker.util

import android.content.Context
import android.text.format.DateFormat as AndroidDateFormat
import com.datools.qrchecker.R
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Время отметки для выгрузки: год-месяц-день, потому что такой порядок сортируется как
 * текст и одинаково читается в любой стране. Локаль фиксирована по той же причине - файл
 * уходит из телефона и попадает к человеку с другими настройками.
 */
private val EXPORT_FORMAT = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

fun formatScanTimeForExport(millis: Long?): String =
    if (millis == null) "" else EXPORT_FORMAT.format(Date(millis))

/** Круглосуточные часы или с половинами дня - по настройке, иначе как в телефоне. */
private fun is24Hour(context: Context): Boolean = when (AppSettings.clock(context)) {
    ClockChoice.H24 -> true
    ClockChoice.H12 -> false
    ClockChoice.SYSTEM -> AndroidDateFormat.is24HourFormat(context)
}

private fun timePattern(context: Context) = if (is24Hour(context)) "HH:mm" else "h:mm a"

/** Точные дата и время: то, что показывают, когда код развёрнут. */
fun formatScanTimeForScreen(context: Context, millis: Long): String {
    val locale = Locale.getDefault()
    val pattern = AndroidDateFormat.getBestDateTimePattern(
        locale,
        if (is24Hour(context)) "d MMMM yyyy HH:mm:ss" else "d MMMM yyyy h:mm:ss a"
    )
    return SimpleDateFormat(pattern, locale).format(Date(millis))
}

/**
 * Насколько давно это было, словами.
 *
 * «Двенадцать секунд назад» отвечает на вопрос, который человек задаёт у ленты: этот
 * короб я только что просканировал или он лежит здесь со вчерашнего дня. Точное время
 * на этот вопрос не отвечает - его ещё надо вычесть из текущего в уме.
 *
 * Дальше суток слова кончаются: «вчера в 17:55» уже точнее и короче, чем «19 часов
 * назад», а через неделю нужна и дата.
 */
fun formatTimeAgo(context: Context, millis: Long, now: Long = System.currentTimeMillis()): String {
    val res = context.resources
    val elapsed = now - millis

    // часы телефона могли перевести назад; «через минуту» - не ответ
    if (elapsed < 0) return atTime(context, millis)

    val seconds = TimeUnit.MILLISECONDS.toSeconds(elapsed)
    if (seconds < 10) return res.getString(R.string.ago_just_now)
    if (seconds < 60) return res.getQuantityString(R.plurals.ago_seconds, seconds.toInt(), seconds.toInt())

    val minutes = TimeUnit.MILLISECONDS.toMinutes(elapsed)
    if (minutes < 60) return res.getQuantityString(R.plurals.ago_minutes, minutes.toInt(), minutes.toInt())

    val hours = TimeUnit.MILLISECONDS.toHours(elapsed)
    if (hours < 12) return res.getQuantityString(R.plurals.ago_hours, hours.toInt(), hours.toInt())

    // дальше считаем не разницей, а календарём: в 00:30 «час назад» - это вчера, и
    // сказать «сегодня» было бы неправдой
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

/** Сколько календарных суток между двумя моментами - по местному времени. */
private fun daysBetween(from: Long, to: Long): Int {
    val start = midnightOf(from)
    val end = midnightOf(to)
    return TimeUnit.MILLISECONDS.toDays(end - start).toInt()
}

private fun midnightOf(millis: Long): Long = Calendar.getInstance().apply {
    timeInMillis = millis
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis
