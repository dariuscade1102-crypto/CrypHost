package com.cryptmc.app.ui.nav

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.NavType
import com.cryptmc.app.ui.admin.AdminDashboardScreen
import com.cryptmc.app.ui.ai.AiAssistantScreen
import com.cryptmc.app.ui.console.ConsoleScreen
import com.cryptmc.app.ui.files.FilesScreen
import com.cryptmc.app.ui.files.ServerFilesScreen
import com.cryptmc.app.ui.home.HomeScreen
import com.cryptmc.app.ui.profile.ProfileScreen
import com.cryptmc.app.ui.server.ServerSettingsScreen
import com.cryptmc.app.ui.setup.GuidedSetupScreen

@Composable
fun AppRoot() {
    val navController = rememberNavController()
    val current by navController.currentBackStackEntryAsState()
    val rootRoutes = setOf(Dest.Home.route, Dest.Files.route, Dest.Profile.route)
    Scaffold(bottomBar = {
        if (current?.destination?.route in rootRoutes) AppBottomBar(navController)
    }) { padding ->
        NavHost(navController, startDestination = Dest.Home.route, modifier = Modifier.padding(padding)) {
            composable(Dest.Home.route) {
                HomeScreen(
                    onCreateServer = { navController.navigate(Dest.GuidedSetup.route) },
                    onOpenConsole = { navController.navigate(Dest.Console.of(it)) },
                    onOpenSettings = { navController.navigate(Dest.ServerSettings.of(it)) },
                    onOpenFiles = { navController.navigate(Dest.ServerFiles.of(it)) },
                    onOpenAdminDashboard = { navController.navigate(Dest.AdminDashboard.route) },
                    onOpenAiAssistant = { navController.navigate(Dest.AiAssistant.general()) }
                )
            }
            composable(Dest.Files.route) { FilesScreen(onOpenServer = { navController.navigate(Dest.ServerFiles.of(it)) }) }
            composable(Dest.ServerFiles.route, arguments = listOf(navArgument("serverId") { type = NavType.StringType })) { entry ->
                entry.arguments?.getString("serverId")?.let { id -> ServerFilesScreen(id, onBack = { navController.popBackStack() }) }
            }
            composable(Dest.Profile.route) { ProfileScreen(showAccountSection = true, onOpenAiAssistant = { navController.navigate(Dest.AiAssistant.general()) }) }
            composable(Dest.AdminDashboard.route) { AdminDashboardScreen(onBack = { navController.popBackStack() }) }
            composable(Dest.GuidedSetup.route) {
                GuidedSetupScreen(onFinished = { id -> navController.navigate(Dest.ServerSettings.of(id)) { popUpTo(Dest.Home.route) } })
            }
            composable(Dest.Console.route, arguments = listOf(navArgument("serverId") { type = NavType.StringType })) { entry ->
                entry.arguments?.getString("serverId")?.let { id -> ConsoleScreen(id, onBack = { navController.popBackStack() }, onAskAi = { navController.navigate(Dest.AiAssistant.forServer(id)) }) }
            }
            composable(Dest.ServerSettings.route, arguments = listOf(navArgument("serverId") { type = NavType.StringType })) { entry ->
                entry.arguments?.getString("serverId")?.let { id -> ServerSettingsScreen(id, onBack = { navController.popBackStack() }, onAskAi = { navController.navigate(Dest.AiAssistant.forServer(id)) }) }
            }
            composable(Dest.AiAssistant.route, arguments = listOf(navArgument("serverId") { type = NavType.StringType; nullable = true; defaultValue = null })) { entry ->
                AiAssistantScreen(entry.arguments?.getString("serverId"), onBack = { navController.popBackStack() })
            }
        }
    }
}

private data class BottomItem(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)
private val bottomItems = listOf(
    BottomItem(Dest.Home.route, "Home", Icons.Filled.Home),
    BottomItem(Dest.Files.route, "Files", Icons.Filled.Folder),
    BottomItem(Dest.Profile.route, "Profile", Icons.Filled.Person)
)

@Composable
private fun AppBottomBar(navController: NavHostController) {
    val current by navController.currentBackStackEntryAsState()
    NavigationBar {
        bottomItems.forEach { item ->
            val selected = current?.destination?.hierarchy?.any { destination: NavDestination -> destination.route == item.route } == true
            NavigationBarItem(
                selected = selected,
                onClick = { navController.navigate(item.route) { popUpTo(navController.graph.findStartDestination().id) { saveState = true }; launchSingleTop = true; restoreState = true } },
                icon = { Icon(item.icon, contentDescription = null) },
                label = { Text(item.label) }
            )
        }
    }
}
