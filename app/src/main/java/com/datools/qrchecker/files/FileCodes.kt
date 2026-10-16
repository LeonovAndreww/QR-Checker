package com.datools.qrchecker.files

import com.datools.qrchecker.codes.CodeFormat
import com.datools.qrchecker.codes.parseCodeList
import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The file kind is told by its first bytes, not by name or system type: the system does
 * not know .qrcheck and reports octet-stream, and a document from a messenger may have
 * no name at all.
 */
private enum class FileKind { PDF, IMAGE, TEXT }

private const val TAG = "QRChecker"

private val PDF_MAGIC = "%PDF-".toByteArray()

private val IMAGE_MAGICS = listOf(
    byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47),               // PNG
    byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()),   // JPEG
    byteArrayOf(0x47, 0x49, 0x46, 0x38),                        // GIF
    byteArrayOf(0x42, 0x4D),                                    // BMP
    byteArrayOf(0x52, 0x49, 0x46, 0x46)                         // RIFF, used by WebP
)

/** How many codes each picked file gave, so an empty one is visible. */
data class SourceSummary(val name: String, val codes: Int)

/** Everything read from the picked files. */
data class ParsedFiles(
    val codes: List<String>,
    val sources: List<SourceSummary>,
    val scanned: List<String>,
    val scanTimes: Map<String, Long>,
    /** The session name, only when exactly one session file was picked. */
    val sessionName: String?,
    /** The session file says the session is collecting. */
    val collecting: Boolean = false,
    /** How each code was printed, so a kind can be filtered out before saving. */
    val formats: Map<String, CodeFormat> = emptyMap()
)

/**
 * Reads codes from the picked files: documents, images and lists, mixed. Shared by the
 * create and edit screens, so both accept the same files.
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
    var collecting = false

    files.forEachIndexed { index, (uri, name) ->
        onProgress(index, files.size, name, 0, 0)
        val before = codes.size

        // A file that fails to open must not take the others with it: it shows up in the
        // summary with zero codes.
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
                    // a session file carries codes and marks; read as a plain list it
                    // would lose the marks silently
                    val session = readSessionOrNull(context, uri)
                    if (session != null) {
                        codes += session.codes
                        session.codes.forEach { formats.putIfAbsent(it, CodeFormat.TEXT) }
                        scanned += session.scannedCodes
                        session.scanTimes?.let { scanTimes.putAll(it) }
                        if (files.size == 1) {
                            sessionName = session.name
                            collecting = session.collecting
                        }
                    } else {
                        val listed = withContext(Dispatchers.IO) {
                            parseCodeList(readTextFromUri(context, uri))
                        }
                        codes += listed
                        listed.forEach { formats.putIfAbsent(it, CodeFormat.TEXT) }
                    }
                }
            }
        } catch (c: CancellationException) {
            // Cancellation is not a failed file: swallowed here, the loop would go on and
            // return a result for a pick the user already cancelled.
            throw c
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
        collecting = collecting,
        formats = formats
    )
}

private suspend fun kindOf(context: Context, uri: Uri): FileKind = withContext(Dispatchers.IO) {
    val head = context.contentResolver.openInputStream(uri)?.use { input ->
        // Read in a loop: one read may return fewer bytes, as files from cloud storage
        // do, and a short read made a PDF fail its signature and parse as text.
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

/** Tries to read the file as a session; null when it is something else. */
private suspend fun readSessionOrNull(context: Context, uri: Uri) = withContext(Dispatchers.IO) {
    try {
        readSessionFile(readTextFromUri(context, uri)).session
    } catch (t: Throwable) {
        null
    }
}

private fun ByteArray.startsWith(prefix: ByteArray): Boolean =
    size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }
