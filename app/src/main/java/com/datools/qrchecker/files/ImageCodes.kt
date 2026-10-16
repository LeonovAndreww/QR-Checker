package com.datools.qrchecker.files

import com.datools.qrchecker.codes.CodeFormat
import com.datools.qrchecker.codes.ScannedCode
import com.datools.qrchecker.codes.normalizeCode
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "QRChecker"
private const val DECODE_TIMEOUT_SECONDS = 15L

/** The detector works on camera-sized frames; more pixels than this are wasted memory. */
private const val MAX_IMAGE_PIXELS = 12_000_000L

/** The downsampling factor for decoding: powers of two only. */
private fun sampleSizeFor(width: Int, height: Int): Int {
    if (width <= 0 || height <= 0) return 1
    var sample = 1
    while (width.toLong() * height / (sample.toLong() * sample) > MAX_IMAGE_PIXELS) {
        sample *= 2
    }
    return sample
}

/**
 * Reads codes from an image: a photo of a label or a screenshot. A separate path because
 * the detector takes a bitmap, not a file, and PdfRenderer does not apply.
 */
suspend fun parseImageForCodes(context: Context, uri: Uri): List<ScannedCode> =
    withContext(Dispatchers.IO) {
        // A camera photo can have fifty megapixels, which the detector does not need and
        // the process cannot hold. Bounds are read first, the sample size computed from
        // them, and only then the image is decoded.
        val bitmap = try {
            // a bounds-only decode returns null by contract, so the stream is checked,
            // not the result
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
            // a timeout or a detector failure is normal for a tilted or blurry image: the
            // file just gives no codes, and the other files are still read
            Log.e(TAG, "Could not read the codes", e)
            emptyList()
        } finally {
            bitmap.recycle()
            scanner.close()
        }
    }
