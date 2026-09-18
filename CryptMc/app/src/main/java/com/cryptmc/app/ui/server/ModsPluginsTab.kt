package com.cryptmc.app.ui.server

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cryptmc.app.data.InstalledAddon
import com.cryptmc.app.data.ServerConfig
import com.cryptmc.app.network.ModrinthHit
import com.cryptmc.app.network.ModrinthRepository
import kotlinx.coroutines.launch

private enum class AddonKind { DATAPACKS, PLUGINS }

/**
 * Requirement #5's UI surface — one-tap install straight from Modrinth.
 * Search debouncing/paging omitted for scope; wire up
 * androidx.compose.runtime.snapshotFlow + delay() on searchQuery if the
 * live API call needs throttling once this is hooked to a real network.
 */
@Composable
fun ModsPluginsTab(config: ServerConfig, onChange: (ServerConfig) -> Unit) {
    var kind by remember { mutableStateOf(AddonKind.DATAPACKS) }
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<ModrinthHit>>(emptyList()) }
    val repository = remember { ModrinthRepository() }
    val scope = rememberCoroutineScope()

    val installedList = if (kind == AddonKind.DATAPACKS) config.installedDatapacks else config.installedPlugins

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Plugins & Mods", style = MaterialTheme.typography.titleMedium)
        }

        TabRow(selectedTabIndex = kind.ordinal) {
            Tab(selected = kind == AddonKind.DATAPACKS, onClick = { kind = AddonKind.DATAPACKS }, text = { Text("Datapacks") })
            Tab(selected = kind == AddonKind.PLUGINS, onClick = { kind = AddonKind.PLUGINS }, text = { Text("Plugins") })
        }

        Spacer(Modifier.height(8.dp))

        LazyColumn(modifier = Modifier.weight(1f)) {
            if (installedList.isNotEmpty()) {
                item { Text("INSTALLED", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                items(installedList, key = { it.fileName }) { addon ->
                    InstalledAddonRow(addon) {
                        val updated = if (kind == AddonKind.DATAPACKS)
                            config.copy(installedDatapacks = config.installedDatapacks - addon)
                        else
                            config.copy(installedPlugins = config.installedPlugins - addon)
                        onChange(updated)
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { query ->
                        searchQuery = query
                        scope.launch {
                            if (query.length >= 2) {
                                searchResults = runCatching {
                                    repository.search(
                                        query = query,
                                        projectType = if (kind == AddonKind.DATAPACKS) "datapack" else "plugin",
                                        mcVersion = config.minecraftVersion
                                    )
                                }.getOrDefault(emptyList())
                            }
                        }
                    },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    placeholder = { Text("Search ${kind.name.lowercase()} on Modrinth") },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                )
            }

            items(searchResults, key = { it.projectId }) { hit ->
                SearchResultRow(hit) {
                    scope.launch {
                        val destSubdir = if (kind == AddonKind.DATAPACKS) "datapacks" else "plugins"
                        val file = runCatching {
                            repository.installToServer(
                                projectSlugOrId = hit.slug,
                                mcVersion = config.minecraftVersion,
                                loader = config.loader.name.lowercase(),
                                destinationDir = java.io.File("${config.workingDir}/$destSubdir")
                            )
                        }.getOrNull() ?: return@launch

                        val addon = InstalledAddon(name = hit.title, fileName = file.name, modrinthProjectId = hit.projectId)
                        val updated = if (kind == AddonKind.DATAPACKS)
                            config.copy(installedDatapacks = config.installedDatapacks + addon)
                        else
                            config.copy(installedPlugins = config.installedPlugins + addon)
                        onChange(updated)
                    }
                }
            }
        }
    }
}

@Composable
private fun InstalledAddonRow(addon: InstalledAddon, onRemove: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(addon.name, style = MaterialTheme.typography.titleSmall)
            Text(addon.fileName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        OutlinedIconButton(onClick = onRemove, colors = IconButtonDefaults.outlinedIconButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
            Icon(Icons.Filled.Close, contentDescription = "Remove")
        }
    }
}

@Composable
private fun SearchResultRow(hit: ModrinthHit, onInstall: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            Text(hit.title, style = MaterialTheme.typography.titleSmall)
            Text(
                hit.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2
            )
        }
        OutlinedIconButton(onClick = onInstall) {
            Icon(Icons.Filled.Download, contentDescription = "Install")
        }
    }
}
