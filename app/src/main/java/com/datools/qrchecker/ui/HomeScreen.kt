package com.datools.qrchecker.ui

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
import androidx.compose.material3.Button
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.datools.qrchecker.data.room.AppDatabase
import com.datools.qrchecker.data.room.SessionEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(navController: NavController) {
    val context = LocalContext.current
    val database = AppDatabase.getInstance(context)
    val scope = rememberCoroutineScope()
    var sessions by remember { mutableStateOf<List<SessionEntity>>(emptyList()) }
    val buttonHeight = 82.dp

    LaunchedEffect(Unit) {
        sessions = database.sessionDao().getAllFlow().first()
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { navController.navigate("createSession") },
                containerColor = Color.Yellow,
                contentColor = Color.Black
            ) {
                Icon(Icons.Default.Add, contentDescription = "Новая сессия")
            }
        },
        floatingActionButtonPosition = FabPosition.Center
    ) { innerPadding ->
        Column (
            modifier = Modifier.padding(innerPadding)
        )
        {
            Text(
                text = "Сессии",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                style = MaterialTheme.typography.displaySmall,
                textAlign = TextAlign.Center
            )
            LazyColumn(
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                items(sessions.reversed()) { session ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Button(
                            onClick = { navController.navigate("scan/${session.id}") },
                            modifier = Modifier
                                .height(buttonHeight)
                                .weight(1f),
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text(
                                text = session.name,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }

                        FilledIconButton(
                            onClick = {
                                scope.launch {
                                    database.sessionDao().delete(session)
                                    sessions = database.sessionDao().getAllFlow().first()
                                }
                            },
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = Color.Red,
                                contentColor = Color.White
                            ),
                            modifier = Modifier
                                .padding(start = 4.dp)
                                .height(buttonHeight),
                            shape = MaterialTheme.shapes.small
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Удалить")
                        }

                    }

                }
            }
        }
    }
}
