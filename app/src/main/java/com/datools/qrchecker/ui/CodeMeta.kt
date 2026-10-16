package com.datools.qrchecker.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.datools.qrchecker.R
import com.datools.qrchecker.util.describeCode
import com.datools.qrchecker.util.formatExpiry

/**
 * Одна строка под кодом: что это за код, до какого числа годен и какая партия.
 *
 * Видна и у свёрнутого кода: срок и партию на складе сверяют, просматривая список, а не
 * раскрывая каждую строку по очереди. Ничего из этого нет - нет и строки.
 */
@Composable
fun CodeMetaLine(
    code: String,
    showKind: Boolean,
    showFields: Boolean,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    singleLine: Boolean = true,
    textAlign: TextAlign? = null
) {
    val context = LocalContext.current
    // один разбор на строку: вид и поля берутся из него оба
    val description = remember(code) { describeCode(code) }
    val kind = description.kind.takeIf { showKind }
    val fields = description.fields.takeIf { showFields }
    val expiryLabel = stringResource(id = R.string.field_expiry)
    val batchLabel = stringResource(id = R.string.field_batch)
    val kindLabel = kind?.let { stringResource(id = it.label) }

    val parts = buildList {
        kindLabel?.let(::add)
        fields?.expiry?.let { formatExpiry(context, it) }?.let { add("$expiryLabel $it") }
        fields?.batch?.let { add("$batchLabel $it") }
    }
    if (parts.isEmpty()) return

    Text(
        text = parts.joinToString("  ·  "),
        style = MaterialTheme.typography.bodySmall,
        color = color,
        maxLines = if (singleLine) 1 else Int.MAX_VALUE,
        overflow = TextOverflow.Ellipsis,
        textAlign = textAlign,
        modifier = modifier
    )
}
