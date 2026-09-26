package com.cryptmc.app.ui.nav

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.cryptmc.app.ui.console.ConsoleScreen
import com.cryptmc.app.ui.home.HomeScreen
import com.cryptmc.app.ui.server.ServerSettingsScreen
import com.cryptmc.app.ui.setup.GuidedSetupScreen

/**
 * Deliberately small navigation surface: the app is a local server controller,
 * not a collection of unrelated dashboards.
 */
@Composable
fun AppRoot() {
    val navController = rememberNavController()
    Scaffold { padding ->
        NavHost(
            navController = navController,
            startDestination = Dest.Home.route,
            modifier = Modifier.padding(padding)
        ) {
            composable(Dest.Home.route) {
                HomeScreen(
                    onCreateServer = { navController.navigate(Dest.GuidedSetup.route) },
                    onOpenConsole = { navController.navigate(Dest.Console.of(it)) },
                    onOpenSettings = { navController.navigate(Dest.ServerSettings.of(it)) }
                )
            }
            composable(Dest.GuidedSetup.route) {
                GuidedSetupScreen(
                    onFinished = { id ->
                        navController.navigate(Dest.ServerSettings.of(id)) {
                            popUpTo(Dest.Home.route)
                        }
                    },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(
                Dest.Console.route,
                arguments = listOf(navArgument("serverId") { type = NavType.StringType })
            ) { entry ->
                entry.arguments?.getString("serverId")?.let { id ->
                    ConsoleScreen(id, onBack = { navController.popBackStack() })
                }
            }
            composable(
                Dest.ServerSettings.route,
                arguments = listOf(navArgument("serverId") { type = NavType.StringType })
            ) { entry ->
                entry.arguments?.getString("serverId")?.let { id ->
                    ServerSettingsScreen(id, onBack = { navController.popBackStack() })
                }
            }
        }
    }
}
