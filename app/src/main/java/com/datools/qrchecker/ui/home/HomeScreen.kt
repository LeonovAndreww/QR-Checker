package com.datools.qrchecker.ui.home

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.lazy.rememberLazyListState
import com.datools.qrchecker.util.AppSettings
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.datools.qrchecker.navigateOnce
import com.datools.qrchecker.R
import com.datools.qrchecker.Screen
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.datools.qrchecker.ui.ScrollbarBox
import com.datools.qrchecker.ui.SwipeAction
import com.datools.qrchecker.ui.SwipeActionRow
import com.datools.qrchecker.util.Outcome
import com.datools.qrchecker.util.rememberFeedback
import com.datools.qrchecker.util.formatTimeAgo
import com.datools.qrchecker.ui.theme.accents
import com.datools.qrchecker.model.SessionSummary
import kotlinx.coroutines.launch


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(navController: NavController) {
    val context = LocalContext.current
    val viewModel: HomeViewModel = hiltViewModel()
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    var sessionToDelete by remember { mutableStateOf<SessionSummary?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val accents = MaterialTheme.accents
    val feel = rememberFeedback()
    val scrollbarShown by AppSettings.scrollbarState(context)
    val listState = rememberLazyListState()
    val buttonHeight = 82.dp
    val groupCorner = 12.dp
    val groupStartShape = RoundedCornerShape(
        topStart = groupCorner, bottomStart = groupCorner, topEnd = 0.dp, bottomEnd = 0.dp
    )
    val groupEndShape = RoundedCornerShape(
        topStart = 0.dp, bottomStart = 0.dp, topEnd = groupCorner, bottomEnd = groupCorner
    )

    val fabCd = stringResource(id = R.string.cd_new_session)
    val titleText = stringResource(id = R.string.sessions_title)
    val editCd = stringResource(id = R.string.cd_edit)
    val deleteCd = stringResource(id = R.string.cd_delete)
    val deleteTitle = stringResource(id = R.string.delete_session_title)
    val deleteCancel = stringResource(id = R.string.delete_cancel)
    val deleteConfirm = stringResource(id = R.string.delete_confirm)
    val settingsCd = stringResource(id = R.string.cd_settings)
    val progressTemplate = stringResource(id = R.string.sessions_progress)
    val collectedTemplate = stringResource(id = R.string.sessions_collected)
    val emptyText = stringResource(id = R.string.sessions_empty)

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(titleText) },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateOnce(Screen.Settings.route) }) {
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
                onClick = { navController.navigateOnce(Screen.CreateSession.route) },
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
            // the database has not answered yet: an empty screen claims nothing
            val loaded = sessions ?: return@Column

            if (loaded.isEmpty()) {
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

            // Keyed rows keep the scroll position on the item that was first, so a session
            // moved to the top would land above the edge. A user at the top stays there.
            val firstId = loaded.firstOrNull()?.id
            LaunchedEffect(firstId) {
                if (listState.firstVisibleItemIndex <= 1) listState.scrollToItem(0)
            }

            ScrollbarBox(state = listState, enabled = scrollbarShown) {
                LazyColumn(
                    state = listState,
                    // top gap, or the first session sticks to the bar
                    contentPadding = PaddingValues(top = 12.dp, bottom = 80.dp)
                ) {
                    items(loaded, key = { it.id }) { session ->
                        // the same gestures as code lists: right edits, left deletes; the
                        // buttons stay, since a gesture has to be known first
                        SwipeActionRow(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            onThresholdCrossed = { feel(Outcome.THRESHOLD) },
                            start = SwipeAction(
                                background = accents.edit.container,
                                onTrigger = {
                                    feel(Outcome.ACTION)
                                    navController.navigateOnce(Screen.EditSession.createRoute(session.id))
                                },
                                icon = {
                                    Icon(
                                        Icons.Default.Edit,
                                        contentDescription = editCd,
                                        tint = accents.edit.content
                                    )
                                }
                            ),
                            end = SwipeAction(
                                background = accents.delete.container,
                                onTrigger = { sessionToDelete = session },
                                icon = {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = deleteCd,
                                        tint = accents.delete.content
                                    )
                                }
                            )
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                // the session's buttons form one group: rounded outside only
                                horizontalArrangement = Arrangement.spacedBy(0.dp)
                            ) {
                                FilledIconButton(
                                    onClick = {
                                        navController.navigateOnce(Screen.EditSession.createRoute(session.id))
                                    },
                                    colors = IconButtonDefaults.filledIconButtonColors(
                                        containerColor = accents.edit.container,
                                        contentColor = accents.edit.content
                                    ),
                                    modifier = Modifier.height(buttonHeight),
                                    shape = groupStartShape
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = editCd)
                                }
                                Button(
                                    onClick = { navController.navigateOnce(Screen.Scan.createRoute(session.id)) },
                                    modifier = Modifier
                                        .height(buttonHeight)
                                        .weight(1f),
                                    shape = RectangleShape
                                ) {
                                    // the progress is what the app is opened for
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = session.name,
                                            style = MaterialTheme.typography.titleMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = buildString {
                                                // "12 of 340" would be false: a collecting
                                                // session has no total
                                                append(
                                                    if (session.collecting) {
                                                        collectedTemplate.format(session.scanned)
                                                    } else {
                                                        progressTemplate.format(
                                                            session.scanned,
                                                            session.total
                                                        )
                                                    }
                                                )
                                                // no time recorded: the line is just shorter
                                                val at = maxOf(session.openedAt, session.createdAt)
                                                if (at > 0) {
                                                    append("  ·  ")
                                                    append(formatTimeAgo(context, at))
                                                }
                                            },
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
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
                                    modifier = Modifier.height(buttonHeight),
                                    shape = groupEndShape
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = deleteCd)
                                }

                            }
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
                    viewModel.delete(session.id)
                    sessionToDelete = null
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

