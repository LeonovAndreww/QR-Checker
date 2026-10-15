package com.datools.qrchecker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.datools.qrchecker.R
import com.datools.qrchecker.util.CodeFormat

/**
 * Галочки по видам кодов.
 *
 * Общие для создания сессии и для её правки: розничный штрихкод рядом с Data Matrix
 * одинаково лишний в обоих случаях, а снять его можно было только при создании, и
 * догруженный документ приносил его в сессию навсегда.
 *
 * Показывается, только когда выбирать есть из чего, а последний вид снять нельзя:
 * документ без кодов - это не документ.
 */
@Composable
fun FormatFilter(
    counts: List<Pair<CodeFormat, Int>>,
    kept: Set<CodeFormat>,
    onKeptChange: (Set<CodeFormat>) -> Unit
) {
    if (counts.size <= 1) return
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(10.dp))
        Text(
            text = stringResource(id = R.string.format_filter_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(6.dp))
        val countTemplate = stringResource(id = R.string.format_filter_count)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            for ((format, count) in counts) {
                val on = format in kept
                FilterChip(
                    selected = on,
                    onClick = {
                        onKeptChange(
                            when {
                                !on -> kept + format
                                kept.size > 1 -> kept - format
                                else -> kept
                            }
                        )
                    },
                    label = { Text(countTemplate.format(stringResource(id = format.label), count)) }
                )
            }
        }
    }
}
