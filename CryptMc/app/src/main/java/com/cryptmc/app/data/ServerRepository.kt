package com.cryptmc.app.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * Holds the list of configured servers plus each one's live runtime status.
 * In-memory only in this scaffold — swap for a Room database (one entity
 * table for ServerConfig, keyed by id) once you need configs to survive a
 * process death; the StateFlow-based API surface here wouldn't need to
 * change, only the backing storage inside it.
 */
object ServerRepository {

    private val _servers = MutableStateFlow<List<ServerConfig>>(
        listOf(
            ServerConfig(
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
        )
    )
    val servers: StateFlow<List<ServerConfig>> = _servers.asStateFlow()

    private val _statuses = MutableStateFlow<Map<String, ServerRuntimeStatus>>(emptyMap())
    val statuses: StateFlow<Map<String, ServerRuntimeStatus>> = _statuses.asStateFlow()

    fun get(id: String): ServerConfig? = _servers.value.firstOrNull { it.id == id }

    fun statusOf(id: String): ServerRuntimeStatus = _statuses.value[id] ?: ServerRuntimeStatus()

    fun upsert(config: ServerConfig) {
        _servers.value = _servers.value
            .filterNot { it.id == config.id }
            .plus(config)
    }

    fun delete(id: String) {
        _servers.value = _servers.value.filterNot { it.id == id }
        _statuses.value = _statuses.value - id
    }

    fun createDraft(name: String, loader: ServerLoader, mcVersion: String): ServerConfig {
        val id = UUID.randomUUID().toString()
        val config = ServerConfig(
            id = id,
            name = name,
            loader = loader,
            minecraftVersion = mcVersion,
            workingDir = "/servers/${name.lowercase().replace(" ", "-")}"
        )
        upsert(config)
        return config
    }

    fun updateStatus(id: String, transform: (ServerRuntimeStatus) -> ServerRuntimeStatus) {
        val current = statusOf(id)
        _statuses.value = _statuses.value + (id to transform(current))
    }
}
