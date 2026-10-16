package com.datools.qrchecker.ui.codes

import com.datools.qrchecker.R
import com.datools.qrchecker.TYPE_SCANNED
import com.datools.qrchecker.codes.shortCode
import com.datools.qrchecker.files.buildCsv
import com.datools.qrchecker.files.shareCsv
import com.datools.qrchecker.popBackStackOnce
import com.datools.qrchecker.settings.AppSettings
import com.datools.qrchecker.ui.CodeMetaLine
import com.datools.qrchecker.ui.ScrollbarBox
import com.datools.qrchecker.ui.SwipeAction
import com.datools.qrchecker.ui.SwipeActionRow
import com.datools.qrchecker.ui.resolve
import com.datools.qrchecker.util.Outcome
import com.datools.qrchecker.util.formatScanTimeForScreen
import com.datools.qrchecker.util.formatTimeAgo
import com.datools.qrchecker.util.rememberFeedback
import android.content.ClipData
import android.os.Build
import android.util.Log
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "QRChecker"

/** How often "time ago" labels are refreshed. */
private const val TIME_AGO_REFRESH_MS = 10_000L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CodesListScreen(
    navController: NavController,
    type: String // TYPE_SCANNED or TYPE_NOT_SCANNED
) {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val feel = rememberFeedback()
    val viewModel: CodeListViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val session = state.session
    val scope = rememberCoroutineScope()

    // snackbar host
    val snackbarHostState = remember { SnackbarHostState() }

    // "12 seconds ago" stops being true, and nothing else redraws the list
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(TIME_AGO_REFRESH_MS)
            now = System.currentTimeMillis()
        }
    }

    // dialog state for delete confirmation
    var query by rememberSaveable { mutableStateOf("") }
    // several codes can be expanded at once, so they can be compared
    var expandedCodes by rememberSaveable { mutableStateOf(setOf<String>()) }
    var codeToDelete by remember { mutableStateOf<String?>(null) }
    var newestFirst by rememberSaveable { mutableStateOf(true) }
    var dontAskChecked by remember { mutableStateOf(false) }
    var codeToDeleteIsScanned by remember { mutableStateOf(false) }
    val codeDetails by AppSettings.codeFieldsState(context)
    val codeKindShown by AppSettings.codeKindState(context)
    val scrollbarShown by AppSettings.scrollbarState(context)
    val listState = rememberLazyListState()

    val scannedCount = session?.scannedCodes?.size ?: 0
    // a collecting session has one list and its own wording: nothing to unmark into
    val collecting = session?.collecting == true
    val titleScanned = if (collecting) {
        stringResource(id = R.string.codes_title_collected, scannedCount)
    } else {
        stringResource(id = R.string.codes_title_scanned, scannedCount)
    }
    val titleNotScanned = stringResource(
        id = R.string.codes_title_not_scanned,
        session?.let { it.codes.size - it.scannedCodes.size } ?: 0
    )
    val loadingText = stringResource(id = R.string.loading_session)
    val noScannedText = if (collecting) {
        stringResource(id = R.string.no_collected_codes)
    } else {
        stringResource(id = R.string.no_scanned_codes)
    }
    val noNotScannedText = stringResource(id = R.string.no_not_scanned_codes)
    val binTitle = stringResource(id = R.string.bin_code_title)
    val binConfirm = stringResource(id = R.string.bin_code_confirm)
    val unmarkTitle = stringResource(id = R.string.unmark_code_title)
    val unmarkConfirm = stringResource(id = R.string.unmark_code_confirm)
    val deleteCancel = stringResource(id = R.string.delete_cancel)
    val binAction = stringResource(id = R.string.bin_code_action)
    val unmarkAction = stringResource(id = R.string.unmark_code_action)
    val exportCd = stringResource(id = R.string.cd_export)
    val sortNewestCd = stringResource(id = R.string.cd_sort_newest)
    val sortOldestCd = stringResource(id = R.string.cd_sort_oldest)
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

    // Hoisted above the Scaffold: the toolbar action needs the same list the body draws.
    // Scanned codes go newest first, so a mistake is at the top; unscanned codes keep
    // the document order. Membership goes through a set: on lists of thousands a list
    // lookup turned each search keystroke into millions of comparisons.
    val exportableCodes: List<String> = remember(session, type, newestFirst) {
        val loaded = session ?: return@remember emptyList()
        val order = if (type == TYPE_SCANNED) {
            val times = loaded.scanTimes.orEmpty()
            loaded.scannedCodes.sortedByDescending { times[it] ?: 0L }
        } else {
            val scanned = loaded.scannedCodes.toHashSet()
            loaded.codes.filterNot { it in scanned }
        }
        if (newestFirst) order else order.asReversed()
    }

    // The search narrows what is shown; the export always writes the whole list, so a
    // forgotten filter cannot quietly turn "what is missing" into a shorter answer.
    // Short forms are computed once per list and only once something is searched.
    val shortCodes = remember(session, type) { lazy { exportableCodes.associateWith { shortCode(it) } } }
    val visibleCodes = remember(exportableCodes, query) {
        if (query.isBlank()) {
            exportableCodes
        } else {
            // the shown form is searched too: for PPN, HIBC and Digital Link it is rebuilt
            // and does not occur in the raw code
            val q = query.trim()
            exportableCodes.filter {
                it.contains(q, ignoreCase = true) ||
                    shortCodes.value[it].orEmpty().contains(q, ignoreCase = true)
            }
        }
    }

    // messages replace each other instead of queueing up behind a burst of actions
    fun say(message: String) {
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(message)
        }
    }

    fun copyCode(code: String) {
        scope.launch {
            clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(copyCodeText, code)))
            // Android 13+ shows its own confirmation
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) say(codeCopiedText)
        }
    }

    LaunchedEffect(viewModel, context) {
        viewModel.messages.collect { say(it.resolve(context)) }
    }

    // the button and the swipe share one path, so confirmation works the same
    fun requestDelete(code: String, isScanned: Boolean) {
        if (state.skipDeleteConfirm) {
            viewModel.delete(code, isScanned)
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
                    IconButton(onClick = { newestFirst = !newestFirst }) {
                        Icon(
                            painter = painterResource(
                                id = if (newestFirst) R.drawable.ic_sort_desc
                                else R.drawable.ic_sort_asc
                            ),
                            contentDescription = if (newestFirst) sortOldestCd else sortNewestCd
                        )
                    }

                    IconButton(
                        // enabled on an empty list too: a disabled button looks broken
                        onClick = {
                            if (exportableCodes.isEmpty()) {
                                say(nothingToExport)
                                return@IconButton
                            }
                            val current = session ?: return@IconButton
                            scope.launch {
                                try {
                                    val content = withContext(Dispatchers.Default) {
                                        buildCsv(
                                            title = exportHeader,
                                            columnOnBox = csvColumnOnBox,
                                            columnFull = csvColumnFull,
                                            columnScannedAt = csvColumnScannedAt,
                                            codes = exportableCodes,
                                            scanTimes = current.scanTimes
                                        )
                                    }
                                    val intent = shareCsv(
                                        context = context,
                                        baseName = "${current.name}_$type",
                                        content = content
                                    )
                                    context.startActivity(intent)
                                } catch (c: CancellationException) {
                                    throw c
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
                        ScrollbarBox(
                            state = listState,
                            enabled = scrollbarShown,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(items = visibleCodes, key = { it }) { code ->
                                    val expanded = code in expandedCodes
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
                                            expandedCodes =
                                                if (expanded) expandedCodes - code else expandedCodes + code
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
                                                // collapsed shows what is printed on the box;
                                                // the crypto tail of a marking code is not
                                                Column(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .padding(end = 8.dp)
                                                ) {
                                                    Text(
                                                        text = if (expanded) code else remember(code) { shortCode(code) },
                                                        maxLines = if (expanded) Int.MAX_VALUE else 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        style = MaterialTheme.typography.bodyLarge
                                                    )
                                                    CodeMetaLine(
                                                        code = code,
                                                        showKind = codeKindShown,
                                                        showFields = codeDetails,
                                                        singleLine = !expanded
                                                    )
                                                    // relative time when collapsed, the exact
                                                    // date when expanded
                                                    if (!expanded) {
                                                        session.scanTimes?.get(code)?.let { at ->
                                                            Text(
                                                                text = formatTimeAgo(context, at, now),
                                                                style = MaterialTheme.typography.bodySmall,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis
                                                            )
                                                        }
                                                    }
                                                }

                                                IconButton(
                                                    onClick = {
                                                        requestDelete(code, type == TYPE_SCANNED)
                                                    }
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Delete,
                                                        contentDescription = deleteCodeCd
                                                    )
                                                }
                                            }

                                            if (expanded) {
                                                // the date shares the row with the button
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
                                                session.scanTimes?.get(code)?.let { at ->
                                                    Text(
                                                        text = formatScanTimeForScreen(context, at),
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
            }

            if (codeToDelete != null) {
                val previewCode = codeToDelete?.let { shortCode(it) } ?: ""
                AlertDialog(
                    onDismissRequest = {
                        codeToDelete = null
                        dontAskChecked = false
                    },
                    title = {
                        // Codes are not deleted here: scanned ones are unmarked, unscanned
                        // ones go to the bin. "Delete" is the bin's word.
                        Text(
                            // a collecting session has nothing to unmark into, so its codes go to the bin
                            text = if (codeToDeleteIsScanned && !collecting) {
                                unmarkTitle
                            } else {
                                binTitle
                            },
                            style = MaterialTheme.typography.headlineSmall
                        )
                    },
                    text = {
                        Column {
                            Text(
                                text = if (codeToDeleteIsScanned && !collecting) {
                                    unmarkConfirm
                                } else {
                                    binConfirm
                                },
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            // on its own line: next to the question it would be truncated
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
                                viewModel.skipDeleteConfirmation()
                            }
                            codeToDelete = null
                            dontAskChecked = false
                            viewModel.delete(code, isScanned)
                        }) {
                            Text(
                                if (codeToDeleteIsScanned && !collecting) {
                                    unmarkAction
                                } else {
                                    binAction
                                }
                            )
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            // cancelling discards the checkbox too
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
