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
import com.datools.qrchecker.ui.SettingsScreen

const val ARG_SESSION_ID = "sessionId"
const val ARG_TYPE = "type"

// Set of all screens (routes) for navigation
sealed class Screen(val route: String) {
    object Home : Screen("home")
    object CreateSession : Screen("createSession")
    object Settings : Screen("settings")

    object Scan : Screen("scan/{$ARG_SESSION_ID}") {
        fun createRoute(sessionId: String) = "scan/$sessionId"
    }

    object EditSession : Screen("edit/{$ARG_SESSION_ID}") {
        fun createRoute(sessionId: String) = "edit/$sessionId"
    }

    object CodesList : Screen("codes_list/{$ARG_SESSION_ID}/{$ARG_TYPE}") {
        // type: "scanned" | "not_scanned"
        fun createRoute(sessionId: String, type: String) = "codes_list/$sessionId/$type"
    }
}

private val sessionIdArgument = listOf(
    navArgument(ARG_SESSION_ID) { type = NavType.StringType }
)

@Composable
fun AppNav() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Screen.Home.route
    ) {
        // Main screen
        composable(Screen.Home.route) {
            HomeScreen(navController = navController)
        }

        composable(Screen.Settings.route) {
            SettingsScreen(navController = navController)
        }

        // Create session screen
        composable(Screen.CreateSession.route) {
            CreateSessionScreen(navController = navController)
        }

        // Scan screen
        composable(route = Screen.Scan.route, arguments = sessionIdArgument) { backStackEntry ->
            val sessionId = backStackEntry.arguments?.getString(ARG_SESSION_ID)
            if (sessionId != null) {
                ScanScreen(navController = navController, sessionId = sessionId)
            } else {
                navController.popBackStack()
            }
        }

        // Edit session screen
        composable(
            route = Screen.EditSession.route,
            arguments = sessionIdArgument
        ) { backStackEntry ->
            val sessionId = backStackEntry.arguments?.getString(ARG_SESSION_ID)
            if (sessionId != null) {
                EditSessionScreen(navController = navController, sessionId = sessionId)
            } else {
                navController.popBackStack()
            }
        }

        // Scanned / not scanned code list
        composable(
            route = Screen.CodesList.route,
            arguments = listOf(
                navArgument(ARG_SESSION_ID) { type = NavType.StringType },
                navArgument(ARG_TYPE) { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val sessionId = backStackEntry.arguments?.getString(ARG_SESSION_ID)
            val type = backStackEntry.arguments?.getString(ARG_TYPE) ?: TYPE_SCANNED
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

const val TYPE_SCANNED = "scanned"
const val TYPE_NOT_SCANNED = "not_scanned"
