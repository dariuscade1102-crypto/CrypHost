package com.cryptmc.desktop.ai

import com.cryptmc.desktop.DesktopServerConfig
import com.cryptmc.desktop.DesktopServerRuntimeStatus
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

data class ChatMessage(val role: ChatRole, val text: String)
enum class ChatRole { USER, ASSISTANT }

/** Mirrors app's ai/AiServerContext.kt for the simpler DesktopServerConfig shape. */
data class AiServerContext(
    val config: DesktopServerConfig,
    val status: DesktopServerRuntimeStatus?,
    val recentConsoleLines: List<String> = emptyList()
)

private const val DEFAULT_MODEL = "claude-sonnet-5"
private const val MAX_TOKENS = 1024
private const val MAX_CONSOLE_LINES_IN_PROMPT = 40

/**
 * Desktop counterpart to app's ai/AiAssistantRepository.kt — same Anthropic
 * Messages API, same "bring your own key" model (see AiSettingsStore.kt for
 * why the storage story is weaker here than the Keystore-backed Android
 * version), same idea of folding the selected server's live config into the
 * system prompt so answers are concrete.
 */
class AiAssistantManager {

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
        history: List<ChatMessage>,
        userMessage: String,
        context: AiServerContext?,
        model: String = DEFAULT_MODEL
    ): Result<String> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.failure(IllegalStateException("No Anthropic API key set — add one via the Ask AI panel."))
        }
        runCatching {
            val request = AiMessageRequest(
                model = model,
                maxTokens = MAX_TOKENS,
                system = buildSystemPrompt(context),
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

    private fun buildSystemPrompt(context: AiServerContext?): String = buildString {
        appendLine(
            "You are the built-in setup assistant inside CryptMc Desktop, the companion app to " +
                "CryptMc for Android/tablet, which lets people self-host a Minecraft server on their " +
                "own machine — no rented server, no third-party host."
        )
        appendLine(
            "This desktop app is simpler than the phone app: a server list, Start/Stop, and a live " +
                "console with a command box. It does not yet have the phone app's jar downloader, " +
                "backups, scheduling, Discord bridge, mod installer, or hotspot hosting — hotspot " +
                "hosting in particular is phone/tablet-only by design, since it uses that device's own " +
                "radio to become a Wi-Fi access point, which desktop OSes don't expose a portable API " +
                "for. If the user is on the same Wi-Fi/LAN as their players, point them at their " +
                "router's LAN IP and the server port instead — that already works with no extra setup."
        )
        appendLine(
            "Answer concretely and briefly by default (a few sentences or a short numbered list). " +
                "Prefer the exact setting/file and where to find it over generic Minecraft-server " +
                "advice. If something requires info you don't have (their router model, their ISP, a " +
                "stack trace they haven't pasted), ask one focused follow-up question instead of " +
                "guessing."
        )
        if (context != null) {
            val c = context.config
            appendLine("Current server the user has selected:")
            appendLine("- Name: ${c.name}, jar: ${c.jarFileName}, folder: ${c.workingDir}")
            appendLine("- RAM: ${c.minRamMb}-${c.maxRamMb} MB, port: ${c.serverPort}")
            appendLine("- EULA accepted: ${c.eulaAccepted}")
            context.status?.let { s ->
                appendLine("- Currently ${if (s.running) "RUNNING (pid ${s.pid})" else "STOPPED"}.")
            }
            if (context.recentConsoleLines.isNotEmpty()) {
                appendLine("Recent console output (most recent last, may help diagnose an issue):")
                context.recentConsoleLines.takeLast(MAX_CONSOLE_LINES_IN_PROMPT).forEach { appendLine("  $it") }
            }
        } else {
            appendLine("No specific server is currently selected — the user may be asking about adding one.")
        }
    }
}
