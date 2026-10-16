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
 * Какой код найден и где он лежал в кадре.
 *
 * Прямоугольник нужен для двух вещей сразу: отсеять коды, не попавшие в рамку, и
 * подсветить тот, который приняли. Координаты - уже повёрнутого кадра, того самого,
 * который человек видит на экране.
 */
data class DetectedCode(
    val value: String,
    val box: Rect,
    val imageWidth: Int,
    val imageHeight: Int,
    /** Линейный штрихкод: у него нет исправления ошибок, и он бывает прочитан неверно. */
    val linear: Boolean = false,
    /** Когда снят кадр, мс. По нему считается сглаживание, а не по времени разбора. */
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
     * До какого момента кадры разбираются. В режиме «по касанию» распознаватель стоит,
     * пока человек не коснётся экрана: кадр закрывается сразу, не доходя до ML Kit, -
     * а разбор кадра и есть то, что ест батарею.
     */
    @Volatile
    var activeUntil: Long = Long.MAX_VALUE

    private val scanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            // Камера читает всё, что умеет распознаватель. Ограничение двумя форматами
            // осталось с перехода на ML Kit и было лишним: код не из списка получает
            // честный ответ «нет в этой сессии», а вот молчание на поднесённый штрихкод
            // читается как сломанная камера.
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
        // время съёмки, а не момент, когда результат доберётся до экрана: главный поток
        // бывает занят, и два кадра подряд пришли бы к нему с разницей в миллисекунду
        val frameTimeMs = imageProxy.imageInfo.timestamp / 1_000_000
        // ML Kit отдаёт координаты уже повёрнутого кадра - того, что видно на экране,
        // поэтому при повороте на четверть стороны меняются местами
        val upright = rotation == 90 || rotation == 270
        val width = if (upright) mediaImage.height else mediaImage.width
        val height = if (upright) mediaImage.width else mediaImage.height

        val image = InputImage.fromMediaImage(mediaImage, rotation)
        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                // отдаются все коды кадра: какой из них брать - ближайший к центру или
                // тот, которого коснулись, - решает экран
                barcodes
                    .mapNotNull { barcode ->
                        // Нормализуется здесь, а не только при отметке: с этим значением
                        // сравниваются и код в кадре, и цвет рамки. Код с GS или переводом
                        // строки по краям иначе не совпадал с уже отмеченным - рамка
                        // краснела, а возврат коробки в рамку давал повторный ответ.
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

