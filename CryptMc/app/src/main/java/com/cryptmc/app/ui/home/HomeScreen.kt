package com.cryptmc.app.ui.home

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.cryptmc.app.data.ServerConfig
import com.cryptmc.app.data.ServerRepository
import com.cryptmc.app.data.ServerRuntimeStatus
import com.cryptmc.app.service.ServerForegroundService

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onCreateServer: () -> Unit,
    onOpenConsole: (String) -> Unit,
    onOpenSettings: (String) -> Unit,
    onOpenFiles: (String) -> Unit,
    onOpenAdminDashboard: () -> Unit = {},
    onOpenAiAssistant: () -> Unit = {}
) {
    val context = LocalContext.current
    val servers by ServerRepository.servers.collectAsState()
    val statuses by ServerRepository.statuses.collectAsState()
    var pendingDelete by remember { mutableStateOf<ServerConfig?>(null) }
    val anyRunning = statuses.values.any { it.running }

    Scaffold(topBar = { TopAppBar(title = { Text("CryptHost") }) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Spacer(Modifier.size(8.dp))
                Text("Servers", style = MaterialTheme.typography.headlineSmall)
                Text("Start, monitor, and configure your Minecraft servers.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (servers.isEmpty()) {
                item { EmptyState(onCreateServer) }
            } else {
                items(servers, key = { it.id }) { config ->
                    ServerCard(
                        config = config,
                        status = statuses[config.id] ?: ServerRuntimeStatus(),
                        canStart = !anyRunning || statuses[config.id]?.running == true,
                        onStart = {
                            val intent = Intent(context, ServerForegroundService::class.java)
                                .setAction(ServerForegroundService.ACTION_START)
                                .putExtra(ServerForegroundService.EXTRA_CONFIG, config)
                            context.startForegroundService(intent)
                            onOpenConsole(config.id)
                        },
                        onStop = {
                            context.startService(Intent(context, ServerForegroundService::class.java).setAction(ServerForegroundService.ACTION_STOP))
                        },
                        onConsole = { onOpenConsole(config.id) },
                        onFiles = { onOpenFiles(config.id) },
                        onSettings = { onOpenSettings(config.id) },
                        onDelete = { pendingDelete = config }
                    )
                }
                item {
                    OutlinedButton(onClick = onCreateServer, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Add server")
                    }
                }
            }
            item { Spacer(Modifier.size(20.dp)) }
        }
    }

    pendingDelete?.let { config ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Remove ${config.name}?") },
            text = { Text("This removes the server configuration from CryptHost. Server files are not deleted.") },
            confirmButton = {
                TextButton(onClick = { ServerRepository.delete(config.id); pendingDelete = null }) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } }
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
    onConsole: () -> Unit,
    onFiles: () -> Unit,
    onSettings: () -> Unit,
    onDelete: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Dns, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(config.name, style = MaterialTheme.typography.titleLarge)
                    Text("${config.loader.displayName} · Minecraft ${config.minecraftVersion}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(if (status.running) "ONLINE" else "OFFLINE", style = MaterialTheme.typography.labelMedium, color = if (status.running) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.size(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = if (status.running) onConsole else onStart, enabled = status.running || canStart, modifier = Modifier.weight(1f)) {
                    Icon(if (status.running) Icons.Filled.Terminal else Icons.Filled.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(if (status.running) "Console" else "Start")
                }
                if (status.running) {
                    IconButton(onClick = onStop) { Icon(Icons.Filled.Stop, contentDescription = "Stop") }
                }
                IconButton(onClick = onFiles) { Icon(Icons.Filled.Folder, contentDescription = "Files") }
                IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, contentDescription = "Settings") }
                IconButton(onClick = onDelete) { Icon(Icons.Filled.DeleteOutline, contentDescription = "Remove") }
            }
        }
    }
}

@Composable
private fun EmptyState(onCreateServer: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Filled.Dns, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.size(12.dp))
            Text("No servers yet", style = MaterialTheme.typography.titleMedium)
            Text("Create one to get started.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.size(16.dp))
            Button(onClick = onCreateServer) { Text("Create server") }
        }
    }
}
