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

// Set of all screens (routes) for navigation
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
            startDestination = Home.route
        ) {
            // Main screen
            composable(Home.route) {
                HomeScreen(navController = navController)
            }

            // Create session screen
            composable(CreateSession.route) {
                CreateSessionScreen(navController = navController)
            }

            // Scan screen
            composable(route = Scan.route) { backStackEntry ->
                val sessionId = backStackEntry.arguments?.getString("sessionId")
                if (sessionId != null) {
                    ScanScreen(navController = navController, sessionId = sessionId)
                } else {
                    navController.popBackStack()
                }
            }

            // Edit session screen
            composable(route = EditSession.route) { backStackEntry ->
                val sessionId = backStackEntry.arguments?.getString("sessionId")
                if (sessionId != null) {
                    EditSessionScreen(navController = navController, sessionId = sessionId)
                } else {
                    navController.popBackStack()
                }
            }
            composable(
                route = CodesList.route,
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