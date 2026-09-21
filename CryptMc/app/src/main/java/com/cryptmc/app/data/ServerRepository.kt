package com.cryptmc.app.data

import android.content.Context
import android.util.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.util.UUID

/** Durable local repository for server configurations and live runtime status. */
object ServerRepository {
    private const val PREFS = "cryptmc_servers"
    private const val SERVERS_KEY = "servers"

    private val _servers = MutableStateFlow<List<ServerConfig>>(emptyList())
    val servers: StateFlow<List<ServerConfig>> = _servers.asStateFlow()
    private val _statuses = MutableStateFlow<Map<String, ServerRuntimeStatus>>(emptyMap())
    val statuses: StateFlow<Map<String, ServerRuntimeStatus>> = _statuses.asStateFlow()

    @Volatile private var initialized = false
    private var prefs: android.content.SharedPreferences? = null

    fun initialize(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            _servers.value = decode<List<ServerConfig>>(prefs?.getString(SERVERS_KEY, null)) ?: listOf(defaultServer())
            initialized = true
            persist()
        }
    }

    fun get(id: String): ServerConfig? = _servers.value.firstOrNull { it.id == id }
    fun statusOf(id: String): ServerRuntimeStatus = _statuses.value[id] ?: ServerRuntimeStatus()

    fun upsert(config: ServerConfig) {
        _servers.value = _servers.value.filterNot { it.id == config.id } + config
        persist()
    }

    fun delete(id: String) {
        _servers.value = _servers.value.filterNot { it.id == id }
        _statuses.value = _statuses.value - id
        persist()
    }

    fun createDraft(name: String, loader: ServerLoader, mcVersion: String): ServerConfig {
        val safeName = name.trim().ifBlank { "Minecraft Server" }
        val config = ServerConfig(
            id = UUID.randomUUID().toString(),
            name = safeName,
            loader = loader,
            minecraftVersion = mcVersion,
            workingDir = "/servers/${safeName.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')}"
        )
        upsert(config)
        return config
    }

    fun updateStatus(id: String, transform: (ServerRuntimeStatus) -> ServerRuntimeStatus) {
        _statuses.value = _statuses.value + (id to transform(statusOf(id)))
    }

    private fun persist() {
        prefs?.edit()?.putString(SERVERS_KEY, encode(_servers.value))?.apply()
    }

    private fun defaultServer() = ServerConfig(
        id = "demo-buret-smp",
        name = "Buret SMP",
        motd = "A Minecraft Server",
        loader = ServerLoader.PAPER,
        minecraftVersion = "1.21.1",
        jarFileName = "paper-26.2-123.jar",
        maxPlayers = 10,
        maxRamMb = 4096,
        viewDistanceChunks = 32,
        simulationDistanceChunks = 10,
        bedrockCrossplayEnabled = true,
        eulaAccepted = true,
        workingDir = "/servers/buret-smp"
    )

    private fun encode(value: Any): String = runCatching {
        val bytes = ByteArrayOutputStream()
        ObjectOutputStream(bytes).use { it.writeObject(value) }
        Base64.encodeToString(bytes.toByteArray(), Base64.NO_WRAP)
    }.getOrDefault("")

    @Suppress("UNCHECKED_CAST")
    private fun <T> decode(value: String?): T? = runCatching {
        if (value.isNullOrBlank()) return null
        ObjectInputStream(ByteArrayInputStream(Base64.decode(value, Base64.DEFAULT))).use { it.readObject() as T }
    }.getOrNull()
}
