package com.datools.qrchecker.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
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

private val FILE_STAMP = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.US)

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

private fun escapeCsv(value: String): String =
    if (value.any { it == SEPARATOR || it == '"' || it == '\n' || it == '\r' }) {
        "\"" + value.replace("\"", "\"\"") + "\""
    } else {
        value
    }

/**
 * Writes the CSV into the shared cache directory and returns an intent that offers it to
 * any app that can take a file — mail, a messenger, cloud storage.
 */
fun shareCsv(context: Context, baseName: String, content: String): Intent {
    val exportDir = File(context.cacheDir, "export").apply { mkdirs() }
    // one file per session and list, so repeated exports do not pile up in the cache
    val file = File(exportDir, "${sanitizeFileName(baseName)}_${FILE_STAMP.format(Date())}.csv")
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

private fun sanitizeFileName(name: String): String =
    name.replace(Regex("[^\\p{L}\\p{N}._-]+"), "_").trim('_').ifEmpty { "session" }
