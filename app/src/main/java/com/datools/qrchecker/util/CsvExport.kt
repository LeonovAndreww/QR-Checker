package com.datools.qrchecker.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Excel decides the separator from the locale, and a Russian Excel expects a semicolon.
 * The explicit "sep=" line makes the file open correctly regardless of that setting.
 */
private const val SEPARATOR = ';'

/** Without a BOM, Excel reads UTF-8 as ANSI and Cyrillic session names arrive as mojibake. */
private const val BOM = "\uFEFF"

private val FILE_STAMP = threadLocalFormat("yyyy-MM-dd_HH-mm")

private const val EXPORT_LIFETIME_MS = 24L * 60 * 60 * 1000

/**
 * Отчёт из трёх колонок.
 *
 * Первая - код в том виде, в каком он напечатан на коробке: по ней сверяют глазами.
 * Вторая - полное содержимое кода вместе с криптохвостом маркировки: она нужна, чтобы
 * строку можно было загрузить в учётную систему, а не только прочитать. Третья - когда
 * код отметили; у неотсканированных и у отмеченных до появления этого столбца она пуста.
 *
 * Оговорка про Excel: код, состоящий из одних цифр, Excel при открытии превратит в
 * число и съест ведущий ноль. У кодов маркировки в серийнике почти всегда есть буквы,
 * поэтому на практике это не срабатывает, но полагаться на это в отчёте для учёта
 * нельзя - для такой выгрузки нужен xlsx, а не csv.
 */
fun buildCsv(
    title: String,
    columnOnBox: String,
    columnFull: String,
    columnScannedAt: String,
    codes: List<String>,
    scanTimes: Map<String, Long>? = null
): String = buildString {
    append(BOM)
    append("sep=").append(SEPARATOR).append("\r\n")
    append(escapeCsv(title)).append("\r\n")
    append(escapeCsv(columnOnBox)).append(SEPARATOR)
    append(escapeCsv(columnFull)).append(SEPARATOR)
    append(escapeCsv(columnScannedAt)).append("\r\n")
    for (code in codes) {
        append(escapeCsv(shortCode(code)))
        append(SEPARATOR)
        append(escapeCsv(code))
        append(SEPARATOR)
        append(escapeCsv(formatScanTimeForExport(scanTimes?.get(code))))
        append("\r\n")
    }
}

/**
 * Запятая экранируется наравне с точкой с запятой, хотя разделителем у нас не является:
 * коды маркировки её содержат, и без кавычек такую строку нельзя разобрать обратно - ни
 * нами, ни Excel с запятой в качестве разделителя.
 *
 * Ячейка, которая начинается со знака формулы, получает впереди апостроф. Кавычки от
 * формулы не спасают: Excel вычисляет "=HYPERLINK(...)" и в кавычках, а собирающая
 * сессия записывает любой QR, какой попал в камеру. Апостроф Excel не показывает, а
 * разбор списка снимает его обратно.
 */
private fun escapeCsv(value: String): String {
    val safe = if (needsFormulaGuard(value)) "'$value" else value
    return if (safe.any { it == SEPARATOR || it == ',' || it == '"' || it == '\n' || it == '\r' }) {
        "\"" + safe.replace("\"", "\"\"") + "\""
    } else {
        safe
    }
}

/**
 * Ячейке нужен апостроф впереди: она начинается со знака формулы или с апострофа.
 *
 * Апостроф прикрывается наравне с формулой. Иначе код «'+380...» выгружался как есть, а
 * при обратном чтении терял свой апостроф, приняв его за наш.
 */
internal fun needsFormulaGuard(value: String): Boolean =
    value.firstOrNull()?.let { it in "=+-@\t\r'" } == true

/**
 * Writes the CSV into the shared cache directory and returns an intent that offers it to
 * any app that can take a file — mail, a messenger, cloud storage.
 */
suspend fun shareCsv(context: Context, baseName: String, content: String): Intent =
    withContext(Dispatchers.IO) { writeCsvForSharing(context, baseName, content) }

private fun writeCsvForSharing(context: Context, baseName: String, content: String): Intent {
    val exportDir = File(context.cacheDir, "export").apply { mkdirs() }

    // Время в имени нужно: в мессенджере видно, какая это выгрузка, а не один файл,
    // молча перезаписывающий предыдущий. Но тогда файлы копятся в кеше, поэтому
    // вчерашние удаляются. Сутки - чтобы не выдернуть файл из-под ещё не законченной
    // отправки.
    val stale = System.currentTimeMillis() - EXPORT_LIFETIME_MS
    exportDir.listFiles()?.forEach { if (it.lastModified() < stale) it.delete() }

    val file = File(exportDir, "${sessionFileName(baseName)}_${FILE_STAMP.get()!!.format(Date())}.csv")
    file.writeText(content)

    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    return Intent.createChooser(
        Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, file.name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        },
        null
    )
}
