package com.datools.qrchecker.util

import android.content.Context
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.util.Log
import androidx.core.graphics.createBitmap
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import kotlin.math.sqrt

private const val TAG = "QRChecker"

/**
 * Backstop against an out-of-memory crash on a very large page. A4 at scale 3 is ~4.5M
 * pixels and stays untouched: downscaling a page is a good way to lose a small code.
 */
private const val MAX_PAGE_PIXELS = 9_000_000L

/**
 * The scale argument alone is wrong for anything but a big page: a 35x61mm label at
 * scale 3 renders to 297x519 px, far below the 1280x720 the detector is built for, and
 * nothing is found on it. The long edge is brought up to this instead, which leaves an
 * A4 page at the scale it already used.
 */
private const val TARGET_LONG_EDGE_PX = 2400.0

/** A page that hangs the detector must not hang the whole import. */
private const val DECODE_TIMEOUT_SECONDS = 15L

/**
 * [pageCount] separates "the document was read but held no codes" from "nothing was read
 * at all", which is otherwise the same empty list to the user.
 */
data class PdfScanResult(
    val codes: List<ScannedCode>,
    val pageCount: Int,
    /** Size the pages were rendered at, so a fruitless import can be told what it looked at. */
    val renderedSize: String = ""
)

/**
 * Renders every page of the PDF and decodes the QR codes on it.
 *
 * Pages without a QR code are skipped silently - that is normal input. Anything that makes
 * the document unreadable (no access to the uri, a broken PDF, a detector that fails) is
 * thrown to the caller, so the UI can say what went wrong instead of claiming the file
 * holds no codes.
 */
suspend fun parsePdfForQRCodes(
    context: Context,
    uri: Uri,
    scale: Int = 3,
    /**
     * Сколько страниц пройдено из скольких. Сотня страниц разбирается заметно дольше
     * секунды, и без этого экран показывает крутилку, по которой не отличить работу от
     * зависания.
     */
    onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }
): PdfScanResult = withContext(Dispatchers.IO) {
    // по коду, а не по паре: один и тот же код, прочитанный дважды, остаётся одним
    val qrCodes = LinkedHashMap<String, ScannedCode>()
    var pageCount = 0
    var renderedSize = ""

    val tempFile = File.createTempFile("qrchecker_", ".pdf", context.cacheDir)
    val scanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            // читается всё, что умеет распознаватель: на складах и в фондах наклеен
            // обычный штрихкод, а не Data Matrix. Что из прочитанного взять в сессию,
            // решают уже на экране создания
            .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
            .build()
    )

    try {
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(tempFile).use { output -> input.copyTo(output) }
        } ?: throw IllegalStateException("Cannot open the selected file")

        ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                pageCount = renderer.pageCount

                onProgress(0, pageCount)

                for (pageIndex in 0 until pageCount) {
                    // parsing a big document takes a while - honour cancellation
                    ensureActive()
                    onProgress(pageIndex, pageCount)

                    val page = renderer.openPage(pageIndex)
                    val bitmap = try {
                        val longEdge = maxOf(page.width, page.height).toDouble()
                        val effectiveScale =
                            maxOf(scale.toDouble(), TARGET_LONG_EDGE_PX / longEdge)
                        var width = (page.width * effectiveScale).toInt()
                        var height = (page.height * effectiveScale).toInt()
                        val pixels = width.toLong() * height.toLong()
                        if (pixels > MAX_PAGE_PIXELS) {
                            val factor = sqrt(MAX_PAGE_PIXELS.toDouble() / pixels.toDouble())
                            width = (width * factor).toInt().coerceAtLeast(1)
                            height = (height * factor).toInt().coerceAtLeast(1)
                            Log.w(TAG, "Page $pageIndex scaled down to ${width}x$height")
                        }
                        renderedSize = "${width}x$height"
                        createBitmap(width, height).also {
                            // PdfRenderer draws the page onto whatever the bitmap already
                            // holds and never paints the paper itself, so without this the
                            // code sits on a transparent background
                            it.eraseColor(Color.WHITE)
                            page.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        }
                    } finally {
                        page.close()
                    }

                    try {
                        val barcodes = Tasks.await(
                            scanner.process(InputImage.fromBitmap(bitmap, 0)),
                            DECODE_TIMEOUT_SECONDS,
                            TimeUnit.SECONDS
                        )
                        for (barcode in barcodes) {
                            val text = normalizeCode(barcode.rawValue.orEmpty())
                            if (text.isEmpty()) continue
                            qrCodes.getOrPut(text) {
                                ScannedCode(text, CodeFormat.of(barcode.format))
                            }
                        }
                    } catch (e: OutOfMemoryError) {
                        Log.e(TAG, "Out of memory decoding page $pageIndex", e)
                    } finally {
                        bitmap.recycle()
                    }
                }

                Log.i(TAG, "Parsed $pageCount pages at $renderedSize, found ${qrCodes.size} codes")
            }
        }
    } finally {
        scanner.close()
        if (!tempFile.delete()) {
            Log.w(TAG, "Could not delete temp file ${tempFile.absolutePath}")
        }
    }

    PdfScanResult(qrCodes.values.toList(), pageCount, renderedSize)
}

fun getFileNameFromUri(uri: Uri, context: Context): String {
    try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx != -1 && cursor.moveToFirst()) {
                return cursor.getString(idx) ?: ""
            }
        }
    } catch (e: Exception) {
        Log.w(TAG, "Could not read display name for $uri", e)
    }
    return uri.lastPathSegment.orEmpty().substringAfterLast('/')
}
