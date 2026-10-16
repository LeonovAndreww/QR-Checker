package com.datools.qrchecker.ui

import com.datools.qrchecker.R
import com.datools.qrchecker.codes.describeCode
import com.datools.qrchecker.codes.formatExpiry
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

/**
 * One line under a code: its kind, expiry and batch. Shown for collapsed codes too,
 * since these are checked while scanning the list. Nothing to show, no line.
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
