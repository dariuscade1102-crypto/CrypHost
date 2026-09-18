package com.cryptmc.app.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Backs the admin dashboard's per-server collaborator lists. In-memory only
 * in this scaffold, same caveat as ServerRepository — move to Room (a
 * ServerId+Email-keyed table) once this needs to survive a process death,
 * or to a backend table if collaborators should be visible across devices
 * (which requires the backend session layer noted in GoogleAuthManager's
 * class doc — an invited collaborator's own device needs some server-side
 * source of truth for "which servers can I see," not just this device's
 * local state).
 */
object AdminRepository {

    private val _collaboratorsByServer = MutableStateFlow<Map<String, List<Collaborator>>>(emptyMap())
    val collaboratorsByServer: StateFlow<Map<String, List<Collaborator>>> = _collaboratorsByServer.asStateFlow()

    fun collaboratorsFor(serverId: String): List<Collaborator> =
        _collaboratorsByServer.value[serverId] ?: emptyList()

    fun addCollaborator(serverId: String, email: String, role: CollaboratorRole) {
        val current = collaboratorsFor(serverId)
        if (current.any { it.email.equals(email, ignoreCase = true) }) return
        _collaboratorsByServer.value = _collaboratorsByServer.value +
            (serverId to (current + Collaborator(email, role)))
    }

    fun updateRole(serverId: String, email: String, role: CollaboratorRole) {
        val updated = collaboratorsFor(serverId).map {
            if (it.email.equals(email, ignoreCase = true)) it.copy(role = role) else it
        }
        _collaboratorsByServer.value = _collaboratorsByServer.value + (serverId to updated)
    }

    fun removeCollaborator(serverId: String, email: String) {
        val updated = collaboratorsFor(serverId).filterNot { it.email.equals(email, ignoreCase = true) }
        _collaboratorsByServer.value = _collaboratorsByServer.value + (serverId to updated)
    }

    fun clearServer(serverId: String) {
        _collaboratorsByServer.value = _collaboratorsByServer.value - serverId
    }
}
