package com.datools.qrchecker

import androidx.compose.runtime.Composable
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavController
import androidx.navigation.NavOptionsBuilder
import androidx.navigation.NavType
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.datools.qrchecker.ui.codes.CodesListScreen
import com.datools.qrchecker.ui.create.CreateSessionScreen
import com.datools.qrchecker.ui.edit.EditSessionScreen
import com.datools.qrchecker.ui.home.HomeScreen
import com.datools.qrchecker.ui.scan.ScanScreen
import com.datools.qrchecker.ui.settings.SettingsScreen

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

/** Насколько долго один экран сменяет другой. */
private const val SCREEN_FADE_MS = 160

@Composable
fun AppNav() {
    val navController = rememberNavController()

    // Экраны сменяются растворением, а не выездом сбоку.
    //
    // При выезде между уходящим и приходящим экраном на мгновение видно фон окна, и
    // переход с белого списка на чёрную камеру читался как вспышка. Растворение ничего
    // не открывает: под верхним экраном всё это время лежит нижний.
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
        enterTransition = { fadeIn(tween(SCREEN_FADE_MS)) },
        exitTransition = { fadeOut(tween(SCREEN_FADE_MS)) },
        popEnterTransition = { fadeIn(tween(SCREEN_FADE_MS)) },
        popExitTransition = { fadeOut(tween(SCREEN_FADE_MS)) }
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

        // the session id is a required path argument; the view models read it themselves
        composable(route = Screen.Scan.route, arguments = sessionIdArgument) {
            ScanScreen(navController = navController)
        }

        composable(route = Screen.EditSession.route, arguments = sessionIdArgument) {
            EditSessionScreen(navController = navController)
        }

        composable(
            route = Screen.CodesList.route,
            arguments = listOf(
                navArgument(ARG_SESSION_ID) { type = NavType.StringType },
                navArgument(ARG_TYPE) { type = NavType.StringType }
            )
        ) { backStackEntry ->
            CodesListScreen(
                navController = navController,
                type = backStackEntry.arguments?.getString(ARG_TYPE) ?: TYPE_SCANNED
            )
        }
    }
}

const val TYPE_SCANNED = "scanned"
const val TYPE_NOT_SCANNED = "not_scanned"

/**
 * Переход, который не срабатывает дважды.
 *
 * Два быстрых нажатия успевают отдать две команды до того, как применилась первая, и
 * приложение уезжает в пустой белый экран. Экран, с которого уходят, в этот момент уже не
 * в состоянии RESUMED - по нему второй тап и отсеивается. То же и для возврата.
 */
fun NavController.navigateOnce(route: String): Boolean {
    if (currentBackStackEntry?.lifecycle?.currentState != Lifecycle.State.RESUMED) return false
    navigate(route)
    return true
}

fun NavController.navigateOnce(route: String, builder: NavOptionsBuilder.() -> Unit) {
    if (currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED) {
        navigate(route, builder)
    }
}

fun NavController.popBackStackOnce() {
    if (currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED) {
        popBackStack()
    }
}
