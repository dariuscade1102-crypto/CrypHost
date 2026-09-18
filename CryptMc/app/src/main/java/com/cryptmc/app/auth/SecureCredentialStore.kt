package com.cryptmc.app.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Backs every password this app stores locally — RCON passwords, any future
 * admin-panel credentials — with a key in the Android Keystore (hardware-
 * backed on most devices), never plaintext SharedPreferences or a plain
 * file. Values are encrypted at rest and only decryptable by this app on
 * this device; there's no cloud sync of these, by design — a server's RCON
 * password only ever needs to exist where the server itself runs.
 */
class SecureCredentialStore(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "cryptmc_secure_credentials",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun setRconPassword(serverId: String, password: String) {
        prefs.edit().putString(rconKey(serverId), password).apply()
    }

    fun getRconPassword(serverId: String): String? = prefs.getString(rconKey(serverId), null)

    fun clearRconPassword(serverId: String) {
        prefs.edit().remove(rconKey(serverId)).apply()
    }

    /** Generic slot for anything else that shouldn't touch ServerConfig
     *  (which, unlike this store, isn't encrypted — see README note). */
    fun setSecret(key: String, value: String) = prefs.edit().putString(key, value).apply()
    fun getSecret(key: String): String? = prefs.getString(key, null)
    fun clearSecret(key: String) = prefs.edit().remove(key).apply()

    /**
     * The user's own Anthropic API key, pasted into Profile > AI Assistant,
     * used by ai/AiAssistantRepository.kt. Same Keystore-backed encryption
     * as the RCON password above — never bundled with the app, never synced.
     */
    fun setAnthropicApiKey(key: String) = setSecret(ANTHROPIC_API_KEY, key)
    fun getAnthropicApiKey(): String? = getSecret(ANTHROPIC_API_KEY)
    fun clearAnthropicApiKey() = clearSecret(ANTHROPIC_API_KEY)

    /**
     * Which AI backend (cloud API vs downloaded on-device model) the user
     * has picked in Profile > AI Assistant. Not a secret, but this store
     * is the existing per-user local-settings home for the AI feature, so
     * it lives alongside the API key rather than starting a new file.
     */
    fun setAiEngineMode(mode: String) = setSecret(AI_ENGINE_MODE, mode)
    fun getAiEngineMode(): String? = getSecret(AI_ENGINE_MODE)

    private fun rconKey(serverId: String) = "rcon_password:$serverId"

    private companion object {
        const val ANTHROPIC_API_KEY = "anthropic_api_key"
        const val AI_ENGINE_MODE = "ai_engine_mode"
    }
}
