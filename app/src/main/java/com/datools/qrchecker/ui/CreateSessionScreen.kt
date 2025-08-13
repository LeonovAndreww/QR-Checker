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
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.datools.qrchecker.R
import com.datools.qrchecker.util.getFileNameFromUri
import com.datools.qrchecker.util.parsePdfForQRCodes
import kotlinx.coroutines.launch


@Composable
fun CreateSessionScreen(navController: NavController) {
    val context = LocalContext.current
    var sessionName by rememberSaveable { mutableStateOf("") }
    var selectedPdfUriString by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedPdfName by rememberSaveable { mutableStateOf("") }

    val coroutineScope = rememberCoroutineScope()

    val documentPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri: Uri? ->
            if (uri != null) {
                selectedPdfName = getFileNameFromUri(uri, context)
                selectedPdfUriString = uri.toString()
                Log.d("LogCat", "Uri opened: $uri")

                coroutineScope.launch {
                    val codes = parsePdfForQRCodes(context, uri)
                    codes.forEach { code ->
                        Log.d("LogCat", "QR Code: $code")
                    }
                }
            }
        }
    )

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Настройка сессии",
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
                label = { Text("Имя сессии") },
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
                        contentDescription = "PDF Icon"
                    )
                    Text(
                        text = selectedPdfName.ifEmpty { "Добавить PDF файл" },
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
                        navController.currentBackStackEntry?.savedStateHandle?.set("pdfUri", uriStr)
                        navController.currentBackStackEntry?.savedStateHandle?.set("sessionName", sessionName)
                        navController.navigate("scan")
                    }
                },
                enabled = (sessionName.isNotBlank() && selectedPdfUriString != null),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(vertical = 14.dp)
                    .height(72.dp),
                shape = MaterialTheme.shapes.small
            ) {
                Text("Продолжить", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
