package com.cryptmc.app.ui.server

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cryptmc.app.data.ServerConfig

/**
 * "Select JAR" launches Android's Storage Access Framework document picker
 * (Intent.ACTION_OPEN_DOCUMENT, mime type application/java-archive) rather
 * than any in-app file browser — the actual copy-into-workingDir/jar-path
 * logic is a few lines in the Activity's registerForActivityResult callback,
 * omitted here since it's boilerplate rather than architecture.
 */
@Composable
fun SoftwareTab(config: ServerConfig, onChange: (ServerConfig) -> Unit) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            SectionCard(title = "") {
                SettingToggleRow(
                    title = "Agree to EULA",
                    description = "By enabling, you agree to the Minecraft EULA",
                    checked = config.eulaAccepted,
                    onCheckedChange = { onChange(config.copy(eulaAccepted = it)) }
                )
            }
        }

        item {
            SectionCard(title = "Software") {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text("JAR File", style = MaterialTheme.typography.titleMedium)
                        Text(
                            config.jarFileName ?: "None selected",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    OutlinedButton(onClick = { /* SAF picker, see note above */ }) {
                        Icon(Icons.Filled.Folder, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Select JAR")
                    }
                }
                Divider(modifier = Modifier.padding(vertical = 12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Java Version", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Java ${config.javaVersion} recommended for ${config.minecraftVersion}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    LabeledDropdown(
                        label = "",
                        selected = "Java ${config.javaVersion}",
                        options = listOf("Java 17", "Java 21", "Java 25")
                    ) { chosen ->
                        onChange(config.copy(javaVersion = chosen.filter { it.isDigit() }.toInt()))
                    }
                }
            }
        }

        item {
            SectionCard(title = "") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Java Flags", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.width(8.dp))
                    AssistChip(onClick = {}, enabled = false, label = { Text("ADVANCED") })
                }
                Text(
                    "Extra JVM flags added before -jar",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = config.javaFlags,
                    onValueChange = { onChange(config.copy(javaFlags = it)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )
            }
        }

        item {
            SectionCard(title = "") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Program Arguments", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.width(8.dp))
                    AssistChip(onClick = {}, enabled = false, label = { Text("ADVANCED") })
                }
                Text(
                    "Extra args appended after nogui",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = config.programArguments,
                    onValueChange = { onChange(config.copy(programArguments = it)) },
                    placeholder = { Text("--some-arg value") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
