package com.datools.qrchecker.ui

import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.datools.qrchecker.R
import com.datools.qrchecker.data.SessionRepository
import com.datools.qrchecker.util.SessionBackup
import kotlinx.coroutines.launch

private const val TAG = "QRChecker"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavController) {
    val context = LocalContext.current
    val repo = remember { SessionRepository(context) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var folderName by remember { mutableStateOf(SessionBackup.folderName(context)) }
    var enabled by remember { mutableStateOf(SessionBackup.isEnabled(context)) }
    var restoring by remember { mutableStateOf(false) }

    val title = stringResource(id = R.string.settings_title)
    val backupTitle = stringResource(id = R.string.backup_title)
    val backupWhy = stringResource(id = R.string.backup_why)
    val folderNone = stringResource(id = R.string.backup_folder_none)
    val chooseFolder = stringResource(id = R.string.backup_choose_folder)
    val changeFolder = stringResource(id = R.string.backup_change_folder)
    val forgetFolder = stringResource(id = R.string.backup_forget_folder)
    val autoLabel = stringResource(id = R.string.backup_auto)
    val restoreLabel = stringResource(id = R.string.backup_restore)
    val folderFailed = stringResource(id = R.string.backup_folder_failed)
    val restoreTemplate = stringResource(id = R.string.backup_restored)
    val restoreFailed = stringResource(id = R.string.backup_restore_failed)

    val pickFolder = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        if (SessionBackup.setFolder(context, uri)) {
            folderName = SessionBackup.folderName(context)
            enabled = SessionBackup.isEnabled(context)
        } else {
            scope.launch { snackbarHostState.showSnackbar(folderFailed) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(backupTitle, style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = backupWhy,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = folderName ?: folderNone,
                        style = MaterialTheme.typography.bodyLarge
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { pickFolder.launch(null) }) {
                            Text(if (folderName == null) chooseFolder else changeFolder)
                        }
                        if (folderName != null) {
                            TextButton(onClick = {
                                SessionBackup.clearFolder(context)
                                folderName = null
                                enabled = false
                            }) {
                                Text(forgetFolder)
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(autoLabel, style = MaterialTheme.typography.bodyLarge)
                        Switch(
                            checked = enabled,
                            // без папки переключателю нечего включать
                            enabled = folderName != null,
                            onCheckedChange = {
                                enabled = it
                                SessionBackup.setEnabled(context, it)
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedButton(
                        enabled = folderName != null && !restoring,
                        onClick = {
                            restoring = true
                            scope.launch {
                                try {
                                    val known = repo.existingIds()
                                    // восстанавливаются только те, которых на устройстве
                                    // нет: повторное восстановление ничего не затирает
                                    val missing =
                                        SessionBackup.readAll(context).filter { it.id !in known }
                                    for (session in missing) repo.insert(session)
                                    snackbarHostState.showSnackbar(
                                        restoreTemplate.format(missing.size)
                                    )
                                } catch (t: Throwable) {
                                    Log.e(TAG, "Can't restore from the backup folder", t)
                                    snackbarHostState.showSnackbar(restoreFailed)
                                } finally {
                                    restoring = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(restoreLabel)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
