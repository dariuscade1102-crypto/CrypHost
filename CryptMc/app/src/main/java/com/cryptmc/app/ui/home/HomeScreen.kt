package com.cryptmc.app.ui.home

import android.content.Intent
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.cryptmc.app.data.ServerConfig
import com.cryptmc.app.data.ServerRepository
import com.cryptmc.app.data.ServerRuntimeStatus
import com.cryptmc.app.data.TunnelMode
import com.cryptmc.app.service.ServerForegroundService

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenConsole: (String) -> Unit,
    onOpenSettings: (String) -> Unit,
    onOpenAdminDashboard: () -> Unit,
    onOpenAiAssistant: () -> Unit
) {
    val context = LocalContext.current
    val servers by ServerRepository.servers.collectAsState()
    val statuses by ServerRepository.statuses.collectAsState()
    var tunnelMenuExpanded by remember { mutableStateOf(false) }
    var selectedTunnel by remember { mutableStateOf(TunnelMode.LOCAL_ONLY) }
    // Delete is destructive (world files, backups, config) and was previously a
    // single un-confirmed tap — this holds the server pending confirmation.
    var pendingDelete by remember { mutableStateOf<ServerConfig?>(null) }
    // ServerProcessManager (inside ServerForegroundService) only ever runs
    // ONE server at a time — see its class doc — so once something is
    // running, every other card's Start needs to be disabled rather than
    // silently crashing the service when tapped.
    val anyRunning = statuses.values.any { it.running }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Dns,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("cryptmc", color = MaterialTheme.colorScheme.primary)
                    }
                },
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
                    val status = statuses[config.id] ?: ServerRuntimeStatus()
                    ServerCard(
                        config = config,
                        status = status,
                        canStart = !anyRunning || status.running,
                        onStart = {
                            if (!status.running) {
                                // This used to be onStart = { onOpenConsole(config.id) }
                                // — tapping "Start" never actually started
                                // anything, it just navigated to a console
                                // with nothing running behind it.
                                val intent = Intent(context, ServerForegroundService::class.java)
                                    .setAction(ServerForegroundService.ACTION_START)
                                    .putExtra(ServerForegroundService.EXTRA_CONFIG, config)
                                context.startForegroundService(intent)
                            }
                            onOpenConsole(config.id)
                        },
                        onStop = {
                            val intent = Intent(context, ServerForegroundService::class.java)
                                .setAction(ServerForegroundService.ACTION_STOP)
                            context.startService(intent)
                        },
                        onOpenMap = { /* opens squaremap web view if liveWorldMapEnabled */ },
                        onOpenFiles = { /* deep-links into FilesScreen scoped to this server */ },
                        onOpenSettings = { onOpenSettings(config.id) },
                        onDelete = { pendingDelete = config }
                    )
                }
            }
        }
    }

    pendingDelete?.let { config ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            icon = { Icon(Icons.Filled.Delete, contentDescription = null) },
            title = { Text("Delete \"${config.name}\"?") },
            text = { Text("This removes the server from CryptMc. This can't be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        ServerRepository.delete(config.id)
                        pendingDelete = null
                    }
                ) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun ServerCard(
    config: ServerConfig,
    status: ServerRuntimeStatus,
    canStart: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
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
                Button(onClick = onStart, modifier = Modifier.weight(1f), enabled = canStart) {
                    // Previously used Icons.Filled.Stop here paired with the
                    // text "Open Console" — an icon that promises stopping
                    // the server next to a label that just navigates away.
                    // Visibility (view) matches what the button really does.
                    Icon(if (status.running) Icons.Filled.Visibility else Icons.Filled.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(if (status.running) "Open Console" else "Start")
                }
                if (status.running) {
                    IconButton(onClick = onStop) {
                        Icon(Icons.Filled.Stop, contentDescription = "Stop server")
                    }
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
