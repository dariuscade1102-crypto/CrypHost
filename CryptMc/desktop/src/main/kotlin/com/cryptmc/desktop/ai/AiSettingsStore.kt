package com.cryptmc.desktop.ai

import java.util.prefs.Preferences

/**
 * Desktop's answer to app's auth/SecureCredentialStore.kt for exactly one
 * secret: the user's own Anthropic API key for the AI Assistant panel.
 *
 * Unlike Android, there's no single cross-platform OS keystore API this
 * module can call from plain JVM code without adding a native-integration
 * dependency per OS (Keychain on macOS, DPAPI on Windows, libsecret on
 * Linux) — that's a real gap worth naming rather than glossing over.
 * `java.util.prefs.Preferences` stores this in the OS's per-user settings
 * store (registry on Windows, a plist under `~/Library/Preferences` on
 * macOS, a file under `~/.java/.userPrefs` on Linux) — better than a
 * plaintext file next to the jar, but **not encrypted at rest**. If you
 * want real encryption here, the practical options are: shell out to each
 * OS's native credential store via JNI/JNA, or require the user re-paste
 * the key each launch and hold it only in memory (safer, more annoying).
 */
object AiSettingsStore {
    private val prefs = Preferences.userNodeForPackage(AiSettingsStore::class.java)
    private const val API_KEY = "anthropic_api_key"

    fun getApiKey(): String? = prefs.get(API_KEY, null)
    fun setApiKey(key: String) = prefs.put(API_KEY, key)
    fun clearApiKey() = prefs.remove(API_KEY)
}
