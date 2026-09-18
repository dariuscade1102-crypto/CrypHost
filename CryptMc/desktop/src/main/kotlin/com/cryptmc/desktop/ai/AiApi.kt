package com.cryptmc.desktop.ai

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST

/**
 * Same shape as app's network/AiApi.kt. Deliberately duplicated rather than
 * shared — see :desktop's build.gradle.kts class doc on why `:app` and
 * `:desktop` don't share source yet (no `:core` KMP module). Note this
 * module has no ksp/kapt configured, so @JsonClass(generateAdapter = true)
 * here is inert (no generated adapter is compiled) — Moshi falls back to
 * KotlinJsonAdapterFactory's reflection-based adapter instead, which is set
 * up in AiAssistantManager. That's fine for this call volume; if you add
 * ksp to :desktop later these annotations start paying off for free.
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
