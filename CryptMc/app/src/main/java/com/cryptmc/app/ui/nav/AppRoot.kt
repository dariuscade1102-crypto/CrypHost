package com.cryptmc.app.ui.nav

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.NavType
import com.cryptmc.app.ui.admin.AdminDashboardScreen
import com.cryptmc.app.ui.ai.AiAssistantScreen
import com.cryptmc.app.ui.console.ConsoleScreen
import com.cryptmc.app.ui.files.FilesScreen
import com.cryptmc.app.ui.home.HomeScreen
import com.cryptmc.app.ui.profile.ProfileScreen
import com.cryptmc.app.ui.server.ServerSettingsScreen
import com.cryptmc.app.ui.setup.GuidedSetupScreen

@Composable
fun AppRoot() {
    val navController = rememberNavController()

    Scaffold(
        bottomBar = { AppBottomBar(navController) }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Dest.Home.route,
            modifier = Modifier.padding(padding)
        ) {
            composable(Dest.Home.route) {
                HomeScreen(
                    onOpenConsole = { id -> navController.navigate(Dest.Console.of(id)) },
                    onOpenSettings = { id -> navController.navigate(Dest.ServerSettings.of(id)) },
                    onOpenAdminDashboard = { navController.navigate(Dest.AdminDashboard.route) },
                    onOpenAiAssistant = { navController.navigate(Dest.AiAssistant.general()) }
                )
            }
            composable(Dest.AdminDashboard.route) {
                AdminDashboardScreen(onBack = { navController.popBackStack() })
            }
            composable(Dest.Plans.route) {
                // Billing/upgrade surface — same "Upgrade Now" content shown
                // in the reference app's Profile screen, broken out here
                // since the bottom bar treats it as its own tab.
                ProfileScreen(
                    showAccountSection = false,
                    onOpenAiAssistant = { navController.navigate(Dest.AiAssistant.general()) }
                )
            }
            composable(Dest.GuidedSetup.route) {
                GuidedSetupScreen(
                    onFinished = { id ->
                        navController.navigate(Dest.ServerSettings.of(id)) {
                            popUpTo(Dest.Home.route)
                        }
                    }
                )
            }
            composable(Dest.Files.route) { FilesScreen() }
            composable(Dest.Profile.route) {
                ProfileScreen(
                    showAccountSection = true,
                    onOpenAiAssistant = { navController.navigate(Dest.AiAssistant.general()) }
                )
            }

            composable(
                route = Dest.Console.route,
                arguments = listOf(navArgument("serverId") { type = NavType.StringType })
            ) { backStackEntry ->
                val serverId = backStackEntry.arguments?.getString("serverId") ?: return@composable
                ConsoleScreen(
                    serverId = serverId,
                    onBack = { navController.popBackStack() },
                    onAskAi = { navController.navigate(Dest.AiAssistant.forServer(serverId)) }
                )
            }
            composable(
                route = Dest.ServerSettings.route,
                arguments = listOf(navArgument("serverId") { type = NavType.StringType })
            ) { backStackEntry ->
                val serverId = backStackEntry.arguments?.getString("serverId") ?: return@composable
                ServerSettingsScreen(
                    serverId = serverId,
                    onBack = { navController.popBackStack() },
                    onAskAi = { navController.navigate(Dest.AiAssistant.forServer(serverId)) }
                )
            }

            composable(
                route = Dest.AiAssistant.route,
                arguments = listOf(navArgument("serverId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                })
            ) { backStackEntry ->
                val serverId = backStackEntry.arguments?.getString("serverId")
                AiAssistantScreen(serverId = serverId, onBack = { navController.popBackStack() })
            }
        }
    }
}

@Composable
private fun AppBottomBar(navController: androidx.navigation.NavHostController) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination

    NavigationBar {
        bottomBarDestinations.forEach { dest ->
            val selected = currentRoute?.hierarchy?.any { it.route == dest.route } == true
            NavigationBarItem(
                selected = selected,
                onClick = {
                    navController.navigate(dest.route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Icon(iconFor(dest), contentDescription = null) },
                label = { androidx.compose.material3.Text(labelFor(dest)) }
            )
        }
    }
}

private fun iconFor(dest: Dest) = when (dest) {
    Dest.Home -> Icons.Filled.Home
    Dest.Plans -> Icons.Filled.CreditCard
    Dest.GuidedSetup -> Icons.Filled.Add
    Dest.Files -> Icons.Filled.Folder
    Dest.Profile -> Icons.Filled.Person
    else -> Icons.Filled.Home
}

private fun labelFor(dest: Dest) = when (dest) {
    Dest.Home -> "Home"
    Dest.Plans -> "Plans"
    Dest.GuidedSetup -> "New"
    Dest.Files -> "Files"
    Dest.Profile -> "Profile"
    else -> ""
}
