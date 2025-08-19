package com.datools.qrchecker.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.datools.qrchecker.data.SessionRepository
import com.datools.qrchecker.model.SessionData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CodesListScreen(
    navController: NavController,
    sessionId: String,
    type: String // "scanned" or "not_scanned"
) {
    val context = LocalContext.current
    val repo = remember { SessionRepository(context) }
    var session by remember { mutableStateOf<SessionData?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(sessionId) {
        scope.launch(Dispatchers.IO) {
            session = try {
                repo.getById(sessionId)
            } catch (t: Throwable) {
                null
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar( // Changed from SmallTopAppBar
                title = {
                    Text(
                        when (type) {
                            "scanned" -> "Сканированные (${session?.scannedCodes?.size ?: 0})"
                            else -> "Неотсканированные (${session?.let { it.codes.size - it.scannedCodes.size } ?: 0})"
                        }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier
            .fillMaxSize()
            .padding(padding)) {

            if (session == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Загрузка сессии...")
                }
                return@Box
            }

            val codes: List<String> = if (type == "scanned") {
                session!!.scannedCodes
            } else {
                session!!.codes.filter { it !in session!!.scannedCodes }
            }

            if (codes.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = if (type == "scanned") "Нет отсканированных кодов" else "Нет неотсканированных кодов"
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(codes) { code ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = code,
                                    maxLines = 4,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = if (type == "scanned") "Отсканировано" else "Не отсканировано",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.align(Alignment.End)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}