package com.cryptmc.desktop

/**
 * Deliberately a separate, simpler data class from app's
 * com.cryptmc.app.data.ServerConfig — desktop has no JRE-provisioning,
 * mobile-only ABI, or tunnel-agent-binary concerns, and no shared `:core`
 * module exists yet to hold one canonical version (see build.gradle.kts'
 * class doc). Field names are kept aligned where the concept is genuinely
 * the same, so promoting this to commonMain later is a rename exercise,
 * not a rewrite.
 */
data class DesktopServerConfig(
    val id: String,
    val name: String,
    val workingDir: String,
    val jarFileName: String,          // must already exist in workingDir — no in-app downloader wired yet, see README
    val minRamMb: Int = 1024,
    val maxRamMb: Int = 4096,
    val serverPort: Int = 25565,
    val eulaAccepted: Boolean = false
)

data class DesktopServerRuntimeStatus(
    val running: Boolean = false,
    val pid: Long? = null
)
