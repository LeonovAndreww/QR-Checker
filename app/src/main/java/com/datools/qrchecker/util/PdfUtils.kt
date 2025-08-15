package com.datools.qrchecker.util

import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.util.Log
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import androidx.core.graphics.createBitmap

suspend fun parsePdfForQRCodes(
    context: Context,
    uri: Uri,
    scale: Int = 3
): List<String> = withContext(Dispatchers.IO) {
    val qrCodes = mutableListOf<String>()
    val tempFile = File(context.cacheDir, "temp_${System.currentTimeMillis()}.pdf")

    try {
        Log.d("LogCat", "Copying uri to temp file...")
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(tempFile).use { output -> input.copyTo(output) }
        } ?: run {
            Log.e("LogCat", "openInputStream returned null for uri=$uri")
            return@withContext emptyList()
        }

        Log.d("LogCat", "Temp PDF path: ${tempFile.absolutePath}")

        ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                Log.d("LogCat", "Всего страниц в PDF: ${renderer.pageCount}")

                val reader = MultiFormatReader()
                for (pageIndex in 0 until renderer.pageCount) {
                    //Log.d("LogCat", "Rendering page $pageIndex ... (scale=$scale)")
                    val page = renderer.openPage(pageIndex)

                    val width = page.width * scale
                    val height = page.height * scale
                    val bitmap = createBitmap(width, height)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()

                    // Сохраняем первые 3 страницы в кеш для визуальной проверки
//                    if (pageIndex < 3) {
//                        try {
//                            val outFile = File(context.cacheDir, "pdf_page_${pageIndex}.png")
//                            FileOutputStream(outFile).use { fos ->
//                                bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
//                            }
//                            Log.d("LogCat", "Saved sample page $pageIndex -> ${outFile.absolutePath}")
//                        } catch (t: Throwable) {
//                            Log.w("LogCat", "Can't save sample page $pageIndex: ${t.message}")
//                        }
//                    }

                    try {
                        val px = IntArray(bitmap.width * bitmap.height)
                        bitmap.getPixels(px, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

                        val source = RGBLuminanceSource(bitmap.width, bitmap.height, px)
                        val binary = BinaryBitmap(HybridBinarizer(source))

                        val result = reader.decode(binary) // может бросить NotFoundException
                        val text = result.text.filter { it >= ' ' }
                        if (text.isNotEmpty()) {
                            qrCodes.add(text)
                        }

                    //Log.d("LogCat", "Found QR on page $pageIndex: $text")

                    } catch (_: NotFoundException) {
                        Log.d("LogCat", "QR not found on page $pageIndex")
                    } catch (t: Throwable) {
                        Log.e("LogCat", "Error decoding QR on page $pageIndex", t)
                    } finally {
                        bitmap.recycle()
                    }
                }
            }
        }
    } catch (t: Throwable) {
        Log.e("LogCat", "Error parsing PDF", t)
    } finally {
        try {
            tempFile.delete()
        } catch (_: Throwable) { /* ignore */ }
    }

    qrCodes.distinct()
}

fun getFileNameFromUri(uri: Uri, context: Context): String {
    var fileName = ""
    try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx != -1 && cursor.moveToFirst()) {
                fileName = cursor.getString(idx)
            }
        }
    } catch (e: Exception) {
        Log.e("LogCat", "Error getting file name", e)
    }
    return fileName
}
