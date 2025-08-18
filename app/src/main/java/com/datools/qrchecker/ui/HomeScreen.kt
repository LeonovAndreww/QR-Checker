package com.datools.qrchecker.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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

@Composable
fun HomeScreen(navController: NavController) {
    val context = LocalContext.current
    val database = AppDatabase.getInstance(context)

    var sessions by remember { mutableStateOf<List<SessionEntity>>(emptyList()) }

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
                    Button(
                        onClick = { navController.navigate("scan/${session.id}") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(96.dp)
                            .padding(8.dp),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = session.name,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }
        }
    }
}
