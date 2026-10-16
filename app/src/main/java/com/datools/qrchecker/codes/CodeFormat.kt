package com.datools.qrchecker.codes

import androidx.annotation.StringRes
import com.datools.qrchecker.R
import com.google.mlkit.vision.barcode.common.Barcode

/**
 * The symbology a code was printed with.
 *
 * Needed for one thing: next to a marking Data Matrix a retail label almost always has
 * a plain product barcode. Everything is read now, so such a sheet would give twice the
 * codes, half of them not what is being checked; the format is kept so a kind can be
 * unticked before the session is created.
 */
enum class CodeFormat(@get:StringRes val label: Int) {
    DATA_MATRIX(R.string.format_data_matrix),
    QR(R.string.format_qr),
    LINEAR(R.string.format_linear),
    OTHER(R.string.format_other),

    /** The code came from a text list, not an image: it has no symbology. */
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

/** Codes per kind, the most numerous first. */
fun countFormats(codes: List<String>, formats: Map<String, CodeFormat>): List<Pair<CodeFormat, Int>> =
    codes.groupingBy { formats[it] ?: CodeFormat.OTHER }.eachCount()
        .toList()
        .sortedWith(compareByDescending<Pair<CodeFormat, Int>> { it.second }.thenBy { it.first.ordinal })

/** Only codes of the kept kinds; a code of unknown kind counts as "other". */
fun keepFormats(
    codes: List<String>,
    formats: Map<String, CodeFormat>,
    kept: Set<CodeFormat>
): List<String> = codes.filter { (formats[it] ?: CodeFormat.OTHER) in kept }

/** A code with the symbology it was printed with. */
data class ScannedCode(
    val value: String,
    val format: CodeFormat
)
