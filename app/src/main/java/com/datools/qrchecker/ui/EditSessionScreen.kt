package com.datools.qrchecker.ui

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import com.datools.qrchecker.R
import com.datools.qrchecker.data.SessionRepository
import com.datools.qrchecker.model.SessionData
import com.datools.qrchecker.util.getFileNameFromUri
import com.datools.qrchecker.util.parsePdfForQRCodes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditSessionScreen(
    navController: NavController,
    sessionId: String
) {
    val context = LocalContext.current
    val repo = remember { SessionRepository(context) }
    val scope = rememberCoroutineScope()

    var original by remember { mutableStateOf<SessionData?>(null) }

    // form states
    var name by remember { mutableStateOf("") }

    // file picker states
    var selectedPdfUriString by remember { mutableStateOf<String?>(null) }
    var selectedPdfName by remember { mutableStateOf("") }

    // parsed codes from newly selected PDF (null = nothing selected, empty list = parsed but no codes)
    var parsedCodes by remember { mutableStateOf<List<String>?>(null) }

    // loading / error
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // confirmation dialog when replacing codes
    var showReplaceConfirm by remember { mutableStateOf(false) }

    val documentPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        selectedPdfUriString = uri.toString()
        selectedPdfName = getFileNameFromUri(uri, context)
        // parse in background — здесь НЕ используем stringResource; используем context.getString в случае ошибок
        scope.launch {
            isLoading = true
            errorMessage = null
            try {
                val codes = withContext(Dispatchers.IO) {
                    parsePdfForQRCodes(context, uri, 3)
                }
                parsedCodes = codes
            } catch (t: Throwable) {
                parsedCodes = emptyList()
                errorMessage = context.getString(R.string.error_parsing_pdf, t.message ?: "")
                Log.e("EditSession", "parse error", t)
            } finally {
                isLoading = false
            }
        }
    }

    // load session
    LaunchedEffect(sessionId) {
        isLoading = true
        try {
            val s = repo.getById(sessionId)
            original = s
            if (s != null) {
                name = s.name
            } else {
                navController.popBackStack()
            }
        } catch (t: Throwable) {
            errorMessage = context.getString(R.string.error_loading_session, t.message ?: "")
        } finally {
            isLoading = false
        }
    }

    // strings for UI (safe to call stringResource here)
    val titleText = stringResource(id = R.string.setup_session_title)
    val nameLabel = stringResource(id = R.string.session_name_label)
    val selectPdfLabel = stringResource(id = R.string.select_pdf_label)
    val pdfIconDesc = stringResource(id = R.string.cd_pdf_icon)
    val cancelText = stringResource(id = R.string.delete_cancel)
    val saveText = stringResource(id = R.string.save_button)
    val replacingTitle = stringResource(id = R.string.replace_codes_title)
    val replaceAndSaveText = stringResource(id = R.string.replace_and_save)
    val parsingText = stringResource(id = R.string.parsing_pdf)

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = titleText,
                style = MaterialTheme.typography.displaySmall,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            TextField(
                value = name,
                onValueChange = { v -> name = v.filterNot { it == '\n' } },
                label = { Text(nameLabel) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = { documentPicker.launch(arrayOf("application/pdf")) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp),
                shape = MaterialTheme.shapes.small
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.pdf_icon),
                        contentDescription = pdfIconDesc
                    )

                    Text(
                        text = selectedPdfName.ifEmpty { selectPdfLabel },
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        textAlign = TextAlign.End,
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 12.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Counts row: current codes (original) and selected-file codes (parsed)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val origCount = original?.codes?.size ?: 0
                val selectedCountText = when {
                    parsedCodes != null -> parsedCodes!!.size.toString()
                    selectedPdfName.isNotEmpty() && isLoading -> parsingText
                    selectedPdfName.isNotEmpty() -> "..."
                    else -> "—"
                }

                Text(
                    text = context.getString(R.string.was_qr_count, origCount),
                    style = MaterialTheme.typography.bodyLarge
                )

                Text(
                    text = context.getString(R.string.will_be_qr_count, selectedCountText),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.End
                )
            }

            if (isLoading) {
                Spacer(modifier = Modifier.height(12.dp))
                CircularProgressIndicator()
            }

            errorMessage?.let { err ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = err, color = MaterialTheme.colorScheme.error)
            }

            Spacer(modifier = Modifier.weight(1f))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { navController.popBackStack() },
                    modifier = Modifier
                        .weight(1f)
                        .height(72.dp),
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(cancelText)
                }

                Button(
                    onClick = {
                        val origCodes = original?.codes ?: emptyList()
                        val newCodes = parsedCodes
                        val willReplace = (newCodes != null) && (newCodes != origCodes)

                        if (willReplace) {
                            showReplaceConfirm = true
                        } else {
                            scope.launch {
                                isLoading = true
                                try {
                                    val finalCodes = newCodes ?: origCodes
                                    val finalScanned = original?.scannedCodes ?: emptyList()
                                    val updated = SessionData(
                                        id = original!!.id,
                                        name = name,
                                        codes = finalCodes,
                                        scannedCodes = finalScanned
                                    )
                                    repo.update(updated)
                                    navController.popBackStack()
                                } catch (t: Throwable) {
                                    errorMessage = context.getString(R.string.error_saving_session, t.message ?: "")
                                } finally {
                                    isLoading = false
                                }
                            }
                        }
                    },
                    enabled = name.isNotBlank() && !isLoading,
                    modifier = Modifier
                        .weight(1f)
                        .height(72.dp),
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(saveText)
                }
            }
        }

    }

    if (showReplaceConfirm && original != null) {
        val origScanned = original!!.scannedCodes
        val finalCodes = parsedCodes ?: original!!.codes
        val willKeep = origScanned.count { it in finalCodes }
        val willRemove = origScanned.size - willKeep

        AlertDialog(
            onDismissRequest = { showReplaceConfirm = false },
            title = { Text(replacingTitle) },
            text = {
                // используем context.getString чтобы заполнить параметры
                val extra = if (willRemove > 0) "\n$willRemove ${context.getString(R.string.removed_scanned_count_suffix)}" else ""
                Text(
                    text = context.getString(R.string.replace_codes_summary, willKeep, extra)
                )
            },
            confirmButton = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Button(onClick = { showReplaceConfirm = false }) {
                        Text(cancelText)
                    }
                    Button(onClick = {
                        showReplaceConfirm = false
                        scope.launch {
                            isLoading = true
                            try {
                                val final = parsedCodes ?: original!!.codes
                                val finalScanned = original!!.scannedCodes.filter { it in final }
                                val updated = SessionData(
                                    id = original!!.id,
                                    name = name,
                                    codes = final,
                                    scannedCodes = finalScanned
                                )
                                repo.update(updated)
                                navController.popBackStack()
                            } catch (t: Throwable) {
                                errorMessage = context.getString(R.string.error_saving_session, t.message ?: "")
                            } finally {
                                isLoading = false
                            }
                        }
                    }) {
                        Text(replaceAndSaveText)
                    }
                }
            }
        )
    }
}