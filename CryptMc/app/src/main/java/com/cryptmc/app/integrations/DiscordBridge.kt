package com.cryptmc.app.integrations

import com.cryptmc.app.data.DiscordBridgeConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * One-way relay: server events -> a Discord channel, via an incoming
 * webhook URL the user creates in Discord (Channel Settings > Integrations
 * > Webhooks > New Webhook > Copy URL, paste into the Network tab).
 *
 * This is deliberately outbound-only. A two-way bridge (Discord messages
 * appearing as in-game chat) needs a bot with a persistent gateway
 * connection, which doesn't fit an Android foreground service's lifecycle
 * well (OS-killed on memory pressure, no guaranteed always-on socket) —
 * that direction is better run as a small always-on process elsewhere
 * (a Paper plugin like DiscordSRV, or a companion service), not from this
 * app. Wire [relay] into ConsoleScreen.kt's log-line parser: same
 * "joined the game" / "left the game" / chat-bracket regexes that already
 * feed parsePlayerList and PlayerStatsRepository.
 */
class DiscordBridge(private val okHttpClient: OkHttpClient = OkHttpClient()) {

    suspend fun relayServerStarted(config: DiscordBridgeConfig, serverName: String) {
        if (!config.enabled || !config.relayServerStartStop) return
        send(config.webhookUrl, "🟢 **$serverName** is now online.")
    }

    suspend fun relayServerStopped(config: DiscordBridgeConfig, serverName: String) {
        if (!config.enabled || !config.relayServerStartStop) return
        send(config.webhookUrl, "🔴 **$serverName** has stopped.")
    }

    suspend fun relayPlayerJoined(config: DiscordBridgeConfig, playerName: String) {
        if (!config.enabled || !config.relayJoinLeave) return
        send(config.webhookUrl, "➡️ **$playerName** joined the game")
    }

    suspend fun relayPlayerLeft(config: DiscordBridgeConfig, playerName: String) {
        if (!config.enabled || !config.relayJoinLeave) return
        send(config.webhookUrl, "⬅️ **$playerName** left the game")
    }

    suspend fun relayChatMessage(config: DiscordBridgeConfig, playerName: String, message: String) {
        if (!config.enabled || !config.relayChat) return
        // Discord webhooks interpret Markdown; escape the player's own message so
        // in-game text like "*ping*" or "_test_" can't inject formatting/mentions.
        val escaped = message.replace(Regex("([*_~`|>])"), "\\\\$1")
        send(config.webhookUrl, "**<$playerName>** $escaped")
    }

    private suspend fun send(webhookUrl: String, content: String) = withContext(Dispatchers.IO) {
        if (webhookUrl.isBlank()) return@withContext
        runCatching {
            val json = JSONObject().put("content", content).toString()
            val body = json.toRequestBody("application/json".toMediaType())
            val request = Request.Builder().url(webhookUrl).post(body).build()
            okHttpClient.newCall(request).execute().close()
        }
        // Failures are swallowed deliberately — a bad/rate-limited webhook shouldn't
        // interrupt server operation. Surface delivery failures via a small in-memory
        // "last relay error" StateFlow on this class if you want a UI indicator later.
    }
}
