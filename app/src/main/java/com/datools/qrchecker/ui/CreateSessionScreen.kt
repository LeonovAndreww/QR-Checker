package com.datools.qrchecker.ui

import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import com.datools.qrchecker.R
import com.datools.qrchecker.Screen
import com.datools.qrchecker.util.getFileNameFromUri
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import com.datools.qrchecker.viewmodel.ScanViewModel

private const val TAG = "QRChecker"

@Composable
fun CreateSessionScreen(navController: NavController) {
    val context = LocalContext.current
    var sessionName by rememberSaveable { mutableStateOf("") }
    var selectedFileName by rememberSaveable { mutableStateOf("") }
    // имя из файла сессии подставляется один раз: дальше человек волен его переписать
    var nameTakenFromFile by rememberSaveable { mutableStateOf(false) }

    // getting ViewModel
    val scanViewModel: ScanViewModel = viewModel()

    val documentPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri: Uri? ->
            if (uri != null) {
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
                selectedFileName = getFileNameFromUri(uri, context)
                nameTakenFromFile = false
                scanViewModel.clearError()
                scanViewModel.clearParsed()
                // разбор начинается здесь, а не по кнопке: ждать нажатия незачем
                scanViewModel.parseSelectedFile(context, uri)
            }
        }
    )

    val isLoading by scanViewModel.isLoading
    val progress by scanViewModel.progress
    val parsed by scanViewModel.parsed
    val createdSessionId by scanViewModel.createdSessionId
    val conflict by scanViewModel.conflict
    val errorMessage by scanViewModel.errorMessage

    // у файла сессии имя уже есть - подставляем его, чтобы не заставлять придумывать
    LaunchedEffect(parsed) {
        val session = (parsed as? ParsedFile.Session)?.session
        if (session != null && !nameTakenFromFile && session.name.isNotBlank()) {
            sessionName = session.name
            nameTakenFromFile = true
        }
    }

    LaunchedEffect(createdSessionId) {
        createdSessionId?.let { id ->
            navController.navigate(Screen.Scan.createRoute(id))
            scanViewModel.clearCreatedSessionId()
        }
    }

    val titleText = stringResource(id = R.string.setup_session_title)
    val nameLabel = stringResource(id = R.string.session_name_label)
    val addFileLabel = stringResource(id = R.string.add_file_label)
    val continueText = stringResource(id = R.string.continue_button)
    val parsingText = stringResource(id = R.string.parsing_pdf)
    val progressTemplate = stringResource(id = R.string.parsing_progress)
    val cancelParsingText = stringResource(id = R.string.parsing_cancel)
    val pdfSummaryTemplate = stringResource(id = R.string.parsed_pdf_summary)
    val sessionSummaryTemplate = stringResource(id = R.string.parsed_session_summary)
    val existsTitle = stringResource(id = R.string.session_exists_title)
    val mergeText = stringResource(id = R.string.session_merge)
    val addNewText = stringResource(id = R.string.session_add_new)
    val pdfIconDesc = stringResource(id = R.string.cd_pdf_icon)

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = titleText,
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.displaySmall,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(20.dp))

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
                    Image(
                        painter = painterResource(R.drawable.pdf_icon),
                        contentDescription = pdfIconDesc
                    )
                    Text(
                        text = selectedFileName.ifEmpty { addFileLabel },
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
                    LinearProgressIndicator(
                        progress = { if (p.total > 0) p.done.toFloat() / p.total else 0f },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = progressTemplate.format(p.done + 1, p.total),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    TextButton(onClick = { scanViewModel.cancelParsing() }) {
                        Text(cancelParsingText)
                    }
                }
            }

            parsed?.let { source ->
                Spacer(Modifier.height(12.dp))
                Text(
                    text = when (source) {
                        is ParsedFile.Pdf ->
                            pdfSummaryTemplate.format(source.codes.size, source.pageCount)
                        is ParsedFile.Session -> sessionSummaryTemplate.format(
                            source.session.codes.size,
                            source.session.scannedCodes.size
                        )
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                )
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
