package com.cryptmc.app.ui.admin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.cryptmc.app.data.*

/**
 * Rolls every server's live status and access list into one screen. This is
 * the multi-server/multi-user control surface — HomeScreen stays the
 * single-tap "start my server" view, this is for the "who can touch what"
 * and "what's the fleet-wide state" view once more than one server or more
 * than one person is involved.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDashboardScreen(onBack: () -> Unit) {
    val servers by ServerRepository.servers.collectAsState()
    val statuses by ServerRepository.statuses.collectAsState()
    val collaboratorsByServer by AdminRepository.collaboratorsByServer.collectAsState()

    val runningCount = statuses.values.count { it.running }
    val totalPlayers = statuses.values.sumOf { it.playersOnline.size }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Admin Dashboard") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                }
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item { FleetSummaryRow(servers.size, runningCount, totalPlayers) }

            items(servers, key = { it.id }) { config ->
                ServerAdminCard(
                    config = config,
                    status = statuses[config.id] ?: ServerRuntimeStatus(),
                    collaborators = collaboratorsByServer[config.id] ?: emptyList()
                )
            }

            if (servers.isEmpty()) {
                item {
                    Text(
                        "No servers to manage yet.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun FleetSummaryRow(totalServers: Int, runningCount: Int, totalPlayers: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        SummaryStat(Modifier.weight(1f), totalServers.toString(), "Servers")
        SummaryStat(Modifier.weight(1f), "$runningCount / $totalServers", "Online")
        SummaryStat(Modifier.weight(1f), totalPlayers.toString(), "Players")
    }
}

@Composable
private fun SummaryStat(modifier: Modifier, value: String, label: String) {
    Card(modifier = modifier) {
        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.headlineSmall)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ServerAdminCard(config: ServerConfig, status: ServerRuntimeStatus, collaborators: List<Collaborator>) {
    var expanded by remember { mutableStateOf(false) }
    var showInviteDialog by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (status.running) Icons.Filled.Circle else Icons.Outlined.Circle,
                    contentDescription = null,
                    tint = if (status.running) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(10.dp)
                )
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(config.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (status.running) "Online · ${status.playersOnline.size}/${config.maxPlayers} players" else "Offline",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = "Toggle details")
                }
            }

            if (expanded) {
                Spacer(Modifier.height(12.dp))
                Divider()
                Spacer(Modifier.height(12.dp))

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Collaborators", style = MaterialTheme.typography.titleSmall)
                    TextButton(onClick = { showInviteDialog = true }) {
                        Icon(Icons.Filled.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Invite")
                    }
                }

                if (collaborators.isEmpty()) {
                    Text(
                        "Only you have access to this server.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    collaborators.forEach { collaborator ->
                        CollaboratorRow(config.id, collaborator)
                    }
                }
            }
        }
    }

    if (showInviteDialog) {
        InviteCollaboratorDialog(
            onDismiss = { showInviteDialog = false },
            onInvite = { email, role ->
                AdminRepository.addCollaborator(config.id, email, role)
                showInviteDialog = false
            }
        )
    }
}

@Composable
private fun CollaboratorRow(serverId: String, collaborator: Collaborator) {
    var roleMenuOpen by remember { mutableStateOf(false) }

    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(Modifier.weight(1f)) {
            Text(collaborator.email, style = MaterialTheme.typography.bodyMedium)
        }
        Box {
            AssistChip(
                onClick = { if (collaborator.role != CollaboratorRole.OWNER) roleMenuOpen = true },
                label = { Text(collaborator.role.label) }
            )
            DropdownMenu(expanded = roleMenuOpen, onDismissRequest = { roleMenuOpen = false }) {
                CollaboratorRole.entries.filter { it != CollaboratorRole.OWNER }.forEach { role ->
                    DropdownMenuItem(
                        text = { Text(role.label) },
                        onClick = {
                            AdminRepository.updateRole(serverId, collaborator.email, role)
                            roleMenuOpen = false
                        }
                    )
                }
            }
        }
        if (collaborator.role != CollaboratorRole.OWNER) {
            IconButton(onClick = { AdminRepository.removeCollaborator(serverId, collaborator.email) }) {
                Icon(Icons.Filled.Close, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun InviteCollaboratorDialog(onDismiss: () -> Unit, onInvite: (String, CollaboratorRole) -> Unit) {
    var email by remember { mutableStateOf("") }
    var role by remember { mutableStateOf(CollaboratorRole.VIEWER) }
    var roleMenuOpen by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Invite collaborator") },
        text = {
            Column {
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email address") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                Box {
                    OutlinedButton(onClick = { roleMenuOpen = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(role.label)
                    }
                    DropdownMenu(expanded = roleMenuOpen, onDismissRequest = { roleMenuOpen = false }) {
                        CollaboratorRole.entries.filter { it != CollaboratorRole.OWNER }.forEach { r ->
                            DropdownMenuItem(text = { Text(r.label) }, onClick = { role = r; roleMenuOpen = false })
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Viewer: read-only console. Admin: full console + settings, cannot remove you.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { if (email.isNotBlank()) onInvite(email.trim(), role) }, enabled = email.isNotBlank()) {
                Text("Invite")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
