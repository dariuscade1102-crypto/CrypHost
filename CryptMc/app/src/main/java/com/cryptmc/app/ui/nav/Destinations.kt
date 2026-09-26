package com.cryptmc.app.ui.nav

sealed class Dest(val route: String) {
    data object Home : Dest("home")
    data object Plans : Dest("plans")
    data object GuidedSetup : Dest("guided_setup")
    data object Files : Dest("files")
    data object Profile : Dest("profile")
    data object AdminDashboard : Dest("admin_dashboard")
    data object ServerFiles : Dest("files/{serverId}") {
        fun of(serverId: String) = "files/$serverId"
    }

    data object Console : Dest("console/{serverId}") {
        fun of(serverId: String) = "console/$serverId"
    }
    data object ServerSettings : Dest("server_settings/{serverId}") {
        fun of(serverId: String) = "server_settings/$serverId"
    }
    data object AiAssistant : Dest("ai_assistant?serverId={serverId}") {
        fun general() = "ai_assistant"
        fun forServer(serverId: String) = "ai_assistant?serverId=$serverId"
    }
}

val bottomBarDestinations = listOf(Dest.Home, Dest.Files, Dest.Profile)
