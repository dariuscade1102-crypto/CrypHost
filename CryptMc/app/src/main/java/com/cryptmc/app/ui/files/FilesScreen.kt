package com.cryptmc.app.ui.files

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cryptmc.app.data.ServerRepository

/**
 * Each card here is a server's working directory (jar, world/, plugins/,
 * server.properties, logs/) rather than a generic file browser — this
 * mirrors the reference app's model of "files" as "per-server storage",
 * not an arbitrary filesystem view. Tapping into a card would push a plain
 * directory-listing screen scoped to that server's workingDir; wire that
 * with Storage Access Framework or a simple File.listFiles() walk plus
 * ACTION_VIEW for opening individual files (server.properties, logs).
 */
@Composable
fun FilesScreen() {
    val servers by ServerRepository.servers.collectAsState()
    var query by remember { mutableStateOf("") }
    var menuOpenFor by remember { mutableStateOf<String?>(null) }

    val filtered = servers.filter { it.name.contains(query, ignoreCase = true) }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Files", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            placeholder = { Text("Search files...") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(Modifier.height(12.dp))
        Text("Home", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
        HorizontalDivider(modifier = Modifier.padding(top = 8.dp))

        LazyColumn(modifier = Modifier.padding(top = 8.dp)) {
            items(filtered, key = { it.id }) { config ->
                val itemCount = 4 +
                    config.installedDatapacks.size +
                    config.installedPlugins.size // jar, world, server.properties, logs + addons

                Row(
                    Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(
                                MaterialTheme.colorScheme.primaryContainer,
                                MaterialTheme.shapes.medium
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Dns, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(config.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "$itemCount items",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Box {
                        IconButton(onClick = { menuOpenFor = config.id }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "More options")
                        }
                        DropdownMenu(
                            expanded = menuOpenFor == config.id,
                            onDismissRequest = { menuOpenFor = null }
                        ) {
                            DropdownMenuItem(text = { Text("Open in file manager") }, onClick = { menuOpenFor = null })
                            DropdownMenuItem(text = { Text("Compress to ZIP") }, onClick = { menuOpenFor = null })
                            DropdownMenuItem(text = { Text("Rename") }, onClick = { menuOpenFor = null })
                        }
                    }
                }
                HorizontalDivider()
            }
        }
    }
}
