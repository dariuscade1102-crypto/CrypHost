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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.cryptmc.app.data.ServerConfig
import com.cryptmc.app.data.ServerRepository
import com.cryptmc.app.data.ServerRuntimeStatus
import com.cryptmc.app.service.ServerForegroundService

@Composable
fun HomeScreen(
    onCreateServer: () -> Unit,
    onOpenConsole: (String) -> Unit,
    onOpenSettings: (String) -> Unit
) {
    val context = LocalContext.current
    val servers by ServerRepository.servers.collectAsState()
    val statuses by ServerRepository.statuses.collectAsState()
    var deleteTarget by remember { mutableStateOf<ServerConfig?>(null) }
    val activeServer = statuses.entries.firstOrNull { it.value.running }?.key

    Scaffold(topBar = { TopAppBar(title = { Text("CryptHost") }) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text("Minecraft servers", style = MaterialTheme.typography.headlineSmall)
                Text("One server at a time. Start it, open its console, and stop it when finished.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (servers.isEmpty()) {
                item { EmptyServerCard(onCreateServer) }
            } else {
                items(servers, key = { it.id }) { config ->
                    SimpleServerCard(
                        config = config,
                        status = statuses[config.id] ?: ServerRuntimeStatus(),
                        anotherServerRunning = activeServer != null && activeServer != config.id,
                        onStart = {
                            context.startForegroundService(
                                Intent(context, ServerForegroundService::class.java)
                                    .setAction(ServerForegroundService.ACTION_START)
                                    .putExtra(ServerForegroundService.EXTRA_CONFIG, config)
                            )
                            onOpenConsole(config.id)
                        },
                        onStop = {
                            context.startService(Intent(context, ServerForegroundService::class.java).setAction(ServerForegroundService.ACTION_STOP))
                        },
                        onConsole = { onOpenConsole(config.id) },
                        onSettings = { onOpenSettings(config.id) },
                        onDelete = { deleteTarget = config }
                    )
                }
                item {
                    OutlinedButton(onClick = onCreateServer, modifier = Modifier.fillMaxWidth()) {
                        Text("+  Add another server")
                    }
                }
            }
        }
    }

    deleteTarget?.let { config ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Remove ${config.name}?") },
            text = { Text("Only the saved CryptHost configuration is removed. Server files stay on disk.") },
            confirmButton = {
                TextButton(onClick = { ServerRepository.delete(config.id); deleteTarget = null }) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun SimpleServerCard(
    config: ServerConfig,
    status: ServerRuntimeStatus,
    anotherServerRunning: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onConsole: () -> Unit,
    onSettings: () -> Unit,
    onDelete: () -> Unit
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(config.name, style = MaterialTheme.typography.titleLarge)
            Text("${config.loader.displayName}  •  Minecraft ${config.minecraftVersion}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.size(8.dp))
            Text(
                when {
                    status.running -> "RUNNING"
                    anotherServerRunning -> "WAITING — another server is running"
                    else -> "STOPPED"
                },
                color = if (status.running) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge
            )
            Spacer(Modifier.size(12.dp))
            if (status.running) {
                Button(onClick = onStop, modifier = Modifier.fillMaxWidth()) { Text("Stop server") }
                Spacer(Modifier.size(8.dp))
                OutlinedButton(onClick = onConsole, modifier = Modifier.fillMaxWidth()) { Text("Open console") }
            } else {
                Button(onClick = onStart, enabled = !anotherServerRunning, modifier = Modifier.fillMaxWidth()) { Text("Start server") }
            }
            Spacer(Modifier.size(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onSettings, modifier = Modifier.weight(1f)) { Text("Settings") }
                TextButton(onClick = onDelete) { Text("Remove", color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

@Composable
private fun EmptyServerCard(onCreateServer: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(24.dp)) {
            Text("No server configured", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.size(6.dp))
            Text("Create a server to begin.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.size(16.dp))
            Button(onClick = onCreateServer, modifier = Modifier.fillMaxWidth()) { Text("Create server") }
        }
    }
}
