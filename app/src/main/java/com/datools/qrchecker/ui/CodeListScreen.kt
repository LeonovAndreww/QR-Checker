package com.datools.qrchecker.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.animation.animateContentSize
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.runtime.LaunchedEffect
import android.content.ClipData
import android.os.Build
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import android.util.Log
import com.datools.qrchecker.TYPE_SCANNED
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.material3.Checkbox
import com.datools.qrchecker.util.DeleteConfirmation
import com.datools.qrchecker.util.Outcome
import com.datools.qrchecker.util.rememberFeedback
import com.datools.qrchecker.util.SessionBackup
import com.datools.qrchecker.data.SessionRepository
import com.datools.qrchecker.util.buildCsv
import com.datools.qrchecker.util.formatScanTimeForScreen
import com.datools.qrchecker.util.shortCode
import com.datools.qrchecker.util.shareCsv
import com.datools.qrchecker.model.SessionData
import kotlinx.coroutines.launch
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.datools.qrchecker.popBackStackOnce
import com.datools.qrchecker.R

private const val TAG = "QRChecker"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CodesListScreen(
    navController: NavController,
    sessionId: String,
    type: String // TYPE_SCANNED or TYPE_NOT_SCANNED
) {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val feel = rememberFeedback()
    val repo = remember { SessionRepository(context) }
    var session by remember { mutableStateOf<SessionData?>(null) }
    val scope = rememberCoroutineScope()

    // snackbar host
    val snackbarHostState = remember { SnackbarHostState() }

    // dialog state for delete confirmation
    var query by rememberSaveable { mutableStateOf("") }
    // раскрыта всегда не больше одной плашки: две длинных строки рядом снова
    // превращают список в стену текста, от которой всё это и уводит
    var expandedCode by rememberSaveable { mutableStateOf<String?>(null) }
    var codeToDelete by remember { mutableStateOf<String?>(null) }
    var skipDeleteConfirm by remember {
        mutableStateOf(DeleteConfirmation.isSkipped(context, sessionId))
    }
    var dontAskChecked by remember { mutableStateOf(false) }
    var codeToDeleteIsScanned by remember { mutableStateOf(false) }

    // LaunchedEffect is already a coroutine tied to this composable, and repo.getById is a
    // suspend Room call that dispatches itself — no extra scope or IO switch needed here
    LaunchedEffect(sessionId) {
        session = try {
            repo.getById(sessionId)
        } catch (t: Throwable) {
            Log.e(TAG, "Can't load session $sessionId", t)
            null
        }
    }

    val titleScanned =
        stringResource(id = R.string.codes_title_scanned, session?.scannedCodes?.size ?: 0)
    val titleNotScanned = stringResource(
        id = R.string.codes_title_not_scanned,
        session?.let { it.codes.size - it.scannedCodes.size } ?: 0
    )
    val loadingText = stringResource(id = R.string.loading_session)
    val noScannedText = stringResource(id = R.string.no_scanned_codes)
    val noNotScannedText = stringResource(id = R.string.no_not_scanned_codes)
    val deleteCodeTitle = stringResource(id = R.string.delete_code_title)
    val deleteCancel = stringResource(id = R.string.delete_cancel)
    val deleteConfirm = stringResource(id = R.string.delete_confirm)
    val deleteSuccess = stringResource(id = R.string.delete_code_success)
    val deleteFailed = stringResource(id = R.string.delete_code_failed)
    val deleteError = stringResource(id = R.string.delete_code_error)
    val exportCd = stringResource(id = R.string.cd_export)
    val searchLabel = stringResource(id = R.string.search_codes)
    val clearSearchCd = stringResource(id = R.string.cd_clear_search)
    val noSearchResults = stringResource(id = R.string.no_search_results)
    val copyCodeText = stringResource(id = R.string.cd_copy_code)
    val codeCopiedText = stringResource(id = R.string.code_copied)
    val deleteCodeCd = stringResource(id = R.string.cd_delete_code)
    val csvColumnOnBox = stringResource(id = R.string.csv_column_on_box)
    val csvColumnFull = stringResource(id = R.string.csv_column_full)
    val csvColumnScannedAt = stringResource(id = R.string.csv_column_scanned_at)
    val dontAskText = stringResource(id = R.string.delete_dont_ask)
    val nothingToExport = stringResource(id = R.string.export_nothing_to_share)
    val swipeCopyCd = stringResource(id = R.string.cd_swipe_copy)
    val swipeDeleteCd = stringResource(id = R.string.cd_swipe_delete)
    val exportFailed = stringResource(id = R.string.export_failed)
    val exportHeader = stringResource(
        id = if (type == TYPE_SCANNED) R.string.export_header_scanned
        else R.string.export_header_not_scanned
    )

    // hoisted above the Scaffold: the toolbar action needs the same list the body draws
    val exportableCodes: List<String> = session?.let { loaded ->
        if (type == TYPE_SCANNED) {
            loaded.scannedCodes
        } else {
            loaded.codes.filter { it !in loaded.scannedCodes }
        }
    }.orEmpty()

    // the search narrows what is shown; the export always writes the whole list, so a
    // forgotten filter cannot quietly turn "what is missing" into a shorter answer
    val visibleCodes = remember(exportableCodes, query) {
        if (query.isBlank()) exportableCodes
        else exportableCodes.filter { it.contains(query.trim(), ignoreCase = true) }
    }

    // Сообщения вытесняют друг друга, а не встают в очередь: десяток действий подряд
    // иначе оборачивался десятком всплывающих плашек одна за другой.
    fun say(message: String) {
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(message)
        }
    }

    fun copyCode(code: String) {
        scope.launch {
            clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(copyCodeText, code)))
            // с Android 13 система сама показывает, что скопировано, и своя плашка
            // встаёт второй такой же поверх системной
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) say(codeCopiedText)
        }
    }

    fun performDelete(code: String, isScanned: Boolean) {
        scope.launch {
            try {
                // в списке отсканированных код возвращается в неотсканированные;
                // в списке неотсканированных он уходит из сессии совсем
                if (isScanned) {
                    repo.unmarkScanned(sessionId, code)
                } else {
                    repo.deleteCode(sessionId, code)
                }

                val updated = repo.getById(sessionId)
                if (updated != null) {
                    session = updated
                    SessionBackup.scheduleSave(context, updated)
                    say(deleteSuccess)
                } else {
                    say(deleteFailed)
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Can't delete code from session $sessionId", t)
                say("$deleteError: ${t.message}")
            }
        }
    }

    // один путь для кнопки и для свайпа: подтверждение спрашивается или нет одинаково
    fun requestDelete(code: String, isScanned: Boolean) {
        if (skipDeleteConfirm) {
            performDelete(code, isScanned)
        } else {
            codeToDelete = code
            codeToDeleteIsScanned = isScanned
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (type == TYPE_SCANNED) titleScanned else titleNotScanned
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStackOnce() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(id = R.string.cd_back)
                        )
                    }
                },
                actions = {
                    IconButton(
                        // кнопка живая и при пустом списке: неактивная выглядит ровно как
                        // сломанная, и понять, почему ничего не происходит, неоткуда
                        onClick = {
                            if (exportableCodes.isEmpty()) {
                                say(nothingToExport)
                                return@IconButton
                            }
                            val current = session ?: return@IconButton
                            scope.launch {
                                try {
                                    val intent = shareCsv(
                                        context = context,
                                        baseName = "${current.name}_$type",
                                        content = buildCsv(
                                            title = exportHeader,
                                            columnOnBox = csvColumnOnBox,
                                            columnFull = csvColumnFull,
                                            columnScannedAt = csvColumnScannedAt,
                                            codes = exportableCodes,
                                            scanTimes = current.scanTimes
                                        )
                                    )
                                    context.startActivity(intent)
                                } catch (t: Throwable) {
                                    Log.e(TAG, "Can't export the code list", t)
                                    say("$exportFailed: ${t.message}")
                                }
                            }
                        }
                    ) {
                        Icon(imageVector = Icons.Default.Share, contentDescription = exportCd)
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {

            if (session == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(loadingText)
                }
                return@Box
            }

            if (exportableCodes.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = if (type == TYPE_SCANNED) noScannedText else noNotScannedText
                    )
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it.filterNot { ch -> ch == '\n' } },
                        label = { Text(searchLabel) },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { query = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = clearSearchCd)
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )

                    if (visibleCodes.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(noSearchResults)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(items = visibleCodes, key = { it }) { code ->
                                val expanded = code == expandedCode
                                val isScanned = type == TYPE_SCANNED

                                SwipeActionRow(
                                    onThresholdCrossed = { feel(Outcome.THRESHOLD) },
                                    start = SwipeAction(
                                        background =
                                            MaterialTheme.colorScheme.secondaryContainer,
                                        onTrigger = {
                                            feel(Outcome.ACTION)
                                            copyCode(code)
                                        },
                                        icon = {
                                            Icon(
                                                painter = painterResource(
                                                    id = R.drawable.ic_content_copy
                                                ),
                                                contentDescription = swipeCopyCd,
                                                tint = MaterialTheme.colorScheme
                                                    .onSecondaryContainer
                                            )
                                        }
                                    ),
                                    end = SwipeAction(
                                        background = MaterialTheme.colorScheme.errorContainer,
                                        onTrigger = { requestDelete(code, isScanned) },
                                        icon = {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = swipeDeleteCd,
                                                tint = MaterialTheme.colorScheme.onErrorContainer
                                            )
                                        }
                                    )
                                ) {
                                Card(
                                    onClick = {
                                        expandedCode = if (expanded) null else code
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .animateContentSize()
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 10.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // свёрнуто видно ровно то, что напечатано на
                                            // коробке; криптохвост кода маркировки на
                                            // этикетку не выводят и глазами не сверяют
                                            Text(
                                                text = if (expanded) code else shortCode(code),
                                                maxLines = if (expanded) Int.MAX_VALUE else 1,
                                                overflow = TextOverflow.Ellipsis,
                                                style = MaterialTheme.typography.bodyLarge,
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .padding(end = 8.dp)
                                            )

                                            IconButton(
                                                onClick = {
                                                    codeToDelete = code
                                                    codeToDeleteIsScanned = (type == TYPE_SCANNED)
                                                }
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = deleteCodeCd
                                                )
                                            }
                                        }

                                        if (expanded) {
                                            // дата встаёт в одну строку с кнопкой и
                                            // прижимается вправо: строкой выше она
                                            // нагромождалась на сам код и растила плашку
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                            TextButton(onClick = { copyCode(code) }) {
                                                Icon(
                                                    painter = painterResource(
                                                        id = R.drawable.ic_content_copy
                                                    ),
                                                    contentDescription = null,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(copyCodeText)
                                            }
                                            Spacer(modifier = Modifier.weight(1f))
                                            session?.scanTimes?.get(code)?.let { at ->
                                                Text(
                                                    text = formatScanTimeForScreen(at),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
                }
            }

            if (codeToDelete != null) {
                val previewCode = codeToDelete?.let { shortCode(it) } ?: ""
                AlertDialog(
                    onDismissRequest = {
                        codeToDelete = null
                        dontAskChecked = false
                    },
                    title = {
                        Text(deleteCodeTitle, style = MaterialTheme.typography.headlineSmall)
                    },
                    text = {
                        Column {
                            Text(
                                stringResource(id = R.string.delete_code_confirm),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            // код отдельной строкой: в одну с вопросом он не влезает и
                            // обрезается на пару символов
                            Text(
                                text = previewCode,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { dontAskChecked = !dontAskChecked }
                            ) {
                                Checkbox(
                                    checked = dontAskChecked,
                                    onCheckedChange = { dontAskChecked = it }
                                )
                                Text(dontAskText, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            val code = codeToDelete!!
                            val isScanned = codeToDeleteIsScanned
                            if (dontAskChecked) {
                                DeleteConfirmation.skip(context, sessionId)
                                skipDeleteConfirm = true
                            }
                            codeToDelete = null
                            dontAskChecked = false
                            performDelete(code, isScanned)
                        }) {
                            Text(deleteConfirm)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            // отменили - значит и галочку не применяем, и не оставляем
                            // её нажатой на следующий раз
                            codeToDelete = null
                            dontAskChecked = false
                        }) {
                            Text(deleteCancel)
                        }
                    }
                )
            }
        }
    }
}
