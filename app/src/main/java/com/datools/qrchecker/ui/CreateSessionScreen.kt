package com.datools.qrchecker.ui

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.TextButton
import com.datools.qrchecker.viewmodel.ParsedFile
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.datools.qrchecker.navigateOnce
import com.datools.qrchecker.popBackStackOnce
import com.datools.qrchecker.R
import com.datools.qrchecker.Screen
import com.datools.qrchecker.util.getFileNameFromUri
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import com.datools.qrchecker.viewmodel.ScanViewModel

private const val TAG = "QRChecker"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateSessionScreen(navController: NavController) {
    val context = LocalContext.current
    var sessionName by rememberSaveable { mutableStateOf("") }
    var selectedNames by rememberSaveable { mutableStateOf(listOf<String>()) }
    // имя из файла сессии подставляется один раз: дальше человек волен его переписать
    var nameTakenFromFile by rememberSaveable { mutableStateOf(false) }

    // getting ViewModel
    val scanViewModel: ScanViewModel = viewModel()

    val documentPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
        onResult = { uris: List<Uri> ->
            if (uris.isNotEmpty()) {
                val files = uris.map { uri ->
                // the uri is kept across process death via rememberSaveable, so the read
                // grant has to outlive this activity instance as well
                    try {
                        context.contentResolver.takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    } catch (e: SecurityException) {
                        Log.w(TAG, "Could not persist read access to $uri", e)
                    }
                    uri to getFileNameFromUri(uri, context)
                }
                selectedNames = files.map { it.second }
                nameTakenFromFile = false
                scanViewModel.clearError()
                scanViewModel.clearParsed()
                // разбор начинается здесь, а не по кнопке: ждать нажатия незачем
                scanViewModel.parseSelectedFiles(context, files)
            }
        }
    )

    val isLoading by scanViewModel.isLoading
    val progress by scanViewModel.progress
    val parsed by scanViewModel.parsed
    val createdSessionId by scanViewModel.createdSessionId
    val conflict by scanViewModel.conflict
    val mergedMarks by scanViewModel.mergedMarks
    val errorMessage by scanViewModel.errorMessage

    // у файла сессии имя уже есть - подставляем его, чтобы не заставлять придумывать
    LaunchedEffect(parsed) {
        val session = (parsed as? ParsedFile.Session)?.session
        if (session != null && !nameTakenFromFile && session.name.isNotBlank()) {
            sessionName = session.name
            nameTakenFromFile = true
        }
    }

    // Переход ждёт, пока прочитают, сколько отметок перенеслось: иначе экран уезжает
    // на сканирование, и единственный ответ на «объединить» человек не видит вовсе.
    LaunchedEffect(createdSessionId, mergedMarks) {
        val id = createdSessionId ?: return@LaunchedEffect
        if (mergedMarks != null) return@LaunchedEffect
        navController.navigateOnce(Screen.Scan.createRoute(id))
        scanViewModel.clearCreatedSessionId()
    }

    val titleText = stringResource(id = R.string.setup_session_title)
    val backCd = stringResource(id = R.string.cd_back)
    val nameLabel = stringResource(id = R.string.session_name_label)
    val addFileLabel = stringResource(id = R.string.add_files_label)
    val selectedFilesTemplate = stringResource(id = R.string.selected_files)
    val fileProgressTemplate = stringResource(id = R.string.parsing_file)
    val pageProgressTemplate = stringResource(id = R.string.parsing_page)
    val codesSummaryTemplate = stringResource(id = R.string.parsed_codes_summary)
    val sourceLineTemplate = stringResource(id = R.string.parsed_source_line)
    val continueText = stringResource(id = R.string.continue_button)
    val parsingText = stringResource(id = R.string.parsing_pdf)
    val cancelParsingText = stringResource(id = R.string.parsing_cancel)
    val sessionSummaryTemplate = stringResource(id = R.string.parsed_session_summary)
    val existsTitle = stringResource(id = R.string.session_exists_title)
    val mergedTitle = stringResource(id = R.string.session_merged_title)
    val mergeText = stringResource(id = R.string.session_merge)
    val addNewText = stringResource(id = R.string.session_add_new)

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
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(16.dp))

            TextField(
                value = sessionName,
                onValueChange = { input ->
                    val filtered = input.filterNot { it == '\n' }
                    if (!(sessionName.isEmpty() && filtered.startsWith(" "))) {
                        sessionName = filtered
                    }
                },
                label = { Text(nameLabel) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .height(72.dp)
            )

            Spacer(Modifier.height(20.dp))

            Button(
                onClick = { documentPicker.launch(arrayOf("*/*")) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .height(72.dp),
                shape = MaterialTheme.shapes.small
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_file_open),
                        contentDescription = null
                    )
                    Text(
                        text = when (selectedNames.size) {
                            0 -> addFileLabel
                            1 -> selectedNames.first()
                            else -> selectedFilesTemplate.format(selectedNames.size)
                        },
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            if (isLoading && progress != null) {
                val p = progress!!
                Spacer(Modifier.height(12.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (p.pageCount > 0) {
                        LinearProgressIndicator(
                            progress = { p.page.toFloat() / p.pageCount },
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = fileProgressTemplate.format(
                            p.fileIndex + 1,
                            p.fileCount,
                            p.fileName
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (p.pageCount > 0) {
                        Text(
                            text = pageProgressTemplate.format(p.page + 1, p.pageCount),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = { scanViewModel.cancelParsing() }) {
                        Text(cancelParsingText)
                    }
                }
            }

            parsed?.let { source ->
                Spacer(Modifier.height(12.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    when (source) {
                        is ParsedFile.Codes -> {
                            Text(
                                text = codesSummaryTemplate.format(source.codes.size),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            // видно, какой из выбранных файлов приехал пустым: иначе
                            // «нашлось 200» ничего не говорит о том, что один из трёх
                            // документов не прочитался вовсе
                            if (source.sources.size > 1) {
                                for (item in source.sources) {
                                    Text(
                                        text = sourceLineTemplate.format(item.name, item.codes),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        is ParsedFile.Session -> Text(
                            text = sessionSummaryTemplate.format(
                                source.session.codes.size,
                                source.session.scannedCodes.size
                            ),
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            errorMessage?.let { message ->
                Spacer(Modifier.height(12.dp))
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = { scanViewModel.createSession(context, sessionName) },
                enabled = (sessionName.isNotBlank() && parsed != null && !isLoading),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(vertical = 14.dp)
                    .height(72.dp),
                shape = MaterialTheme.shapes.small
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(parsingText)
                } else {
                    Text(continueText, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }

    mergedMarks?.let { count ->
        AlertDialog(
            onDismissRequest = { scanViewModel.clearMergedMarks() },
            title = { Text(mergedTitle) },
            text = { Text(stringResource(R.string.session_merged, count)) },
            confirmButton = {
                TextButton(onClick = { scanViewModel.clearMergedMarks() }) {
                    Text(continueText)
                }
            }
        )
    }

    conflict?.let { existing ->
        AlertDialog(
            onDismissRequest = { scanViewModel.dismissConflict() },
            title = { Text(existsTitle) },
            text = { Text(stringResource(R.string.session_exists_text, existing.name)) },
            confirmButton = {
                TextButton(onClick = { scanViewModel.mergeIntoExisting(context) }) {
                    Text(mergeText)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    scanViewModel.dismissConflict()
                    scanViewModel.createSession(context, sessionName, force = true)
                }) {
                    Text(addNewText)
                }
            }
        )
    }
}
