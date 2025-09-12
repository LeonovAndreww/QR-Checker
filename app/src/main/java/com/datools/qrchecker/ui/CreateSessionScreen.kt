package com.datools.qrchecker.ui

import android.net.Uri
//import android.util.Log
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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.datools.qrchecker.R
import com.datools.qrchecker.Screen
import com.datools.qrchecker.util.getFileNameFromUri
import androidx.core.net.toUri
import com.datools.qrchecker.viewmodel.ScanViewModel

@Composable
fun CreateSessionScreen(navController: NavController) {
    val context = LocalContext.current
    var sessionName by rememberSaveable { mutableStateOf("") }
    var selectedPdfUriString by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedPdfName by rememberSaveable { mutableStateOf("") }

    val documentPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri: Uri? ->
            if (uri != null) {
                selectedPdfName = getFileNameFromUri(uri, context)
                selectedPdfUriString = uri.toString()
//                Log.d("LogCat", "Uri opened: $uri")
            }
        }
    )

    // getting ViewModel
    val scanViewModel: ScanViewModel = androidx.lifecycle.viewmodel.compose.viewModel()

    val isLoading by remember { derivedStateOf { scanViewModel.isLoading.value } }
    val createdSessionId by remember { derivedStateOf { scanViewModel.createdSessionId.value } }

    LaunchedEffect(createdSessionId) {
        createdSessionId?.let { id ->
            navController.currentBackStackEntry?.savedStateHandle?.set("sessionName", sessionName)
            navController.navigate(Screen.Scan.createRoute(id))
            scanViewModel.clearCreatedSessionId()
        }
    }

    val titleText = stringResource(id = R.string.setup_session_title)
    val nameLabel = stringResource(id = R.string.session_name_label)
    val addPdfLabel = stringResource(id = R.string.add_pdf_label)
    val continueText = stringResource(id = R.string.continue_button)
    val parsingText = stringResource(id = R.string.parsing_pdf)
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
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
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
                onClick = { documentPicker.launch(arrayOf("application/pdf")) },
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
                        text = selectedPdfName.ifEmpty { addPdfLabel },
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = {
                    selectedPdfUriString?.let { uriStr ->
                        val pdfUri = uriStr.toUri()
                        scanViewModel.createSessionFromPdf(context, sessionName, pdfUri)
                    }
                },
                enabled = (sessionName.isNotBlank() && selectedPdfUriString != null && !isLoading),
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
}