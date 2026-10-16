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
 * A three-column report: the code as printed on the box, for checking by eye; the full
 * content with the marking crypto tail, for loading into an accounting system; and when
 * it was scanned, empty for unscanned codes.
 *
 * Caveat: Excel turns an all-digit code into a number and drops its leading zero.
 * Marking code serials almost always contain letters, but a report meant for accounting
 * should not rely on that; xlsx would be the format for it.
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
 * A comma is quoted like a semicolon even though it is not our separator: marking codes
 * contain it, and unquoted the row could not be read back, by us or by Excel with a comma
 * separator.
 *
 * A cell starting with a formula sign gets a leading apostrophe. Quotes do not help:
 * Excel evaluates "=HYPERLINK(...)" quoted too, and a collecting session records any QR
 * the camera sees. Excel hides the apostrophe, and the list reader strips it again.
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
 * The cell needs a leading apostrophe: it starts with a formula sign or an apostrophe.
 * A leading apostrophe is guarded too, or "'+380..." would lose it on the way back,
 * taken for ours.
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

    // A timestamp in the name tells exports apart in a messenger, but then files pile up
    // in the cache, so old ones are removed; a day old, so a share still in progress is
    // not pulled away.
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
