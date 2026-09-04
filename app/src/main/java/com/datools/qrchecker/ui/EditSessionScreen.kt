package com.datools.qrchecker.ui

import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import android.content.ClipData
import android.os.Build
import androidx.compose.animation.animateContentSize
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import com.datools.qrchecker.popBackStackOnce
import com.datools.qrchecker.R
import com.datools.qrchecker.data.SessionRepository
import com.datools.qrchecker.util.shortCode
import com.datools.qrchecker.util.SessionBackup
import com.datools.qrchecker.model.SessionData
import com.datools.qrchecker.util.Outcome
import com.datools.qrchecker.util.formatTimeAgo
import com.datools.qrchecker.util.rememberFeedback
import com.datools.qrchecker.util.getFileNameFromUri
import com.datools.qrchecker.util.readCodesFromFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "QRChecker"

/**
 * A failure to show, kept as a resource id so the text is resolved during composition and
 * follows a locale change, instead of being baked in from a Context at the time it happened.
 */
private data class ScreenError(@param:StringRes val resId: Int, val detail: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditSessionScreen(
    navController: NavController,
    sessionId: String
) {
    val context = LocalContext.current
    val repo = remember { SessionRepository(context) }
    val scope = rememberCoroutineScope()

    var original by remember { mutableStateOf<SessionData?>(null) }

    // form states
    var name by remember { mutableStateOf("") }

    // file picker states
    var selectedNames by remember { mutableStateOf(listOf<String>()) }

    // parsed codes from newly selected PDF (null = nothing selected, empty list = parsed but no codes)
    var parsedCodes by remember { mutableStateOf<List<String>?>(null) }

    // loading / error
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<ScreenError?>(null) }

    // корзина: что убрали из сессии и когда
    var binned by remember { mutableStateOf<List<Pair<String, Long>>>(emptyList()) }
    var showEmptyBinConfirm by remember { mutableStateOf(false) }
    // раскрыт всегда не больше одного кода: две длинных строки рядом снова превращают
    // корзину в стену текста
    var expandedBinCode by remember { mutableStateOf<String?>(null) }
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
    val binDeletedText = stringResource(id = R.string.bin_deleted)
    val binDeleteCd = stringResource(id = R.string.cd_swipe_delete)
    val copyCodeText = stringResource(id = R.string.cd_copy_code)
    val codeCopiedText = stringResource(id = R.string.code_copied)

    suspend fun reloadBin() {
        binned = try {
            repo.binnedCodes(sessionId)
        } catch (t: Throwable) {
            Log.e(TAG, "Can't read the bin of session $sessionId", t)
            emptyList()
        }
    }

    // Сессия и её корзина читаются при входе на экран.
    //
    // Загрузка пропала при выносе разбора файлов в FileCodes.kt, и экран правки с тех
    // пор открывался с пустым полем имени, а «Сохранить» молча ничего не делал: сохранять
    // было нечего, original так и оставался пустым.
    LaunchedEffect(sessionId) {
        isLoading = true
        try {
            val loaded = repo.getById(sessionId)
            original = loaded
            if (loaded != null) {
                name = loaded.name
                reloadBin()
            } else {
                navController.popBackStackOnce()
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Can't load session $sessionId", t)
            errorMessage = ScreenError(R.string.error_loading_session, t.message ?: "")
        } finally {
            isLoading = false
        }
    }

    /** [newCodes] is null when only the name changed, so the code rows are left alone. */
    fun saveAndClose(newCodes: List<String>?) {
        val current = original ?: return
        scope.launch {
            isLoading = true
            try {
                if (newCodes == null) {
                    repo.rename(current.id, name)
                } else {
                    // the repository keeps the scanned state of the codes that survive
                    repo.replaceCodes(current.id, name, newCodes)
                }
                // копия снимается с того, что легло в базу, а не с того, что было на экране
                repo.getById(current.id)?.let { SessionBackup.scheduleSave(context, it) }
                navController.popBackStackOnce()
            } catch (t: Throwable) {
                Log.e(TAG, "Can't save session", t)
                errorMessage = ScreenError(R.string.error_saving_session, t.message ?: "")
            } finally {
                isLoading = false
            }
        }
    }

    val documentPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        val files = uris.map { it to getFileNameFromUri(it, context) }
        selectedNames = files.map { it.second }
        scope.launch {
            isLoading = true
            errorMessage = null
            try {
                // тот же разбор, что и при создании сессии: документы, картинки и списки
                // вперемешку. Раньше здесь принимался только PDF, и одно и то же действие
                // в двух местах приложения работало по-разному
                parsedCodes = readCodesFromFiles(context, files).codes
            } catch (t: Throwable) {
                Log.e(TAG, "Can't read the selected files", t)
                parsedCodes = emptyList()
                errorMessage = ScreenError(R.string.error_parsing_pdf, t.message ?: "")
            } finally {
                isLoading = false
            }
        }
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
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            TextField(
                value = name,
                onValueChange = { v -> name = v.filterNot { it == '\n' } },
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
                    parsedCodes != null -> parsedCodes!!.size.toString()
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

            if (isLoading) {
                Spacer(modifier = Modifier.height(12.dp))
                CircularProgressIndicator()
            }

            errorMessage?.let { err ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(err.resId, err.detail),
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
                                val expanded = code == expandedBinCode

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
                                            scope.launch {
                                                try {
                                                    repo.purgeCode(sessionId, code)
                                                    reloadBin()
                                                    snackbarHostState.currentSnackbarData
                                                        ?.dismiss()
                                                    snackbarHostState.showSnackbar(binDeletedText)
                                                } catch (t: Throwable) {
                                                    Log.e(TAG, "Can't purge $code", t)
                                                }
                                            }
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
                                            expandedBinCode = if (expanded) null else code
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
                                                    text = if (expanded) code else shortCode(code),
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
                                            IconButton(onClick = {
                                                scope.launch {
                                                    try {
                                                        repo.restoreCode(sessionId, code)
                                                        original = repo.getById(sessionId)
                                                        reloadBin()
                                                    } catch (t: Throwable) {
                                                        Log.e(TAG, "Can't restore $code", t)
                                                    }
                                                }
                                            }) {
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
                        val newCodes = parsedCodes
                        val willReplace = (newCodes != null) && (newCodes != origCodes)

                        if (willReplace) {
                            showReplaceConfirm = true
                        } else {
                            saveAndClose(newCodes = null)
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
                    scope.launch {
                        try {
                            repo.emptyBin(sessionId)
                            reloadBin()
                        } catch (t: Throwable) {
                            Log.e(TAG, "Can't empty the bin", t)
                        }
                    }
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

        val picked = parsedCodes ?: current.codes
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
                    saveAndClose(newCodes = finalCodes)
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