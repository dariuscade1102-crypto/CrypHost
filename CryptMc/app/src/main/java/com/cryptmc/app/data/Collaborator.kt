package com.cryptmc.app.data

/**
 * Per-server access control for the admin dashboard. This is app-level
 * (who can open the console / change settings for a server in THIS app),
 * separate from in-game Minecraft ops — a Collaborator with VIEWER never
 * touches server.properties or the console, regardless of their in-game
 * op status.
 */
enum class CollaboratorRole(val label: String) {
    OWNER("Owner"),       // created the server; only role that can remove other collaborators
    ADMIN("Admin"),       // full console + settings access, cannot remove the Owner
    VIEWER("Viewer")      // console read-only (no command input), no settings access
}

data class Collaborator(
    val email: String,
    val role: CollaboratorRole,
    val addedAtEpochMillis: Long = System.currentTimeMillis()
)
