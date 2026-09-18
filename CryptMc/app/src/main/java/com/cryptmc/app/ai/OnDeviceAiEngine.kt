package com.cryptmc.app.ai

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInference.LlmInferenceOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Which AI backend the assistant talks to. CLOUD_API is
 * ai/AiAssistantRepository's existing Anthropic Messages API call — needs
 * network + a pasted key. ON_DEVICE runs a downloaded model entirely
 * locally via MediaPipe's LLM Inference API (no network at inference time,
 * no key, but needs the one-time model download below and noticeably more
 * RAM/storage than the cloud path).
 */
enum class AiEngineMode { CLOUD_API, ON_DEVICE }

/**
 * Wraps a downloaded `.task` model bundle (MediaPipe's packaging format for
 * on-device LLMs — a single file bundling weights + tokenizer) with
 * MediaPipe's LlmInference API.
 *
 * This is the "downloadable" counterpart to the Anthropic API path:
 * everything runs on-device once the model file is present, so there's no
 * per-message network call and no API key. The tradeoff, same shape as the
 * embedded-JRE requirement in the README, is that the model itself is a
 * large (1.5-4 GB depending on which one) binary this scaffold can't ship —
 * you point [ModelDownloader] at a URL for a `.task`-format build.
 *
 * Model sourcing: Google publishes ready-to-use `.task` builds of Gemma
 * (e.g. "Gemma 3 1B/2B, .task format") on Kaggle/Hugging Face for exactly
 * this API — search "MediaPipe Gemma task file". Those require accepting
 * Gemma's license and, on Hugging Face, a logged-in download URL (same
 * "user provides the binary" pattern as the JRE/playit-agent assets — see
 * README). Any other model converted to MediaPipe's `.task` format works
 * the same way.
 */
class OnDeviceAiEngine(private val context: Context) {

    private var llmInference: LlmInference? = null
    private var loadedModelPath: String? = null

    /** True once a model file exists on disk (doesn't mean it's loaded into memory yet). */
    fun isModelDownloaded(): Boolean = modelFile().exists()

    fun modelFile(): File = File(context.filesDir, "ai/on-device-model.task")

    fun deleteModel() {
        close()
        modelFile().delete()
    }

    private fun ensureLoaded(): LlmInference {
        val path = modelFile().absolutePath
        val current = llmInference
        if (current != null && loadedModelPath == path) return current

        current?.close()
        val options = LlmInferenceOptions.builder()
            .setModelPath(path)
            .setMaxTokens(1024)
            .build()
        return LlmInference.createFromOptions(context, options).also {
            llmInference = it
            loadedModelPath = path
        }
    }

    suspend fun ask(
        platform: AiPlatform,
        history: List<ChatMessage>,
        userMessage: String,
        serverContext: AiServerContext?
    ): Result<String> = withContext(Dispatchers.IO) {
        if (!isModelDownloaded()) {
            return@withContext Result.failure(
                IllegalStateException("No on-device model downloaded yet — see Profile > AI Assistant > On-device.")
            )
        }
        runCatching {
            val engine = ensureLoaded()
            // The MediaPipe LLM Inference API (as of tasks-genai 0.10.x) takes a
            // single prompt string per session rather than a structured chat
            // history param, so history + the system framing are flattened here
            // the same way AiAssistantRepository.buildSystemPrompt frames it for
            // the cloud path, just inlined into one prompt string.
            val prompt = buildOnDevicePrompt(platform, history, userMessage, serverContext)
            engine.generateResponse(prompt)
        }
    }

    fun close() {
        llmInference?.close()
        llmInference = null
        loadedModelPath = null
    }
}

private fun buildOnDevicePrompt(
    platform: AiPlatform,
    history: List<ChatMessage>,
    userMessage: String,
    serverContext: AiServerContext?
): String = buildString {
    appendLine(
        "You are the built-in setup assistant inside CryptMc, an app for self-hosting a " +
            "Minecraft server on-device. Answer concretely and briefly."
    )
    if (serverContext != null) {
        val c = serverContext.config
        appendLine("Current server: ${c.name}, loader ${c.loader.displayName}, MC ${c.minecraftVersion}, RAM ${c.minRamMb}-${c.maxRamMb}MB, port ${c.serverPort}.")
    }
    history.forEach { appendLine("${if (it.role == ChatRole.USER) "User" else "Assistant"}: ${it.text}") }
    appendLine("User: $userMessage")
    append("Assistant:")
}
