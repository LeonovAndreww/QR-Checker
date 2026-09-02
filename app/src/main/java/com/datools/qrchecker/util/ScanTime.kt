package com.datools.qrchecker.util

import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Время отметки для выгрузки: год-месяц-день, потому что такой порядок сортируется как
 * текст и одинаково читается в любой стране. Локаль фиксирована по той же причине - файл
 * уходит из телефона и попадает к человеку с другими настройками.
 */
private val EXPORT_FORMAT = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

fun formatScanTimeForExport(millis: Long?): String =
    if (millis == null) "" else EXPORT_FORMAT.format(Date(millis))

/**
 * Время отметки на экране - в том виде, в каком его показывает сам телефон.
 *
 * Формат берётся из настроек устройства, а не задаётся здесь: 24 часа или 12, точки или
 * дроби - это решение пользователя, а не приложения.
 */
fun formatScanTimeForScreen(millis: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(millis))
