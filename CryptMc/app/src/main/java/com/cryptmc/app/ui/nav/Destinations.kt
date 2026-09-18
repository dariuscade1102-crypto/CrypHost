package com.cryptmc.app.ui.nav

sealed class Dest(val route: String) {
    data object Home : Dest("home")
    data object Plans : Dest("plans")
    data object GuidedSetup : Dest("guided_setup")
    data object Files : Dest("files")
    data object Profile : Dest("profile")
    data object AdminDashboard : Dest("admin_dashboard")

    // Detail screens, parameterized by server id
    data object Console : Dest("console/{serverId}") {
        fun of(serverId: String) = "console/$serverId"
    }
    data object ServerSettings : Dest("server_settings/{serverId}") {
        fun of(serverId: String) = "server_settings/$serverId"
    }

    /**
     * serverId is an optional query param, not a path segment — reachable
     * both with server context (from a server's Console/Settings "Ask AI"
     * action) and without (from Home's top bar, for general/first-server
     * questions). See ui/ai/AiAssistantScreen.kt.
     */
    data object AiAssistant : Dest("ai_assistant?serverId={serverId}") {
        fun general() = "ai_assistant"
        fun forServer(serverId: String) = "ai_assistant?serverId=$serverId"
    }
}

/** Icons + labels for the five persistent bottom-bar destinations. */
val bottomBarDestinations = listOf(Dest.Home, Dest.Plans, Dest.GuidedSetup, Dest.Files, Dest.Profile)
