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
            // Замерочный проход возвращает null по контракту - inJustDecodeBounds на то
            // и нужен. Проверять тут надо поток, а не результат: на результате стояло
            // "?: return emptyList()", и функция выходила всегда, ни разу не дойдя до
            // самой картинки.
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            val measured = context.contentResolver.openInputStream(uri)
                ?: return@withContext emptyList()
            measured.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext emptyList()

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
        } catch (e: Exception) {
            // Истёкший срок и отказ распознавателя - обычный исход для картинки,
            // снятой под углом или не в фокусе. Файл остаётся без кодов, разбор
            // остальных выбранных файлов продолжается.
            Log.e(TAG, "Could not read the codes", e)
            emptyList()
        } finally {
            bitmap.recycle()
            scanner.close()
        }
    }
