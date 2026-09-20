package com.cryptmc.app.ui.server

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cryptmc.app.data.ServerConfig
import com.cryptmc.app.data.WorldType

/**
 * "Backup World" zips workingDir/world (+world_nether/world_the_end for
 * vanilla-layout servers) via java.util.zip and hands the result to
 * Android's Storage Access Framework CREATE_DOCUMENT intent so the user
 * picks where it's saved — no custom zip format needed since Anvil-style
 * apps and Paper both just expect a standard region-file world folder.
 * "Backup to Google Drive" reuses the same zip, then uploads it through the
 * user's already-connected Drive account via the Drive REST API's
 * files.create (multipart upload) — needs the drive.file OAuth scope only,
 * not full Drive access, since it only touches files this app created.
 */
@Composable
fun WorldTab(config: ServerConfig, onChange: (ServerConfig) -> Unit) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            SectionCard(title = "World") {
                LabeledDropdown(
                    label = "World Type",
                    selected = config.worldType.name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() },
                    options = WorldType.entries.map { it.name.lowercase().replace('_', ' ').replaceFirstChar { c -> c.uppercase() } }
                ) { chosen ->
                    onChange(config.copy(worldType = WorldType.valueOf(chosen.uppercase().replace(' ', '_'))))
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = config.worldSeed,
                    onValueChange = { onChange(config.copy(worldSeed = it)) },
                    label = { Text("World seed") },
                    placeholder = { Text("Leave blank for random") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        item {
            SectionCard(title = "") {
                ActionRow(
                    icon = Icons.Filled.Save,
                    tint = MaterialTheme.colorScheme.primary,
                    title = "Backup World",
                    subtitle = "Export world as a ZIP archive",
                    onClick = { /* zip workingDir/world*, hand to SAF CREATE_DOCUMENT */ }
                )
                Divider()
                ActionRow(
                    icon = Icons.Filled.FolderOpen,
                    tint = MaterialTheme.colorScheme.tertiary,
                    title = "Import World",
                    subtitle = "Replace world from a ZIP file",
                    onClick = { /* SAF OPEN_DOCUMENT, unzip over workingDir/world* */ }
                )
                Divider()
                ActionRow(
                    icon = Icons.Filled.CloudUpload,
                    tint = MaterialTheme.colorScheme.secondary,
                    title = "Backup to Google Drive",
                    subtitle = "Upload world backup to Google Drive",
                    onClick = { /* Drive REST API files.create, drive.file scope */ }
                )
            }
        }

        item {
            SectionCard(title = "") {
                SettingToggleRow(
                    title = "Live world map",
                    description = "Renders your world as a live map on a built-in web page. Best on high-end devices — it uses extra storage and CPU.",
                    checked = config.liveWorldMapEnabled,
                    onCheckedChange = { onChange(config.copy(liveWorldMapEnabled = it)) }
                )
            }
        }
    }
}

@Composable
private fun ActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: androidx.compose.ui.graphics.Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickableRow(onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = tint)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Filled.ChevronRight, contentDescription = null)
    }
}

private fun Modifier.clickableRow(onClick: () -> Unit): Modifier =
    this.then(androidx.compose.foundation.clickable(onClick = onClick))
