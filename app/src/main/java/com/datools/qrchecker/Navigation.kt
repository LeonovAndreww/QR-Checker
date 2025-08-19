package com.datools.qrchecker

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.datools.qrchecker.ui.CodesListScreen
import com.datools.qrchecker.ui.CreateSessionScreen
import com.datools.qrchecker.ui.EditSessionScreen
import com.datools.qrchecker.ui.HomeScreen
import com.datools.qrchecker.ui.ScanScreen

// Определим имена маршрутов (routes) для навигации
sealed class Screen(val route: String) {
    object Home : Screen("home")
    object CreateSession : Screen("createSession")
    object Scan : Screen("scan/{sessionId}") {
        fun createRoute(sessionId: String) = "scan/$sessionId"
    }

    object EditSession : Screen("edit/{sessionId}") {
        fun createRoute(sessionId: String) = "edit/$sessionId"
    }

    object CodesList : Screen("codes_list/{sessionId}/{type}") {
        // type: "scanned" | "not_scanned"
        fun createRoute(sessionId: String, type: String) = "codes_list/$sessionId/$type"
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
            composable(route = Screen.Scan.route) { backStackEntry ->
                val sessionId = backStackEntry.arguments?.getString("sessionId")
                if (sessionId != null) {
                    ScanScreen(navController = navController, sessionId = sessionId)
                } else {
                    navController.popBackStack()
                }
            }

            composable(route = Screen.EditSession.route) { backStackEntry ->
                val sessionId = backStackEntry.arguments?.getString("sessionId")
                if (sessionId != null) {
                    EditSessionScreen(navController = navController, sessionId = sessionId)
                } else {
                    navController.popBackStack()
                }
            }
            composable(
                route = Screen.CodesList.route,
                arguments = listOf(
                    navArgument("sessionId") { type = NavType.StringType },
                    navArgument("type") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val sessionId = backStackEntry.arguments?.getString("sessionId")
                val type = backStackEntry.arguments?.getString("type") ?: "scanned"
                if (sessionId != null) {
                    CodesListScreen(
                        navController = navController,
                        sessionId = sessionId,
                        type = type
                    )
                } else {
                    navController.popBackStack()
                }
            }
        }
    }
}