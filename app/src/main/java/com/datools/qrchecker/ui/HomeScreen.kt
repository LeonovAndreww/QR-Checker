package com.datools.qrchecker.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.datools.qrchecker.R
import com.datools.qrchecker.Screen
import com.datools.qrchecker.data.SessionRepository
import com.datools.qrchecker.model.SessionData
import com.datools.qrchecker.model.SessionSummary
import com.datools.qrchecker.util.SessionFileException
import com.datools.qrchecker.util.readSessionFile
import com.datools.qrchecker.util.readTextFromUri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

private const val TAG = "QRChecker"


@Composable
fun HomeScreen(navController: NavController) {
    val context = LocalContext.current
    val repo = remember { SessionRepository(context) }
    val scope = rememberCoroutineScope()
    // summaries only: the list draws names, so pulling every code of every session would be waste
    val sessions by remember { repo.getSummariesFlow() }.collectAsState(initial = emptyList())
    var sessionToDelete by remember { mutableStateOf<SessionSummary?>(null) }
    // открытый файл, для которого уже нашлась сессия с тем же набором кодов
    var conflict by remember { mutableStateOf<Conflict?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val buttonHeight = 82.dp

    val fabCd = stringResource(id = R.string.cd_new_session)
    val titleText = stringResource(id = R.string.sessions_title)
    val editCd = stringResource(id = R.string.cd_edit)
    val deleteCd = stringResource(id = R.string.cd_delete)
    val deleteTitle = stringResource(id = R.string.delete_session_title)
    val deleteCancel = stringResource(id = R.string.delete_cancel)
    val deleteConfirm = stringResource(id = R.string.delete_confirm)
    val openSessionCd = stringResource(id = R.string.cd_open_session)
    val fileFailed = stringResource(id = R.string.session_file_failed)
    val existsTitle = stringResource(id = R.string.session_exists_title)
    val mergeText = stringResource(id = R.string.session_merge)
    val addNewText = stringResource(id = R.string.session_add_new)
    // шаблоны берутся в композиции, числа подставляются уже в корутине: обращаться к
    // ресурсам через LocalContext вне composable запрещено (LocalContextGetResourceValueCall)
    val openedTemplate = stringResource(id = R.string.session_opened)
    val mergedTemplate = stringResource(id = R.string.session_merged)

    // тип у файла сессии свой, система его не знает и отдаёт как octet-stream, поэтому
    // фильтровать по mime бесполезно - выбор ограничен ничем, разбор решает всё сам
    val openSession = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                val imported = withContext(Dispatchers.IO) {
                    readSessionFile(readTextFromUri(context, uri)).session
                }
                val existing = repo.findWithSameCodes(imported.codes)
                if (existing != null) {
                    conflict = Conflict(imported = imported, existing = existing)
                } else {
                    repo.insert(imported.copy(id = UUID.randomUUID().toString()))
                    snackbarHostState.showSnackbar(
                        openedTemplate.format(
                            imported.codes.size,
                            imported.scannedCodes.size
                        )
                    )
                }
            } catch (e: SessionFileException) {
                snackbarHostState.showSnackbar(e.message ?: fileFailed)
            } catch (t: Throwable) {
                Log.e(TAG, "Can't open a session file", t)
                snackbarHostState.showSnackbar(fileFailed)
            }
        }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { navController.navigate(Screen.CreateSession.route) },
                containerColor = Color.Yellow,
                contentColor = Color.Black
            ) {
                Icon(Icons.Default.Add, contentDescription = fabCd)
            }
        },
        floatingActionButtonPosition = FabPosition.Center,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier.padding(innerPadding)
        )
        {
            Box(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = titleText,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    style = MaterialTheme.typography.displaySmall,
                    textAlign = TextAlign.Center
                )
                IconButton(
                    onClick = { openSession.launch(arrayOf("*/*")) },
                    modifier = Modifier.align(Alignment.CenterEnd)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_file_open),
                        contentDescription = openSessionCd
                    )
                }
            }
            LazyColumn(
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                items(sessions) { session ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        FilledIconButton(
                            onClick = {
                                navController.navigate(Screen.EditSession.createRoute(session.id))
                            },
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = Color.DarkGray,
                                contentColor = MaterialTheme.colorScheme.surface
                            ),
                            modifier = Modifier
                                .height(buttonHeight),
                            shape = MaterialTheme.shapes.small
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = editCd)
                        }
                        Button(
                            onClick = { navController.navigate(Screen.Scan.createRoute(session.id)) },
                            modifier = Modifier
                                .height(buttonHeight)
                                .weight(1f),
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text(
                                text = session.name,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }

                        FilledIconButton(
                            onClick = {
                                sessionToDelete = session
                            },
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = Color.Red,
                                contentColor = MaterialTheme.colorScheme.surface
                            ),
                            modifier = Modifier
                                .height(buttonHeight),
                            shape = MaterialTheme.shapes.small
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = deleteCd)
                        }

                    }

                }
            }
        }
    }

    sessionToDelete?.let { session ->
        AlertDialog(
            onDismissRequest = { sessionToDelete = null },
            title = {
                Text(deleteTitle, style = MaterialTheme.typography.headlineSmall)
            },
            text = {
                Text(
                    text = stringResource(
                        id = R.string.delete_session_confirm,
                        formatArgs = arrayOf(session.name)
                    ),
                    style = MaterialTheme.typography.bodyLarge
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        repo.delete(session.id)
                        sessionToDelete = null
                    }
                }) {
                    Text(deleteConfirm)
                }
            },
            dismissButton = {
                TextButton(onClick = { sessionToDelete = null }) {
                    Text(deleteCancel)
                }
            }
        )
    }

    conflict?.let { pending ->
        AlertDialog(
            onDismissRequest = { conflict = null },
            title = { Text(existsTitle, style = MaterialTheme.typography.headlineSmall) },
            text = {
                Text(
                    text = stringResource(
                        id = R.string.session_exists_text,
                        pending.existing.name
                    ),
                    style = MaterialTheme.typography.bodyLarge
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val target = pending.existing
                    val marks = pending.imported.scannedCodes
                    conflict = null
                    scope.launch {
                        val added = repo.mergeScanned(target.id, marks)
                        snackbarHostState.showSnackbar(mergedTemplate.format(added))
                    }
                }) {
                    Text(mergeText)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    val imported = pending.imported
                    conflict = null
                    scope.launch {
                        repo.insert(imported.copy(id = UUID.randomUUID().toString()))
                        snackbarHostState.showSnackbar(
                            openedTemplate.format(
                                imported.codes.size,
                                imported.scannedCodes.size
                            )
                        )
                    }
                }) {
                    Text(addNewText)
                }
            }
        )
    }
}

/** Открытый файл и сессия с тем же набором кодов, которая уже есть на устройстве. */
private data class Conflict(
    val imported: SessionData,
    val existing: SessionData
)
