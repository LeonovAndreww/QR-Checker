package com.datools.qrchecker.ui.scan

import android.util.Log
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import android.graphics.Rect
import com.datools.qrchecker.util.normalizeCode
import com.datools.qrchecker.util.CodeFormat
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage

private const val TAG = "CodeAnalyzer"

/**
 * A code found in a frame and its box, in the coordinates of the rotated frame the user
 * sees. The box picks the code in the viewfinder and outlines the accepted one.
 */
data class DetectedCode(
    val value: String,
    val box: Rect,
    val imageWidth: Int,
    val imageHeight: Int,
    /** A linear barcode: no error correction, so a single read can be wrong. */
    val linear: Boolean = false,
    /** Capture time in ms; smoothing runs on it rather than on processing time. */
    val frameTimeMs: Long = 0L
)


/**
 * Decodes QR codes from camera frames with ML Kit.
 *
 * The frame is handed over as-is together with its rotation, so codes are read at any
 * orientation without copying and rebuilding the luminance plane by hand.
 */
class MlKitQrCodeAnalyzer(
    private val onCodesDetected: (List<DetectedCode>) -> Unit
) : ImageAnalysis.Analyzer, AutoCloseable {

    /**
     * Frames are decoded only until this moment. In scan-on-tap mode a frame is closed
     * right away, before ML Kit, since decoding is what drains the battery.
     */
    @Volatile
    var activeUntil: Long = Long.MAX_VALUE

    private val scanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            // Every format the detector knows: a code from outside the list gets an
            // honest "not in this session", while silence reads as a broken camera.
            .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
            .build()
    )

    @androidx.annotation.OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null || System.currentTimeMillis() > activeUntil) {
            imageProxy.close()
            return
        }

        val rotation = imageProxy.imageInfo.rotationDegrees
        // capture time: the main thread may be busy and receive two frames at once
        val frameTimeMs = imageProxy.imageInfo.timestamp / 1_000_000
        // ML Kit reports coordinates in the rotated frame, so sides swap at 90° and 270°
        val upright = rotation == 90 || rotation == 270
        val width = if (upright) mediaImage.height else mediaImage.width
        val height = if (upright) mediaImage.width else mediaImage.height

        val image = InputImage.fromMediaImage(mediaImage, rotation)
        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                // all codes of the frame; which one to take is the camera preview's call
                barcodes
                    .mapNotNull { barcode ->
                        // Normalised here, not just when marking: frame colours and
                        // presence compare this value, and a code with GS or a line
                        // break at its ends would otherwise not match its mark.
                        val value = barcode.rawValue?.let(::normalizeCode)
                            ?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                        val box = barcode.boundingBox ?: return@mapNotNull null
                        val linear = CodeFormat.of(barcode.format) == CodeFormat.LINEAR
                        DetectedCode(value, box, width, height, linear, frameTimeMs)
                    }
                    .takeIf { it.isNotEmpty() }
                    ?.let(onCodesDetected)
            }
            .addOnFailureListener { Log.w(TAG, "Frame analysis failed", it) }
            // the frame must stay open until ML Kit is done with it
            .addOnCompleteListener { imageProxy.close() }
    }

    override fun close() = scanner.close()
}

