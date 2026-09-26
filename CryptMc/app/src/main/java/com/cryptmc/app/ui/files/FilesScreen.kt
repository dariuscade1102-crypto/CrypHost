package com.cryptmc.app.ui.files

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cryptmc.app.data.ServerRepository
import java.io.File

@Composable
fun FilesScreen(onOpenServer: (String) -> Unit = {}) {
    val servers by ServerRepository.servers.collectAsState()
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Files", style = MaterialTheme.typography.headlineMedium)
        Text("Choose a server to browse its working directory.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.size(16.dp))
        if (servers.isEmpty()) {
            Text("No servers configured yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(servers, key = { it.id }) { config ->
                    Card(Modifier.fillMaxWidth().clickable { onOpenServer(config.id) }) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Dns, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(config.name, style = MaterialTheme.typography.titleMedium)
                                Text(config.workingDir, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                            }
                            Text("Open", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ServerFilesScreen(serverId: String, onBack: () -> Unit) {
    val servers by ServerRepository.servers.collectAsState()
    val config = servers.firstOrNull { it.id == serverId }
    val entries = config?.workingDir?.let { File(it).listFiles()?.sortedWith(compareByDescending<File> { it.isDirectory }.thenBy { it.name.lowercase() }) } ?: emptyList()
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Files", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            Text("Back", color = MaterialTheme.colorScheme.primary, modifier = Modifier.clickable(onClick = onBack).padding(8.dp))
        }
        Text(config?.name ?: "Server unavailable", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.size(16.dp))
        if (config == null) {
            Text("This server no longer exists.")
        } else if (entries.isEmpty()) {
            Text("Directory is empty or unavailable.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(entries, key = { it.absolutePath }) { entry ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (entry.isDirectory) Icons.Filled.Folder else Icons.Filled.InsertDriveFile, contentDescription = null)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(entry.name)
                            Text(if (entry.isDirectory) "Directory" else "${entry.length()} bytes", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}
