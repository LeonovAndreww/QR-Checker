package com.datools.qrchecker.ui

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
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.datools.qrchecker.R
import com.datools.qrchecker.Screen
import com.datools.qrchecker.data.SessionRepository
import com.datools.qrchecker.util.DeleteConfirmation
import com.datools.qrchecker.ui.theme.accents
import com.datools.qrchecker.model.SessionSummary
import kotlinx.coroutines.launch


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(navController: NavController) {
    val context = LocalContext.current
    val repo = remember { SessionRepository(context) }
    val scope = rememberCoroutineScope()
    // summaries only: the list draws names, so pulling every code of every session would be waste
    val sessions by remember { repo.getSummariesFlow() }.collectAsState(initial = emptyList())
    var sessionToDelete by remember { mutableStateOf<SessionSummary?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val accents = MaterialTheme.accents
    val buttonHeight = 82.dp

    val fabCd = stringResource(id = R.string.cd_new_session)
    val titleText = stringResource(id = R.string.sessions_title)
    val editCd = stringResource(id = R.string.cd_edit)
    val deleteCd = stringResource(id = R.string.cd_delete)
    val deleteTitle = stringResource(id = R.string.delete_session_title)
    val deleteCancel = stringResource(id = R.string.delete_cancel)
    val deleteConfirm = stringResource(id = R.string.delete_confirm)
    val settingsCd = stringResource(id = R.string.cd_settings)
    val progressTemplate = stringResource(id = R.string.sessions_progress)
    val emptyText = stringResource(id = R.string.sessions_empty)

    Scaffold(
        topBar = {
            // Та же панель, что на остальных экранах: раньше здесь был крупный текст, а
            // кнопки висели по его краям, и главный экран выпадал из общего строя
            CenterAlignedTopAppBar(
                title = { Text(titleText) },
                navigationIcon = {
                    IconButton(onClick = { navController.navigate(Screen.Settings.route) }) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_settings),
                            contentDescription = settingsCd
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { navController.navigate(Screen.CreateSession.route) },
                containerColor = accents.newSession.container,
                contentColor = accents.newSession.content
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
            if (sessions.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = emptyText,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(32.dp)
                    )
                }
                return@Column
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
                                containerColor = accents.edit.container,
                                contentColor = accents.edit.content
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
                            // счёт уже считался запросом для списка, но на экран не
                            // выводился: приложение открывалось и не отвечало на
                            // единственный вопрос, ради которого его открывают
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = session.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = progressTemplate.format(
                                        session.scanned,
                                        session.total
                                    ),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }

                        FilledIconButton(
                            onClick = {
                                sessionToDelete = session
                            },
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = accents.delete.container,
                                contentColor = accents.delete.content
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
                        // настройка «не спрашивать» принадлежала этой сессии и вместе с
                        // ней и уходит, иначе новая сессия унаследует чужой выбор
                        DeleteConfirmation.forget(context, session.id)
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
}

