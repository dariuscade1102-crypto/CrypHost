package com.cryptmc.app.network

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST

/**
 * Thin client for Anthropic's Messages API (docs.claude.com/en/api/messages),
 * used to power the in-app "Ask AI" setup assistant on both the phone app
 * and the desktop companion (see desktop's own ai/AiApi.kt — intentionally
 * duplicated rather than shared, same reasoning as ServerConfig: no :core
 * module exists yet, see build.gradle.kts' class doc).
 *
 * The user supplies their own Anthropic API key (Profile > AI Assistant),
 * stored only in SecureCredentialStore's Keystore-backed prefs — this app
 * ships with no key of its own and makes no calls until one is entered.
 */
interface AiApi {
    @Headers("anthropic-version: 2023-06-01", "content-type: application/json")
    @POST("v1/messages")
    suspend fun createMessage(
        @Header("x-api-key") apiKey: String,
        @Body request: AiMessageRequest
    ): Response<AiMessageResponse>
}

@JsonClass(generateAdapter = true)
data class AiMessageRequest(
    val model: String,
    @Json(name = "max_tokens") val maxTokens: Int,
    val system: String,
    val messages: List<AiWireMessage>
)

@JsonClass(generateAdapter = true)
data class AiWireMessage(
    val role: String,      // "user" | "assistant"
    val content: String
)

@JsonClass(generateAdapter = true)
data class AiMessageResponse(
    val id: String? = null,
    val content: List<AiContentBlock> = emptyList(),
    @Json(name = "stop_reason") val stopReason: String? = null
)

@JsonClass(generateAdapter = true)
data class AiContentBlock(
    val type: String,
    val text: String? = null
)

@JsonClass(generateAdapter = true)
data class AiErrorEnvelope(
    val type: String? = null,
    val error: AiErrorDetail? = null
)

@JsonClass(generateAdapter = true)
data class AiErrorDetail(
    val type: String? = null,
    val message: String? = null
)
