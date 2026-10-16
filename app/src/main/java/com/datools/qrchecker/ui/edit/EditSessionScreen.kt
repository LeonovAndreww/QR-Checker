package com.datools.qrchecker.ui.edit

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import com.datools.qrchecker.popBackStackOnce
import com.datools.qrchecker.R
import com.datools.qrchecker.util.rememberFeedback
import com.datools.qrchecker.util.getFileNameFromUri
import com.datools.qrchecker.util.countFormats
import com.datools.qrchecker.ui.FullHeightColumn
import com.datools.qrchecker.ui.resolve
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import android.content.ClipData
import android.os.Build
import androidx.compose.animation.animateContentSize
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.ClipEntry
import com.datools.qrchecker.util.shortCode
import com.datools.qrchecker.util.Outcome
import com.datools.qrchecker.util.formatTimeAgo
import com.datools.qrchecker.ui.FormatFilter
import com.datools.qrchecker.ui.SwipeAction
import com.datools.qrchecker.ui.SwipeActionRow
import com.datools.qrchecker.ui.asString


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditSessionScreen(
    navController: NavController
) {
    val context = LocalContext.current
    val viewModel: EditSessionViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val original = state.original
    val name = state.name
    val selectedNames = state.selectedNames
    val keptFormats = state.keptFormats
    val isLoading = state.isLoading
    val binned = state.binned
    val formatCounts = remember(state.parsedCodes, state.parsedFormats) {
        state.parsedCodes?.let { countFormats(it, state.parsedFormats) }.orEmpty()
    }
    val keptCodes = remember(state.parsedCodes, state.parsedFormats, state.keptFormats) {
        state.keptCodes
    }

    // корзина: что убрали из сессии и когда
    var showEmptyBinConfirm by remember { mutableStateOf(false) }
    // раскрытые коды корзины, сколько угодно сразу
    var expandedBinCodes by remember { mutableStateOf(setOf<String>()) }
    val snackbarHostState = remember { SnackbarHostState() }
    val clipboard = LocalClipboard.current
    val feel = rememberFeedback()

    // confirmation dialog when replacing codes
    var showReplaceConfirm by remember { mutableStateOf(false) }
    // «Заменить» - поведение, которое было до сих пор, поэтому оно и остаётся по умолчанию
    var replaceCodesMode by remember { mutableStateOf(true) }
    val codesFromPdfTitle = stringResource(id = R.string.codes_from_pdf_title)
    val modeReplaceText = stringResource(id = R.string.codes_mode_replace)
    val modeAppendText = stringResource(id = R.string.codes_mode_append)
    val saveCodesText = stringResource(id = R.string.save_codes)
    val binTitle = stringResource(id = R.string.bin_title)
    val binWhy = stringResource(id = R.string.bin_why)
    val binRestoreCd = stringResource(id = R.string.bin_restore)
    val binEmptyText = stringResource(id = R.string.bin_empty)
    val binEmptyTitle = stringResource(id = R.string.bin_empty_title)
    val binEmptyConfirm = stringResource(id = R.string.bin_empty_confirm)
    val binDeleteCd = stringResource(id = R.string.cd_swipe_delete)
    val copyCodeText = stringResource(id = R.string.cd_copy_code)
    val codeCopiedText = stringResource(id = R.string.code_copied)

    LaunchedEffect(state.finished) {
        if (state.finished) navController.popBackStackOnce()
    }
    LaunchedEffect(viewModel, context) {
        viewModel.messages.collect { message ->
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(message.resolve(context))
        }
    }

    val documentPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        val files = uris.map { it to getFileNameFromUri(it, context) }
        viewModel.parseFiles(files)
    }

    val selectedFilesTemplate = stringResource(id = R.string.selected_files)
    val titleText = stringResource(id = R.string.setup_session_title)
    val backCd = stringResource(id = R.string.cd_back)
    val nameLabel = stringResource(id = R.string.session_name_label)
    val selectPdfLabel = stringResource(id = R.string.select_pdf_label)
    val pdfIconDesc = stringResource(id = R.string.cd_pdf_icon)
    val cancelText = stringResource(id = R.string.delete_cancel)
    val saveText = stringResource(id = R.string.save_button)
    val parsingText = stringResource(id = R.string.parsing_pdf)

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(titleText) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStackOnce() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = backCd
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        FullHeightColumn(modifier = Modifier.padding(innerPadding).padding(16.dp)) {
            TextField(
                value = name,
                onValueChange = viewModel::setName,
                label = { Text(nameLabel) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = { documentPicker.launch(arrayOf("*/*")) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp),
                shape = MaterialTheme.shapes.small
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // общий значок файла, а не лист PDF: сюда давно кладут ещё CSV,
                    // картинки и файлы сессий
                    Icon(
                        painter = painterResource(id = R.drawable.ic_file_open),
                        contentDescription = pdfIconDesc
                    )

                    Text(
                        text = when (selectedNames.size) {
                            0 -> selectPdfLabel
                            1 -> selectedNames.first()
                            else -> selectedFilesTemplate.format(selectedNames.size)
                        },
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        textAlign = TextAlign.End,
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 12.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Counts row: current codes (original) and selected-file codes (parsed)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val origCount = original?.codes?.size ?: 0
                val selectedCountText = when {
                    keptCodes != null -> keptCodes.size.toString()
                    selectedNames.isNotEmpty() && isLoading -> parsingText
                    selectedNames.isNotEmpty() -> "..."
                    else -> "—"
                }

                Text(
                    text = stringResource(R.string.was_qr_count, origCount),
                    style = MaterialTheme.typography.bodyLarge
                )

                Text(
                    text = stringResource(R.string.will_be_qr_count, selectedCountText),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.End
                )
            }

            if (!isLoading) {
                FormatFilter(formatCounts, keptFormats, viewModel::setKeptFormats)
            }

            if (isLoading) {
                Spacer(modifier = Modifier.height(12.dp))
                CircularProgressIndicator()
            }

            state.error?.let { err ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = err.asString(),
                    color = MaterialTheme.colorScheme.error
                )
            }

            // Корзина показывается, только когда в ней что-то есть.
            //
            // Пустая карточка «здесь ничего нет» - это строка, объясняющая собственное
            // отсутствие; на экране правки, где и так тесно, ей делать нечего.
            if (binned.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = binTitle.format(binned.size),
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { showEmptyBinConfirm = true }) {
                                Text(binEmptyText)
                            }
                        }

                        Text(
                            text = binWhy,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        // Список ограничен по высоте и прокручивается сам: в корзине
                        // может лежать и сотня кодов, а экран правки - не место, где
                        // их разглядывают.
                        LazyColumn(
                            modifier = Modifier.heightIn(max = 260.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(binned, key = { it.first }) { (code, at) ->
                                val expanded = code in expandedBinCodes

                                // Те же жесты, что и в списках кодов: вправо -
                                // скопировать, влево - удалить. В корзине «удалить»
                                // значит уже насовсем, и другого значения у него здесь
                                // быть не может.
                                SwipeActionRow(
                                    onThresholdCrossed = { feel(Outcome.THRESHOLD) },
                                    start = SwipeAction(
                                        background = MaterialTheme.colorScheme.secondaryContainer,
                                        onTrigger = {
                                            feel(Outcome.ACTION)
                                            scope.launch {
                                                clipboard.setClipEntry(
                                                    ClipEntry(
                                                        ClipData.newPlainText(copyCodeText, code)
                                                    )
                                                )
                                                if (Build.VERSION.SDK_INT <
                                                    Build.VERSION_CODES.TIRAMISU
                                                ) {
                                                    snackbarHostState.currentSnackbarData?.dismiss()
                                                    snackbarHostState.showSnackbar(codeCopiedText)
                                                }
                                            }
                                        },
                                        icon = {
                                            Icon(
                                                painter = painterResource(
                                                    id = R.drawable.ic_content_copy
                                                ),
                                                contentDescription = copyCodeText,
                                                tint = MaterialTheme.colorScheme
                                                    .onSecondaryContainer
                                            )
                                        }
                                    ),
                                    end = SwipeAction(
                                        background = MaterialTheme.colorScheme.errorContainer,
                                        onTrigger = {
                                            viewModel.purgeCode(code)
                                        },
                                        icon = {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = binDeleteCd,
                                                tint = MaterialTheme.colorScheme.onErrorContainer
                                            )
                                        }
                                    )
                                ) {
                                    Card(
                                        onClick = {
                                            expandedBinCodes =
                                                if (expanded) expandedBinCodes - code else expandedBinCodes + code
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .animateContentSize()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = if (expanded) code else remember(code) { shortCode(code) },
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    maxLines = if (expanded) Int.MAX_VALUE else 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                if (at > 0) {
                                                    Text(
                                                        text = formatTimeAgo(context, at),
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme
                                                            .onSurfaceVariant
                                                    )
                                                }
                                            }
                                            IconButton(onClick = { viewModel.restoreCode(code) }) {
                                                Icon(
                                                    painter = painterResource(
                                                        id = R.drawable.ic_restore
                                                    ),
                                                    contentDescription = binRestoreCd
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { navController.popBackStackOnce() },
                    modifier = Modifier
                        .weight(1f)
                        .height(72.dp),
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(cancelText)
                }

                Button(
                    onClick = {
                        val origCodes = original?.codes ?: emptyList()
                        val newCodes = keptCodes
                        val willReplace = (newCodes != null) && (newCodes != origCodes)

                        if (willReplace) {
                            showReplaceConfirm = true
                        } else {
                            viewModel.save(newCodes = null)
                        }
                    },
                    enabled = name.isNotBlank() && !isLoading,
                    modifier = Modifier
                        .weight(1f)
                        .height(72.dp),
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(saveText)
                }
            }
        }

    }

    if (showEmptyBinConfirm) {
        AlertDialog(
            onDismissRequest = { showEmptyBinConfirm = false },
            title = { Text(binEmptyTitle) },
            text = { Text(binEmptyConfirm.format(binned.size)) },
            confirmButton = {
                TextButton(onClick = {
                    showEmptyBinConfirm = false
                    viewModel.emptyBin()
                }) {
                    Text(stringResource(id = R.string.delete_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showEmptyBinConfirm = false }) {
                    Text(cancelText)
                }
            }
        )
    }

    original?.let { current ->
        if (!showReplaceConfirm) return@let

        val picked = keptCodes ?: current.codes
        val previousCodesForMode = current.codes.toHashSet()
        // «Заменить» - список становится тем, что в PDF. «Добавить» - к тому, что уже
        // есть, дописываются только незнакомые коды: партию догружают по частям, и
        // повторная загрузка того же файла не должна ничего задваивать.
        val finalCodes = if (replaceCodesMode) {
            picked
        } else {
            current.codes + picked.filterNot { it in previousCodesForMode }
        }
        // множествами, а не списками: на партии в тысячи коробок contains по списку
        // превращает подсчёт в квадрат
        val nextCodes = finalCodes.toHashSet()
        val previousCodes = current.codes.toHashSet()

        val lostMarks = current.scannedCodes.filterNot { it in nextCodes }
        val keptMarks = current.scannedCodes.size - lostMarks.size
        val newCodes = finalCodes.count { it !in previousCodes }

        AlertDialog(
            onDismissRequest = { showReplaceConfirm = false },
            title = { Text(codesFromPdfTitle) },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = replaceCodesMode,
                            onClick = { replaceCodesMode = true },
                            label = { Text(modeReplaceText) }
                        )
                        FilterChip(
                            selected = !replaceCodesMode,
                            onClick = { replaceCodesMode = false },
                            label = { Text(modeAppendText) }
                        )
                    }
                    // по одному слову не понять, что станет с уже отмеченным, а именно
                    // это и решается выбором режима
                    Text(
                        text = stringResource(
                            if (replaceCodesMode) {
                                R.string.codes_mode_replace_hint
                            } else {
                                R.string.codes_mode_append_hint
                            }
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = stringResource(R.string.replace_diff_keep, keptMarks),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = stringResource(R.string.replace_diff_lost, lostMarks.size),
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (lostMarks.isEmpty()) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.error
                        }
                    )
                    Text(
                        text = stringResource(R.string.replace_diff_new, newCodes),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    if (!replaceCodesMode) {
                        Text(
                            text = stringResource(
                                R.string.codes_already_present,
                                picked.size - newCodes
                            ),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = stringResource(R.string.replace_diff_total, finalCodes.size),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    if (current.collecting) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.collecting_becomes_checking),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Пропавшая отметка - это потерянная работа, поэтому её показываем
                    // поимённо, а не числом. Новые коды перечислять незачем: их и так
                    // видно в сессии после замены.
                    if (lostMarks.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = stringResource(R.string.replace_diff_lost_header),
                            style = MaterialTheme.typography.titleSmall
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        for (code in lostMarks.take(LOST_MARKS_SHOWN)) {
                            Text(
                                text = shortCode(code),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (lostMarks.size > LOST_MARKS_SHOWN) {
                            Text(
                                text = stringResource(
                                    R.string.replace_diff_and_more,
                                    lostMarks.size - LOST_MARKS_SHOWN
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showReplaceConfirm = false
                    viewModel.save(newCodes = finalCodes)
                }) {
                    Text(saveCodesText)
                }
            },
            dismissButton = {
                TextButton(onClick = { showReplaceConfirm = false }) {
                    Text(cancelText)
                }
            }
        )
    }
}

/** Сколько пропадающих отметок показать поимённо, прежде чем свернуть в «и ещё N». */
private const val LOST_MARKS_SHOWN = 12