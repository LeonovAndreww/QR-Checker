package com.datools.qrchecker

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.datools.qrchecker.ui.CreateSessionScreen
import com.datools.qrchecker.ui.HomeScreen
import com.datools.qrchecker.ui.ScanScreen

// Определим имена маршрутов (routes) для навигации
sealed class Screen(val route: String) {
    object Home : Screen("home")
    object CreateSession : Screen("createSession")
    object Scan : Screen("scan")
}

@Composable
fun AppNav() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Screen.Home.route
    ) {
        // Главный экран
        composable(Screen.Home.route) {
            HomeScreen(navController = navController)
        }

        // Экран создания сессии
        composable(Screen.CreateSession.route) {
            CreateSessionScreen(navController = navController)
        }

        // Экран сканирования
        composable(Screen.Scan.route) {
            ScanScreen(navController = navController, 0L)
        }
    }
}
