package com.datools.qrchecker.util

import androidx.annotation.StringRes
import com.datools.qrchecker.R
import com.google.mlkit.vision.barcode.common.Barcode

/**
 * Каким символом записан код.
 *
 * Нужно ровно для одного: на розничной этикетке рядом с Data Matrix маркировки почти
 * всегда стоит ещё и обычный штрихкод товара. Пока распознавались только Data Matrix и
 * QR, этого не было видно; теперь читается всё подряд, и такой лист дал бы вдвое больше
 * кодов, половина из которых - не то, что сверяют. Поэтому формат запоминается, и
 * ненужный можно снять галочкой перед созданием сессии.
 */
enum class CodeFormat(@get:StringRes val label: Int) {
    DATA_MATRIX(R.string.format_data_matrix),
    QR(R.string.format_qr),
    LINEAR(R.string.format_linear),
    OTHER(R.string.format_other),

    /** Код пришёл из текстового списка, а не с картинки: символа у него нет. */
    TEXT(R.string.format_text);

    companion object {
        fun of(mlKitFormat: Int): CodeFormat = when (mlKitFormat) {
            Barcode.FORMAT_DATA_MATRIX -> DATA_MATRIX
            Barcode.FORMAT_QR_CODE -> QR
            Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8,
            Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E,
            Barcode.FORMAT_CODE_128, Barcode.FORMAT_CODE_39,
            Barcode.FORMAT_CODE_93, Barcode.FORMAT_CODABAR,
            Barcode.FORMAT_ITF -> LINEAR
            else -> OTHER
        }
    }
}

/** Код вместе с тем, чем он записан. */
data class ScannedCode(
    val value: String,
    val format: CodeFormat
)
