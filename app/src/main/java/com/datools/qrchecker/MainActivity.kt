package com.datools.qrchecker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
//import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.datools.qrchecker.ui.theme.QRCheckerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            QRCheckerTheme {
                AppNav()
            }
        }

    }
}

@Composable
fun AppNav() {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = "home"
    ) {
        composable("home") {
            HomeScreen(navController)
        }
        composable("createSession") {
            CreateSessionScreen(navController)
        }
    }

}

@Composable
fun HomeScreen(navController: NavController) {
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
        floatingActionButtonPosition = FabPosition.Center,
    ) { innerPadding ->
        Text(
            text = "Сессии",
            modifier = Modifier
                .fillMaxWidth()
                .padding(innerPadding),
            style = MaterialTheme.typography.displaySmall,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun CreateSessionScreen(navController: NavController) {
    Scaffold(
        topBar = { }
    ) { innerPadding ->
        Text(
            text = "Настройка сессии",
            modifier = Modifier
                .fillMaxWidth()
                .padding(innerPadding),
            style = MaterialTheme.typography.displaySmall,
            textAlign = TextAlign.Center
        )
    }
}