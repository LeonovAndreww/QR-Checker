package com.datools.qrchecker.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import kotlinx.coroutines.withContext
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api

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

    // snackbar host
    val snackbarHostState = remember { SnackbarHostState() }

    // dialog state for delete confirmation
    var codeToDelete by remember { mutableStateOf<String?>(null) }
    var codeToDeleteIsScanned by remember { mutableStateOf(false) }

    LaunchedEffect(sessionId) {
        scope.launch(Dispatchers.IO) {
            session = try {
                repo.getById(sessionId)
            } catch (_: Throwable) {
                null
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (type) {
                            "scanned" -> "Отсканированные (${session?.scannedCodes?.size ?: 0})"
                            else -> "Неотсканированные (${session?.let { it.codes.size - it.scannedCodes.size } ?: 0})"
                        }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Назад"
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {

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
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = code,
                                    maxLines = 4,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(end = 8.dp)
                                )

                                IconButton(
                                    onClick = {
                                        codeToDelete = code
                                        codeToDeleteIsScanned = (type == "scanned")
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Удалить код"
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (codeToDelete != null) {
                AlertDialog(
                    onDismissRequest = { codeToDelete = null },
                    title = {
                        Text("Удалить код?", style = MaterialTheme.typography.headlineSmall)
                    },
                    text = {
                        Text(
                            "Вы уверены, что хотите удалить этот код?\n\n${codeToDelete}",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    },
                    confirmButton = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Button(onClick = { codeToDelete = null }) {
                                Text("Отмена")
                            }
                            Button(onClick = {
                                val code = codeToDelete!!
                                val isScanned = codeToDeleteIsScanned

                                scope.launch {
                                    try {
                                        val updated = withContext(Dispatchers.IO) {
                                            val current =
                                                repo.getById(sessionId) ?: return@withContext null
                                            val newSession: SessionData = if (isScanned) {
                                                val newScanned =
                                                    current.scannedCodes.filter { it != code }
                                                        .toMutableList()
                                                current.copy(scannedCodes = newScanned)
                                            } else {
                                                val newCodes = current.codes.filter { it != code }
                                                    .toMutableList()
                                                val newScanned =
                                                    current.scannedCodes.filter { it in newCodes }
                                                        .toMutableList()
                                                current.copy(
                                                    codes = newCodes,
                                                    scannedCodes = newScanned
                                                )
                                            }
                                            repo.update(newSession)
                                            newSession
                                        }

                                        if (updated != null) {
                                            session = updated
                                            codeToDelete = null
                                            snackbarHostState.showSnackbar("Код удалён")
                                        } else {
                                            codeToDelete = null
                                            snackbarHostState.showSnackbar("Не удалось удалить код")
                                        }
                                    } catch (t: Throwable) {
                                        codeToDelete = null
                                        snackbarHostState.showSnackbar("Ошибка при удалении: ${t.message}")
                                    }
                                }
                            }) {
                                Text("Удалить")
                            }
                        }
                    },
                    // мы уже используем Row в confirmButton, dismissButton не нужен отдельно
                    // но чтобы API не ругался, передаём пустую реализацию
                    dismissButton = { /* пусто, используем кнопку "Отмена" в confirmButton Row */ }
                )
            }
        }
    }
}
