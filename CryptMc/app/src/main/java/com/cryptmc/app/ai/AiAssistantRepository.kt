package com.cryptmc.app.ai

import com.cryptmc.app.data.ServerConfig
import com.cryptmc.app.data.ServerRuntimeStatus
import com.cryptmc.app.network.AiApi
import com.cryptmc.app.network.AiMessageRequest
import com.cryptmc.app.network.AiWireMessage
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

/** One turn in the on-screen chat transcript. */
data class ChatMessage(val role: ChatRole, val text: String)
enum class ChatRole { USER, ASSISTANT }

/**
 * Everything about the current server worth handing the model so its
 * advice is concrete ("bump maxRamMb to at least 3072", not "increase your
 * RAM setting") instead of generic Minecraft-hosting trivia. Optional —
 * the assistant is also reachable with no server selected (Home tab),
 * in which case this is null and the system prompt stays platform-general.
 */
data class AiServerContext(
    val config: ServerConfig,
    val status: ServerRuntimeStatus?,
    val recentConsoleLines: List<String> = emptyList(),
    val isHotspotActive: Boolean = false
)

/** Which host this assistant is running on — folded into the system prompt so answers use the right menu names/paths. */
enum class AiPlatform { ANDROID, DESKTOP }

private const val DEFAULT_MODEL = "claude-sonnet-5"
private const val MAX_TOKENS = 1024
private const val MAX_CONSOLE_LINES_IN_PROMPT = 40

/**
 * Requirement: an in-app AI helper for server setup/troubleshooting,
 * available on every device this app runs on (phone, tablet, and — see
 * desktop's ai/AiAssistantManager.kt — the desktop companion too).
 *
 * This talks directly to Anthropic's public Messages API using a key the
 * user pastes into Profile > AI Assistant (stored via
 * SecureCredentialStore, never bundled with the app or sent anywhere but
 * api.anthropic.com). There's no Anthropic-side proxy/backend here — if you
 * want to ship a key-free experience instead, you'd front this call with
 * your own server that holds the key, same as any other "AI feature in a
 * consumer app" architecture; this scaffold intentionally keeps it simple
 * and self-serve.
 */
class AiAssistantRepository {

    private val api: AiApi by lazy {
        val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .build()
        Retrofit.Builder()
            .baseUrl("https://api.anthropic.com/")
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(AiApi::class.java)
    }

    suspend fun ask(
        apiKey: String,
        platform: AiPlatform,
        history: List<ChatMessage>,
        userMessage: String,
        context: AiServerContext?,
        model: String = DEFAULT_MODEL
    ): Result<String> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.failure(IllegalStateException("No Anthropic API key set — add one in Profile > AI Assistant."))
        }
        runCatching {
            val request = AiMessageRequest(
                model = model,
                maxTokens = MAX_TOKENS,
                system = buildSystemPrompt(platform, context),
                messages = (history + ChatMessage(ChatRole.USER, userMessage)).map {
                    AiWireMessage(role = if (it.role == ChatRole.USER) "user" else "assistant", content = it.text)
                }
            )
            val response = api.createMessage(apiKey.trim(), request)
            if (!response.isSuccessful) {
                val bodyText = response.errorBody()?.string()
                error("Anthropic API error ${response.code()}: ${bodyText ?: response.message()}")
            }
            val body = response.body() ?: error("Empty response from Anthropic API")
            body.content.firstOrNull { it.type == "text" }?.text
                ?: error("No text content in Anthropic response (stop_reason=${body.stopReason})")
        }
    }

    private fun buildSystemPrompt(platform: AiPlatform, context: AiServerContext?): String = buildString {
        appendLine(
            "You are the built-in setup assistant inside CryptMc, an app that lets people " +
                "self-host a Minecraft server on their own device — no rented server, no third-party host."
        )
        appendLine(
            when (platform) {
                AiPlatform.ANDROID -> "The user is on the Android/tablet app. Menu names to use: " +
                    "General/Software/World/Mods & Plugins/Performance/Network/Backups/Scheduling tabs under " +
                    "a server's Settings screen, the Console screen for live output and commands, and the " +
                    "Network tab's 'Host via Phone Hotspot' section for hotspot hosting (phone/tablet only)."
                AiPlatform.DESKTOP -> "The user is on the Windows/macOS/Linux desktop companion app. It has a " +
                    "simpler surface than the phone app: a server list, Start/Stop, and a live console with a " +
                    "command box. It does not yet have the jar downloader, backups, scheduling, mod installer, " +
                    "or hotspot hosting the phone app has — hotspot hosting in particular only makes sense on " +
                    "a phone/tablet, since it uses that device's own radio to become a Wi-Fi access point."
            }
        )
        appendLine(
            "Answer concretely and briefly by default (a few sentences or a short numbered list). " +
                "Prefer the exact setting name and where to find it over generic Minecraft-server advice. " +
                "If something requires info you don't have (their router model, their ISP, a stack trace they " +
                "haven't pasted), ask one focused follow-up question instead of guessing."
        )
        if (context != null) {
            val c = context.config
            appendLine("Current server the user has open:")
            appendLine("- Name: ${c.name}, loader: ${c.loader.displayName}, MC version: ${c.minecraftVersion}, Java target: ${c.javaVersion}")
            appendLine("- RAM: ${c.minRamMb}-${c.maxRamMb} MB, max players: ${c.maxPlayers}, view distance: ${c.viewDistanceChunks} chunks")
            appendLine("- Port: ${c.serverPort}, online-mode: ${c.onlineMode}, whitelist: ${c.whitelistEnabled}, tunnel: ${c.tunnelMode}")
            appendLine("- Bedrock crossplay (Geyser): ${c.bedrockCrossplayEnabled}")
            appendLine("- EULA accepted: ${c.eulaAccepted}, jar present: ${c.jarPath != null}")
            if (context.isHotspotActive) appendLine("- This device is currently hosting a local Wi-Fi hotspot for this server.")
            context.status?.let { s ->
                appendLine("- Currently ${if (s.running) "RUNNING" else "STOPPED"}${s.publicAddress?.let { " at $it" } ?: ""}, ${s.playersOnline.size} player(s) online.")
            }
            if (context.recentConsoleLines.isNotEmpty()) {
                appendLine("Recent console output (most recent last, may help diagnose an issue):")
                context.recentConsoleLines.takeLast(MAX_CONSOLE_LINES_IN_PROMPT).forEach { appendLine("  $it") }
            }
        } else {
            appendLine("No specific server is currently open — the user may be asking about creating a new one.")
        }
    }
}
