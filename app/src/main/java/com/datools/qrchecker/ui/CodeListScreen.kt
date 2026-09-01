package com.datools.qrchecker.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import android.util.Log
import com.datools.qrchecker.TYPE_SCANNED
import com.datools.qrchecker.data.SessionRepository
import com.datools.qrchecker.util.buildCsv
import com.datools.qrchecker.util.shareCsv
import com.datools.qrchecker.model.SessionData
import kotlinx.coroutines.launch
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.res.stringResource
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
    val repo = remember { SessionRepository(context) }
    var session by remember { mutableStateOf<SessionData?>(null) }
    val scope = rememberCoroutineScope()

    // snackbar host
    val snackbarHostState = remember { SnackbarHostState() }

    // dialog state for delete confirmation
    var query by rememberSaveable { mutableStateOf("") }
    var codeToDelete by remember { mutableStateOf<String?>(null) }
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (type == TYPE_SCANNED) titleScanned else titleNotScanned
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(id = R.string.cd_back)
                        )
                    }
                },
                actions = {
                    IconButton(
                        enabled = exportableCodes.isNotEmpty(),
                        onClick = {
                            val current = session ?: return@IconButton
                            scope.launch {
                                try {
                                    val intent = shareCsv(
                                        context = context,
                                        baseName = "${current.name}_$type",
                                        content = buildCsv(exportHeader, exportableCodes)
                                    )
                                    context.startActivity(intent)
                                } catch (t: Throwable) {
                                    Log.e(TAG, "Can't export the code list", t)
                                    snackbarHostState.showSnackbar(
                                        "$exportFailed: ${t.message}"
                                    )
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
                                Card(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = code,
                                            maxLines = 4,
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
                                                contentDescription = stringResource(id = R.string.cd_delete_code)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (codeToDelete != null) {
                val previewCode = codeToDelete ?: ""
                AlertDialog(
                    onDismissRequest = { codeToDelete = null },
                    title = {
                        Text(deleteCodeTitle, style = MaterialTheme.typography.headlineSmall)
                    },
                    text = {
                        Text(
                            stringResource(
                                id = R.string.delete_code_confirm_with_value,
                                previewCode
                            ),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    },
                    confirmButton = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Button(onClick = { codeToDelete = null }) {
                                Text(deleteCancel)
                            }
                            Button(onClick = {
                                val code = codeToDelete!!
                                val isScanned = codeToDeleteIsScanned

                                scope.launch {
                                    try {
                                        // in the scanned list the code goes back to unscanned;
                                        // in the unscanned list it leaves the session for good
                                        if (isScanned) {
                                            repo.unmarkScanned(sessionId, code)
                                        } else {
                                            repo.deleteCode(sessionId, code)
                                        }

                                        val updated = repo.getById(sessionId)
                                        codeToDelete = null
                                        if (updated != null) {
                                            session = updated
                                            snackbarHostState.showSnackbar(deleteSuccess)
                                        } else {
                                            snackbarHostState.showSnackbar(deleteFailed)
                                        }
                                    } catch (t: Throwable) {
                                        Log.e(TAG, "Can't delete code from session $sessionId", t)
                                        codeToDelete = null
                                        snackbarHostState.showSnackbar("$deleteError: ${t.message}")
                                    }
                                }
                            }) {
                                Text(deleteConfirm)
                            }
                        }
                    },
                )
            }
        }
    }
}