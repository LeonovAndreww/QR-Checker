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
 * Больше этого числа точек распознавателю не нужно: он работает по кадру телефона, а
 * не по снимку в полном разрешении.
 */
private const val MAX_IMAGE_PIXELS = 12_000_000L

/** Во сколько раз уменьшать картинку при распаковке: только степени двойки. */
private fun sampleSizeFor(width: Int, height: Int): Int {
    if (width <= 0 || height <= 0) return 1
    var sample = 1
    while (width.toLong() * height / (sample.toLong() * sample) > MAX_IMAGE_PIXELS) {
        sample *= 2
    }
    return sample
}

/**
 * Читает коды с картинки.
 *
 * Кодами делятся не только документами: приходит и снимок этикетки, и вырезанный кусок
 * экрана. Отдельная ветка нужна потому, что декодер требует картинку, а не файл, и путь
 * через PdfRenderer сюда не ведёт.
 */
suspend fun parseImageForCodes(context: Context, uri: Uri): List<ScannedCode> =
    withContext(Dispatchers.IO) {
        // Снимок с камеры бывает на пятьдесят мегапикселей: декодеру столько не нужно,
        // а памяти на такую картинку уходит больше, чем есть у процесса.
        //
        // Сначала читаются только размеры, по ним считается делитель, и лишь потом
        // картинка раскладывается в память. Раньше здесь стояло inSampleSize = 1 - то
        // есть «не уменьшать», ровно наоборот тому, что написано в комментарии, - и
        // сама распаковка лежала вне обработчика нехватки памяти.
        val bitmap = try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            } ?: return@withContext emptyList()

            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight)
            }
            context.contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, options)
            } ?: return@withContext emptyList()
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "Out of memory decoding an image", e)
            return@withContext emptyList()
        }

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
            Log.e(TAG, "Out of memory reading the codes", e)
            emptyList()
        } finally {
            bitmap.recycle()
            scanner.close()
        }
    }
