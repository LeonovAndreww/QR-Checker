package com.datools.qrchecker.util

import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.util.Log
import androidx.core.graphics.createBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.sqrt

private const val TAG = "QRChecker"

/** The camera scanner only reads QR codes, so parsing anything else here would be pointless. */
private val QR_HINTS: Map<DecodeHintType, Any> = mapOf(
    DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
    DecodeHintType.TRY_HARDER to true
)

/**
 * Upper bound for a rendered page. A4 at scale 3 is already ~4.5M pixels, and the
 * decoder needs an extra IntArray of the same size, so an unbounded scale is an
 * out-of-memory crash waiting for a large page on a low-end device.
 */
private const val MAX_PAGE_PIXELS = 4_000_000L

/**
 * Renders every page of the PDF and decodes the QR code found on it.
 *
 * Pages without a QR code are skipped silently — that is normal input. Anything that
 * makes the document unreadable (no access to the uri, a broken PDF) is thrown to the
 * caller so the UI can tell the user what went wrong instead of showing an empty list.
 */
suspend fun parsePdfForQRCodes(
    context: Context,
    uri: Uri,
    scale: Int = 3
): List<String> = withContext(Dispatchers.IO) {
    val qrCodes = mutableListOf<String>()
    val tempFile = File.createTempFile("qrchecker_", ".pdf", context.cacheDir)

    try {
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(tempFile).use { output -> input.copyTo(output) }
        } ?: throw IllegalStateException("Cannot open the selected file")

        ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                val reader = MultiFormatReader().apply { setHints(QR_HINTS) }

                for (pageIndex in 0 until renderer.pageCount) {
                    // parsing a big document takes a while — honour cancellation
                    ensureActive()

                    val page = renderer.openPage(pageIndex)
                    val bitmap = try {
                        var width = page.width * scale
                        var height = page.height * scale
                        val pixels = width.toLong() * height.toLong()
                        if (pixels > MAX_PAGE_PIXELS) {
                            val factor = sqrt(MAX_PAGE_PIXELS.toDouble() / pixels.toDouble())
                            width = (width * factor).toInt().coerceAtLeast(1)
                            height = (height * factor).toInt().coerceAtLeast(1)
                        }
                        createBitmap(width, height).also {
                            page.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        }
                    } finally {
                        page.close()
                    }

                    try {
                        val px = IntArray(bitmap.width * bitmap.height)
                        bitmap.getPixels(px, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

                        val source = RGBLuminanceSource(bitmap.width, bitmap.height, px)
                        val result = reader.decode(BinaryBitmap(HybridBinarizer(source)))

                        val text = normalizeCode(result.text)
                        if (text.isNotEmpty()) qrCodes.add(text)
                    } catch (_: NotFoundException) {
                        // no QR code on this page — expected for cover/filler pages
                    } catch (e: OutOfMemoryError) {
                        Log.e(TAG, "Out of memory decoding page $pageIndex", e)
                    } finally {
                        bitmap.recycle()
                    }
                }
            }
        }
    } finally {
        if (!tempFile.delete()) {
            Log.w(TAG, "Could not delete temp file ${tempFile.absolutePath}")
        }
    }

    qrCodes.distinct()
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
