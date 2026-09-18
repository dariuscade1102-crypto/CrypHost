package com.cryptmc.app.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PlayerSession(val playerName: String, val joinedAtEpochMs: Long, val leftAtEpochMs: Long? = null)

data class PlayerStats(
    val playerName: String,
    val totalPlaytimeMs: Long,
    val sessionCount: Int,
    val lastSeenEpochMs: Long,
    val currentlyOnline: Boolean
)

/**
 * Derives per-player playtime from join/leave console lines — same source
 * ConsoleScreen.kt's `parsePlayerList` already taps for the live roster.
 * Feed this from the same log-line stream: call [onPlayerJoined] /
 * [onPlayerLeft] wherever that screen currently just updates the roster
 * (look for the regex match on "joined the game" / "left the game").
 *
 * In-memory + not persisted across app restarts, consistent with the rest
 * of this scaffold's repositories — swap for a Room table
 * (serverId, playerName, joinedAt, leftAt) if you want stats to survive.
 */
object PlayerStatsRepository {

    private val _sessions = MutableStateFlow<Map<String, List<PlayerSession>>>(emptyMap())
    val sessions: StateFlow<Map<String, List<PlayerSession>>> = _sessions.asStateFlow()

    fun onPlayerJoined(serverId: String, playerName: String, atEpochMs: Long = System.currentTimeMillis()) {
        val current = _sessions.value[serverId].orEmpty()
        // Guard against a duplicate join line without an intervening leave (log replay, reconnect glitch)
        if (current.any { it.playerName == playerName && it.leftAtEpochMs == null }) return
        _sessions.value = _sessions.value + (serverId to (current + PlayerSession(playerName, atEpochMs)))
    }

    fun onPlayerLeft(serverId: String, playerName: String, atEpochMs: Long = System.currentTimeMillis()) {
        val current = _sessions.value[serverId].orEmpty()
        val openSessionIndex = current.indexOfLast { it.playerName == playerName && it.leftAtEpochMs == null }
        if (openSessionIndex == -1) return
        val updated = current.toMutableList()
        updated[openSessionIndex] = updated[openSessionIndex].copy(leftAtEpochMs = atEpochMs)
        _sessions.value = _sessions.value + (serverId to updated)
    }

    fun statsFor(serverId: String): List<PlayerStats> {
        val now = System.currentTimeMillis()
        return _sessions.value[serverId].orEmpty()
            .groupBy { it.playerName }
            .map { (name, playerSessions) ->
                val totalMs = playerSessions.sumOf { (it.leftAtEpochMs ?: now) - it.joinedAtEpochMs }
                PlayerStats(
                    playerName = name,
                    totalPlaytimeMs = totalMs,
                    sessionCount = playerSessions.size,
                    lastSeenEpochMs = playerSessions.maxOf { it.leftAtEpochMs ?: now },
                    currentlyOnline = playerSessions.any { it.leftAtEpochMs == null }
                )
            }
            .sortedByDescending { it.totalPlaytimeMs }
    }
}
