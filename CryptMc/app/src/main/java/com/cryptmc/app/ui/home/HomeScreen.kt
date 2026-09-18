package com.cryptmc.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cryptmc.app.data.ServerConfig
import com.cryptmc.app.data.ServerRepository
import com.cryptmc.app.data.ServerRuntimeStatus
import com.cryptmc.app.data.TunnelMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenConsole: (String) -> Unit,
    onOpenSettings: (String) -> Unit,
    onOpenAdminDashboard: () -> Unit,
    onOpenAiAssistant: () -> Unit
) {
    val servers by ServerRepository.servers.collectAsState()
    val statuses by ServerRepository.statuses.collectAsState()
    var tunnelMenuExpanded by remember { mutableStateOf(false) }
    var selectedTunnel by remember { mutableStateOf(TunnelMode.LOCAL_ONLY) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("cryptmc", color = MaterialTheme.colorScheme.primary) },
                actions = {
                    IconButton(onClick = onOpenAiAssistant) {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = "AI Assistant")
                    }
                    IconButton(onClick = onOpenAdminDashboard) {
                        Icon(Icons.Filled.AdminPanelSettings, contentDescription = "Admin dashboard")
                    }
                    Box {
                        AssistChip(
                            onClick = { tunnelMenuExpanded = true },
                            label = { Text("Tunnel: ${selectedTunnel.label()}") },
                            leadingIcon = { Icon(Icons.Filled.Public, contentDescription = null) }
                        )
                        DropdownMenu(expanded = tunnelMenuExpanded, onDismissRequest = { tunnelMenuExpanded = false }) {
                            TunnelMode.values().forEach { mode ->
                                DropdownMenuItem(
                                    text = { Text(mode.label()) },
                                    onClick = { selectedTunnel = mode; tunnelMenuExpanded = false }
                                )
                            }
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (servers.isEmpty()) {
            EmptyState(Modifier.padding(padding))
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(servers, key = { it.id }) { config ->
                    ServerCard(
                        config = config,
                        status = statuses[config.id] ?: ServerRuntimeStatus(),
                        onStart = { onOpenConsole(config.id) },
                        onOpenMap = { /* opens squaremap web view if liveWorldMapEnabled */ },
                        onOpenFiles = { /* deep-links into FilesScreen scoped to this server */ },
                        onOpenSettings = { onOpenSettings(config.id) },
                        onDelete = { ServerRepository.delete(config.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ServerCard(
    config: ServerConfig,
    status: ServerRuntimeStatus,
    onStart: () -> Unit,
    onOpenMap: () -> Unit,
    onOpenFiles: () -> Unit,
    onOpenSettings: () -> Unit,
    onDelete: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (status.running) Icons.Filled.Circle else Icons.Outlined.Circle,
                    contentDescription = null,
                    tint = if (status.running) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(10.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(if (status.running) "ONLINE" else "OFFLINE", style = MaterialTheme.typography.labelMedium)
            }

            Spacer(Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Server icon placeholder — real icon comes from config.iconPath
                // once the user picks one via the General tab's image picker.
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.medium),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Dns, contentDescription = null)
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(config.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${config.loader.displayName} · ${config.maxPlayers} players",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = onStart, modifier = Modifier.weight(1f)) {
                    Icon(if (status.running) Icons.Filled.Stop else Icons.Filled.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(if (status.running) "Open Console" else "Start")
                }
                IconButton(onClick = onOpenMap, enabled = config.liveWorldMapEnabled) {
                    Icon(Icons.Outlined.Map, contentDescription = "World map")
                }
                IconButton(onClick = onOpenFiles) {
                    Icon(Icons.Outlined.Folder, contentDescription = "Files")
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Outlined.Settings, contentDescription = "Settings")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Outlined.Delete, contentDescription = "Delete")
                }
            }
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Outlined.Dns, contentDescription = null, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(12.dp))
        Text("No servers yet", style = MaterialTheme.typography.titleMedium)
        Text(
            "Tap New in the bottom bar to create your first server.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun TunnelMode.label() = when (this) {
    TunnelMode.LOCAL_ONLY -> "Local"
    TunnelMode.PLAYIT_GG -> "playit.gg"
    TunnelMode.CLOUDFLARE -> "Cloudflare"
    TunnelMode.HOTSPOT -> "Hotspot"
}
