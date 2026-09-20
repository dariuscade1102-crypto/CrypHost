package com.cryptmc.app.data

enum class ServerLoader(val displayName: String, val badge: String) {
    PAPER("Paper", "Recommended"),
    VANILLA("Vanilla", "Official"),
    FABRIC("Fabric", "Mods"),
    NEOFORGE("NeoForge", "Modded"),
    PURPUR("Purpur", "Plugins"),
    POWERNUKKITX("PowerNukkitX", "Experimental") // Bedrock-only
}

enum class Gamemode { SURVIVAL, CREATIVE, ADVENTURE, SPECTATOR }
enum class Difficulty { PEACEFUL, EASY, NORMAL, HARD }
enum class WorldType { NORMAL, FLAT, LARGE_BIOMES, AMPLIFIED }
/**
 * HOTSPOT is mobile/tablet-only: instead of joining an existing Wi-Fi
 * network (LOCAL_ONLY) or tunneling out to the internet (PLAYIT_GG,
 * CLOUDFLARE), the device itself becomes the Wi-Fi access point via
 * Android's LocalOnlyHotspot API — see network/HotspotManager.kt. Useful
 * when there's no router at all (a field, a car trip, a venue with no
 * guest Wi-Fi): other players connect their phones to this device's
 * hotspot, then Direct Connect to its local IP. Not offered on the
 * desktop companion, which has no equivalent OS-level "become an AP" API
 * this app can portably call — see README.
 */
enum class TunnelMode { LOCAL_ONLY, PLAYIT_GG, CLOUDFLARE, HOTSPOT }

enum class BackupFrequency { OFF, EVERY_6_HOURS, DAILY, WEEKLY }
enum class RestartFrequency { OFF, DAILY, EVERY_12_HOURS }

/** Backups + auto-restart, edited from the new Backups/Scheduling tab. */
data class ScheduleConfig(
    val backupFrequency: BackupFrequency = BackupFrequency.OFF,
    val backupRetentionCount: Int = 5,       // oldest beyond this count are pruned
    val backupIncludePlugins: Boolean = true,
    val restartFrequency: RestartFrequency = RestartFrequency.OFF,
    val restartHourOfDay: Int = 4,           // 0-23, local time; used by DAILY
    val restartWarningSeconds: Int = 60      // broadcast countdown before kicking players
) : java.io.Serializable

/** Optional chat relay to Discord — see integrations/DiscordBridge.kt. */
data class DiscordBridgeConfig(
    val enabled: Boolean = false,
    val webhookUrl: String = "",
    val relayJoinLeave: Boolean = true,
    val relayChat: Boolean = true,
    val relayServerStartStop: Boolean = true
) : java.io.Serializable

/**
 * Everything a server's settings tabs can edit. Mirrors the six-tab layout
 * of the reference app (General / Software / World / Mods & Plugins /
 * Performance / Network) so each tab is just a view over one slice of this.
 *
 * Implements Serializable so it can travel in an Intent extra to
 * ServerForegroundService (ACTION_START/EXTRA_CONFIG) — it wasn't before,
 * which meant the only call site that ever tried to send one there
 * (`intent.getSerializableExtra(EXTRA_CONFIG) as? ServerConfig`) could
 * never actually have been fed a real ServerConfig.
 */
data class ServerConfig(
    val id: String,
    val name: String,                       // folder name / display name
    val motd: String = "A Minecraft Server",
    val iconPath: String? = null,            // custom server icon, user-picked

    // General tab
    val gamemode: Gamemode = Gamemode.SURVIVAL,
    val difficulty: Difficulty = Difficulty.EASY,
    val hardcore: Boolean = false,

    // Software tab
    val loader: ServerLoader = ServerLoader.PAPER,
    val minecraftVersion: String = "1.21.1",
    val jarFileName: String? = null,         // e.g. "paper-26.2-123.jar"
    val jarPath: String? = null,
    val javaVersion: Int = 21,
    val eulaAccepted: Boolean = false,
    val javaFlags: String = "-XX:+UseG1GC -XX:+ParallelRefProcEnabled",
    val programArguments: String = "",

    // World tab
    val worldType: WorldType = WorldType.NORMAL,
    val worldSeed: String = "",
    val liveWorldMapEnabled: Boolean = false, // squaremap

    // Mods & Plugins tab
    val installedDatapacks: List<InstalledAddon> = emptyList(),
    val installedPlugins: List<InstalledAddon> = emptyList(),

    // Performance tab
    val performanceModeEnabled: Boolean = false,
    val optimizeForPhone: Boolean = true,
    val minRamMb: Int = 1024,
    val maxRamMb: Int = 4096,
    val viewDistanceChunks: Int = 10,
    val simulationDistanceChunks: Int = 10,
    val maxPlayers: Int = 10,

    // Network tab
    val serverPort: Int = 25565,
    val onlineMode: Boolean = false,          // "Block Cracked Players"
    val whitelistEnabled: Boolean = false,
    val bedrockCrossplayEnabled: Boolean = false, // Geyser toggle
    val tunnelMode: TunnelMode = TunnelMode.LOCAL_ONLY,

    // Backups & scheduled restarts (Scheduling tab)
    val schedule: ScheduleConfig = ScheduleConfig(),

    // Discord chat bridge (Network tab)
    val discordBridge: DiscordBridgeConfig = DiscordBridgeConfig(),

    val workingDir: String
) : java.io.Serializable

/** One completed or in-progress backup, shown in the Backups tab list. */
data class BackupRecord(
    val id: String,
    val serverId: String,
    val fileName: String,          // e.g. "buret-smp-2026-09-15-0400.zip"
    val filePath: String,
    val createdAtEpochMs: Long,
    val sizeBytes: Long,
    val trigger: BackupTrigger
)

enum class BackupTrigger { MANUAL, SCHEDULED, PRE_UPDATE }

data class InstalledAddon(
    val name: String,
    val fileName: String,
    val modrinthProjectId: String? = null
) : java.io.Serializable

/** Live runtime state — separate from the saved config above. */
data class ServerRuntimeStatus(
    val running: Boolean = false,
    val publicAddress: String? = null,
    val playersOnline: List<String> = emptyList(),
    val ramUsedMb: Int? = null
)
