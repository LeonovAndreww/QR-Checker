package com.datools.qrchecker.util

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Вид файла определяется по первым байтам, а не по имени и не по типу от системы: своё
 * расширение .qrcheck система не знает и отдаёт файл как octet-stream, а документ из
 * мессенджера приезжает и вовсе без имени.
 */
private enum class FileKind { PDF, IMAGE, TEXT }

private const val TAG = "QRChecker"

private val PDF_MAGIC = "%PDF-".toByteArray()

private val IMAGE_MAGICS = listOf(
    byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47),               // PNG
    byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()),   // JPEG
    byteArrayOf(0x47, 0x49, 0x46, 0x38),                        // GIF
    byteArrayOf(0x42, 0x4D),                                    // BMP
    byteArrayOf(0x52, 0x49, 0x46, 0x46)                         // RIFF, он же WebP
)

/** Сколько кодов дал каждый выбранный файл - чтобы было видно, какой приехал пустым. */
data class SourceSummary(val name: String, val codes: Int)

/** Всё, что удалось вычитать из выбранных файлов. */
data class ParsedFiles(
    val codes: List<String>,
    val sources: List<SourceSummary>,
    val scanned: List<String>,
    val scanTimes: Map<String, Long>,
    /** Имя сессии - только когда выбран ровно один файл сессии и придумывать нечего. */
    val sessionName: String?,
    /** Чем записан каждый код: по этому его можно отсеять перед созданием сессии. */
    val formats: Map<String, CodeFormat> = emptyMap()
)

/**
 * Читает коды из выбранных файлов - документов, картинок и списков вперемешку.
 *
 * Живёт отдельно от экранов, потому что читают их двое: создание сессии и её правка. Пока
 * разбор был вписан в создание, правка умела только PDF, и одно и то же действие в двух
 * местах приложения работало по-разному.
 */
suspend fun readCodesFromFiles(
    context: Context,
    files: List<Pair<Uri, String>>,
    scale: Int = 3,
    onProgress: (fileIndex: Int, fileCount: Int, name: String, page: Int, pageCount: Int) -> Unit =
        { _, _, _, _, _ -> }
): ParsedFiles {
    val codes = LinkedHashSet<String>()
    val formats = HashMap<String, CodeFormat>()
    val sources = ArrayList<SourceSummary>(files.size)
    val scanned = LinkedHashSet<String>()
    val scanTimes = HashMap<String, Long>()
    var sessionName: String? = null

    files.forEachIndexed { index, (uri, name) ->
        onProgress(index, files.size, name, 0, 0)
        val before = codes.size

        // Файл, который не открылся или оказался битым, не должен уносить с собой
        // остальные выбранные: он просто попадёт в сводку с нулём кодов, и будет
        // видно, какой именно приехал пустым.
        try {
            when (kindOf(context, uri)) {
                FileKind.PDF -> {
                    val result = parsePdfForQRCodes(context, uri, scale) { done, total ->
                        onProgress(index, files.size, name, done, total)
                    }
                    for (found in result.codes) {
                        codes += found.value
                        formats.putIfAbsent(found.value, found.format)
                    }
                }

                FileKind.IMAGE -> for (found in parseImageForCodes(context, uri)) {
                    codes += found.value
                    formats.putIfAbsent(found.value, found.format)
                }

                FileKind.TEXT -> {
                    // файл сессии отдаёт и коды, и отметки: разобранный как обычный список,
                    // он терял бы их молча
                    val session = readSessionOrNull(context, uri)
                    if (session != null) {
                        codes += session.codes
                        session.codes.forEach { formats.putIfAbsent(it, CodeFormat.TEXT) }
                        scanned += session.scannedCodes
                        session.scanTimes?.let { scanTimes.putAll(it) }
                        if (files.size == 1) sessionName = session.name
                    } else {
                        val listed = withContext(Dispatchers.IO) {
                            parseCodeList(readTextFromUri(context, uri))
                        }
                        codes += listed
                        listed.forEach { formats.putIfAbsent(it, CodeFormat.TEXT) }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Could not read $name", e)
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "Out of memory reading $name", e)
        }

        sources += SourceSummary(name, codes.size - before)
    }

    return ParsedFiles(
        codes = codes.toList(),
        sources = sources,
        scanned = scanned.filter { it in codes },
        scanTimes = scanTimes.filterKeys { it in codes },
        sessionName = sessionName,
        formats = formats
    )
}

private suspend fun kindOf(context: Context, uri: Uri): FileKind = withContext(Dispatchers.IO) {
    val head = context.contentResolver.openInputStream(uri)?.use { input ->
        // Дочитывать надо в цикле: одно чтение вправе вернуть меньше запрошенного, и
        // у файла из облачного хранилища оно так и делает. Одного короткого чтения
        // хватало, чтобы PDF не опознался по сигнатуре и ушёл разбираться как текст.
        val buffer = ByteArray(8)
        var filled = 0
        while (filled < buffer.size) {
            val read = input.read(buffer, filled, buffer.size - filled)
            if (read <= 0) break
            filled += read
        }
        buffer.copyOf(filled)
    } ?: ByteArray(0)

    when {
        head.startsWith(PDF_MAGIC) -> FileKind.PDF
        IMAGE_MAGICS.any { head.startsWith(it) } -> FileKind.IMAGE
        else -> FileKind.TEXT
    }
}

/** Пробует прочитать файл как сессию. null - значит это что-то другое. */
private suspend fun readSessionOrNull(context: Context, uri: Uri) = withContext(Dispatchers.IO) {
    try {
        readSessionFile(readTextFromUri(context, uri)).session
    } catch (t: Throwable) {
        null
    }
}

private fun ByteArray.startsWith(prefix: ByteArray): Boolean =
    size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }
