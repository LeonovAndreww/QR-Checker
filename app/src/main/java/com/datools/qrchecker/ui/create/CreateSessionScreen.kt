package com.datools.qrchecker.ui.create

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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalWindowInfo
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
import com.datools.qrchecker.util.countFormats
import com.datools.qrchecker.util.getFileNameFromUri
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.datools.qrchecker.ui.FormatFilter
import com.datools.qrchecker.ui.FullHeightColumn
import com.datools.qrchecker.ui.asString

private const val TAG = "QRChecker"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateSessionScreen(navController: NavController) {
    val context = LocalContext.current
    var sessionName by rememberSaveable { mutableStateOf("") }
    var selectedNames by rememberSaveable { mutableStateOf(listOf<String>()) }
    // a session file's name is filled in once; after that the user may change it
    var nameTakenFromFile by rememberSaveable { mutableStateOf(false) }

    // getting ViewModel
    val viewModel: CreateSessionViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val documentPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
        onResult = { uris: List<Uri> ->
            if (uris.isNotEmpty()) {
                // No persistable permission is taken: the files are read right away, and
                // the picker's grant covers that. Persisted grants would only pile up
                // against the app's limit.
                val files = uris.map { uri -> uri to getFileNameFromUri(uri, context) }
                selectedNames = files.map { it.second }
                nameTakenFromFile = false
                // The name defaults to the first file's name without extension: a batch is
                // already named by its waybill. Whatever the user typed is kept.
                if (sessionName.isBlank()) {
                    sessionName = files.firstOrNull()?.second.orEmpty()
                        .substringBeforeLast('.')
                        .trim()
                }
                viewModel.clearError()
                viewModel.clearParsed()
                // reading starts now, not on a button
                viewModel.parseFiles(files)
            }
        }
    )

    val isLoading = state.isLoading
    val isParsing = state.isParsing
    val progress = state.progress
    val parsed = state.parsed
    val createdSessionId = state.createdSessionId
    val conflict = state.conflict
    val mergedMarks = state.mergedMarks

    // Which code kinds go into the session, reset on every new parse. Everything found
    // is ticked, so "found N codes" matches what gets saved unless the user unticks a kind.
    val formatCounts = remember(parsed) {
        (parsed as? ParsedFile.Codes)?.let { countFormats(it.codes, it.formats) }.orEmpty()
    }
    var keptFormats by remember(parsed) {
        mutableStateOf(formatCounts.map { it.first }.toSet())
    }
    val errorMessage = state.error?.asString()

    // a session file carries its own name
    LaunchedEffect(parsed) {
        val session = (parsed as? ParsedFile.Session)?.session
        if (session != null && !nameTakenFromFile && session.name.isNotBlank()) {
            sessionName = session.name
            nameTakenFromFile = true
        }
    }

    // Navigation waits until the merge result has been seen, or the only answer to
    // "merge" would flash by. It also waits for the screen to be active again: a session
    // created while the app was in the background could not navigate, and clearing the id
    // anyway left the user on the create screen in front of a session that exists.
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val resumed = lifecycleState == Lifecycle.State.RESUMED
    LaunchedEffect(createdSessionId, mergedMarks, resumed) {
        val id = createdSessionId ?: return@LaunchedEffect
        if (mergedMarks != null || !resumed) return@LaunchedEffect
        if (navController.navigateOnce(Screen.Scan.createRoute(id))) {
            viewModel.clearCreatedSessionId()
        }
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
    val untitledName = stringResource(id = R.string.untitled_session)
    val stillReadingText = stringResource(id = R.string.file_still_reading)
    val noCodesText = stringResource(id = R.string.error_no_codes_in_files)
    val parsingText = stringResource(id = R.string.parsing_pdf)
    val cancelParsingText = stringResource(id = R.string.parsing_cancel)
    val sessionSummaryTemplate = stringResource(id = R.string.parsed_session_summary)
    val existsTitle = stringResource(id = R.string.session_exists_title)
    val mergedTitle = stringResource(id = R.string.session_merged_title)
    val cancelText = stringResource(id = R.string.delete_cancel)
    val mergeText = stringResource(id = R.string.session_merge)
    val addNewText = stringResource(id = R.string.session_add_new)

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val nameFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    // what blocks creation, asked one at a time on "Continue"
    var askName by remember { mutableStateOf(false) }
    var askNoFile by remember { mutableStateOf<String?>(null) }
    // the name the session is created with; "create new" in the conflict dialog uses it
    // too, while the text field stays empty after "Untitled"
    var chosenName by remember { mutableStateOf("") }
    // focus is requested after the dialog has closed and returned focus to the screen
    var focusName by remember { mutableStateOf(false) }
    val windowInfo = LocalWindowInfo.current
    LaunchedEffect(focusName) {
        if (!focusName) return@LaunchedEffect
        snapshotFlow { windowInfo.isWindowFocused }.first { it }
        nameFocus.requestFocus()
        keyboard?.show()
        focusName = false
    }

    fun say(text: String) {
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(text)
        }
    }

    // The button stays enabled: a disabled one does not say what it is missing.
    fun proceed(name: String) {
        when {
            isParsing -> say(stillReadingText)
            // already saving
            isLoading -> Unit
            parsed != null -> {
                chosenName = name
                viewModel.createSession(name, keepFormats = keptFormats)
            }
            // a file was picked but gave no codes, which is not "no file"
            selectedNames.isNotEmpty() -> say(errorMessage ?: noCodesText)
            else -> askNoFile = name
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
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
        FullHeightColumn(modifier = Modifier.padding(innerPadding)) {
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
                    .focusRequester(nameFocus)
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
                val p = progress
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
                    TextButton(onClick = {
                        viewModel.cancelParsing()
                        // a cancelled pick is no file
                        selectedNames = emptyList()
                    }) {
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
                            // shows which picked file came out empty; "found 200" alone
                            // would hide that one of three documents was unreadable
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

                            FormatFilter(formatCounts, keptFormats) { keptFormats = it }
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
                onClick = {
                    if (sessionName.isBlank() && !isLoading) askName = true
                    else proceed(sessionName.trim())
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(vertical = 14.dp)
                    .height(72.dp),
                shape = MaterialTheme.shapes.small
            ) {
                if (isParsing) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(parsingText)
                } else if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text(continueText, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }

    if (askName) {
        AlertDialog(
            onDismissRequest = { askName = false },
            title = { Text(stringResource(id = R.string.name_missing_title)) },
            confirmButton = {
                // createSession picks "Untitled", numbered if taken
                TextButton(onClick = {
                    askName = false
                    proceed(untitledName)
                }) {
                    Text(stringResource(id = R.string.name_use_untitled, untitledName))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    askName = false
                    focusName = true
                }) {
                    Text(stringResource(id = R.string.name_enter))
                }
            }
        )
    }

    askNoFile?.let { name ->
        AlertDialog(
            onDismissRequest = { askNoFile = null },
            title = { Text(stringResource(id = R.string.no_file_title)) },
            text = { Text(stringResource(id = R.string.no_file_text)) },
            confirmButton = {
                TextButton(onClick = {
                    askNoFile = null
                    viewModel.createCollectingSession(name)
                }) {
                    Text(stringResource(id = R.string.no_file_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { askNoFile = null }) {
                    Text(cancelText)
                }
            }
        )
    }

    mergedMarks?.let { count ->
        AlertDialog(
            onDismissRequest = { viewModel.clearMergedMarks() },
            title = { Text(mergedTitle) },
            text = { Text(stringResource(R.string.session_merged, count)) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearMergedMarks() }) {
                    Text(continueText)
                }
            }
        )
    }

    conflict?.let { existing ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissConflict() },
            title = { Text(existsTitle) },
            text = { Text(stringResource(R.string.session_exists_text, existing.name)) },
            confirmButton = {
                TextButton(onClick = { viewModel.mergeIntoExisting() }) {
                    Text(mergeText)
                }
            },
            dismissButton = {
                // three ways out: continue the old session, create a second one, or cancel
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { viewModel.dismissConflict() }) {
                        Text(cancelText)
                    }
                    TextButton(onClick = {
                        viewModel.dismissConflict()
                        viewModel.createSession(
                            chosenName,
                            force = true,
                            keepFormats = keptFormats
                        )
                    }) {
                        Text(addNewText)
                    }
                }
            }
        )
    }
}
