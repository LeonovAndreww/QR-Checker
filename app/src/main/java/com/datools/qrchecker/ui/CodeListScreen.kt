package com.datools.qrchecker.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.datools.qrchecker.data.SessionRepository
import com.datools.qrchecker.model.SessionData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.res.stringResource
import com.datools.qrchecker.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CodesListScreen(
    navController: NavController,
    sessionId: String,
    type: String // "scanned" or "not_scanned"
) {
    val context = LocalContext.current
    val repo = remember { SessionRepository(context) }
    var session by remember { mutableStateOf<SessionData?>(null) }
    val scope = rememberCoroutineScope()

    // snackbar host
    val snackbarHostState = remember { SnackbarHostState() }

    // dialog state for delete confirmation
    var codeToDelete by remember { mutableStateOf<String?>(null) }
    var codeToDeleteIsScanned by remember { mutableStateOf(false) }

    LaunchedEffect(sessionId) {
        scope.launch(Dispatchers.IO) {
            session = try {
                repo.getById(sessionId)
            } catch (_: Throwable) {
                null
            }
        }
    }

    val titleScanned = stringResource(id = R.string.codes_title_scanned, session?.scannedCodes?.size ?: 0)
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (type == "scanned") titleScanned else titleNotScanned
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(id = R.string.cd_back)
                        )
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

            val codes: List<String> = if (type == "scanned") {
                session!!.scannedCodes
            } else {
                session!!.codes.filter { it !in session!!.scannedCodes }
            }

            if (codes.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = if (type == "scanned") noScannedText else noNotScannedText
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(items = codes, key = { it }) { code ->
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
                                        codeToDeleteIsScanned = (type == "scanned")
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

            if (codeToDelete != null) {
                val previewCode = codeToDelete ?: ""
                AlertDialog(
                    onDismissRequest = { codeToDelete = null },
                    title = {
                        Text(deleteCodeTitle, style = MaterialTheme.typography.headlineSmall)
                    },
                    text = {
                        Text(
                            stringResource(id = R.string.delete_code_confirm_with_value, previewCode),
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
                                        val updated = withContext(Dispatchers.IO) {
                                            val current =
                                                repo.getById(sessionId) ?: return@withContext null
                                            val newSession: SessionData = if (isScanned) {
                                                val newScanned =
                                                    current.scannedCodes.filter { it != code }
                                                        .toMutableList()
                                                current.copy(scannedCodes = newScanned)
                                            } else {
                                                val newCodes = current.codes.filter { it != code }
                                                    .toMutableList()
                                                val newScanned =
                                                    current.scannedCodes.filter { it in newCodes }
                                                        .toMutableList()
                                                current.copy(
                                                    codes = newCodes,
                                                    scannedCodes = newScanned
                                                )
                                            }
                                            repo.update(newSession)
                                            newSession
                                        }

                                        if (updated != null) {
                                            session = updated
                                            codeToDelete = null
                                            snackbarHostState.showSnackbar(deleteSuccess)
                                        } else {
                                            codeToDelete = null
                                            snackbarHostState.showSnackbar(deleteFailed)
                                        }
                                    } catch (t: Throwable) {
                                        codeToDelete = null
                                        snackbarHostState.showSnackbar("$deleteError: ${t.message}")
                                    }
                                }
                            }) {
                                Text(deleteConfirm)
                            }
                        }
                    },
                    dismissButton = { /* empty — used custom Cancel above */ }
                )
            }
        }
    }
}