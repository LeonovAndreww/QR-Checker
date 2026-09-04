package com.datools.qrchecker.util

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

private const val TAG = "QRChecker"
private const val DECODE_TIMEOUT_SECONDS = 15L

/**
 * Читает коды с картинки.
 *
 * Кодами делятся не только документами: приходит и снимок этикетки, и вырезанный кусок
 * экрана. Отдельная ветка нужна потому, что декодер требует картинку, а не файл, и путь
 * через PdfRenderer сюда не ведёт.
 */
suspend fun parseImageForCodes(context: Context, uri: Uri): List<ScannedCode> =
    withContext(Dispatchers.IO) {
        val bitmap = context.contentResolver.openInputStream(uri)?.use { input ->
            // снимок с камеры бывает на 50 мегапикселей: декодеру столько не нужно, а
            // памяти на такую картинку уходит больше, чем есть у процесса
            val options = BitmapFactory.Options().apply { inSampleSize = 1 }
            BitmapFactory.decodeStream(input, null, options)
        } ?: return@withContext emptyList()

        val scanner = BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
                .build()
        )

        try {
            val barcodes = Tasks.await(
                scanner.process(InputImage.fromBitmap(bitmap, 0)),
                DECODE_TIMEOUT_SECONDS,
                TimeUnit.SECONDS
            )
            barcodes.mapNotNull { barcode ->
                val text = normalizeCode(barcode.rawValue.orEmpty())
                if (text.isEmpty()) null else ScannedCode(text, CodeFormat.of(barcode.format))
            }
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "Out of memory decoding an image", e)
            emptyList()
        } finally {
            bitmap.recycle()
            scanner.close()
        }
    }
