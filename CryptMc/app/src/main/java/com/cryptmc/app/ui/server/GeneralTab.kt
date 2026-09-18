package com.cryptmc.app.ui.server

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cryptmc.app.data.Difficulty
import com.cryptmc.app.data.Gamemode
import com.cryptmc.app.data.ServerConfig

@Composable
fun GeneralTab(config: ServerConfig, onChange: (ServerConfig) -> Unit) {
    var motdEditorOpen by remember { mutableStateOf(false) }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            SectionCard(title = "General Properties") {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.medium),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Image, contentDescription = null)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Server Icon", style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (config.iconPath != null) "Custom icon selected" else "No icon selected",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    OutlinedButton(onClick = { /* launches SAF image picker, writes result to config.iconPath */ }) {
                        Text("Select")
                    }
                }
                Divider()
                Text(config.name, modifier = Modifier.padding(vertical = 12.dp), style = MaterialTheme.typography.bodyLarge)
                Divider()
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
                ) {
                    Text(config.motd, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    IconButton(onClick = { motdEditorOpen = true }) {
                        Icon(Icons.Filled.Edit, contentDescription = "Edit MOTD")
                    }
                }
            }
        }

        item {
            SectionCard(title = "Gameplay Rules") {
                LabeledDropdown(
                    label = "Gamemode",
                    selected = config.gamemode.name.lowercase().replaceFirstChar { it.uppercase() },
                    options = Gamemode.entries.map { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } }
                ) { chosen ->
                    onChange(config.copy(gamemode = Gamemode.valueOf(chosen.uppercase())))
                }
                Spacer(Modifier.height(12.dp))
                LabeledDropdown(
                    label = "Difficulty",
                    selected = config.difficulty.name.lowercase().replaceFirstChar { it.uppercase() },
                    options = Difficulty.entries.map { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } }
                ) { chosen ->
                    onChange(config.copy(difficulty = Difficulty.valueOf(chosen.uppercase())))
                }
                Spacer(Modifier.height(4.dp))
                SettingToggleRow(
                    title = "Hardcore Mode",
                    description = "Players banned on death",
                    checked = config.hardcore,
                    onCheckedChange = { onChange(config.copy(hardcore = it)) }
                )
            }
        }
    }

    if (motdEditorOpen) {
        var text by remember { mutableStateOf(config.motd) }
        AlertDialog(
            onDismissRequest = { motdEditorOpen = false },
            title = { Text("Edit MOTD") },
            text = { OutlinedTextField(value = text, onValueChange = { text = it }, modifier = Modifier.fillMaxWidth()) },
            confirmButton = {
                TextButton(onClick = { onChange(config.copy(motd = text)); motdEditorOpen = false }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { motdEditorOpen = false }) { Text("Cancel") } }
        )
    }
}

@Composable
fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(title.uppercase(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LabeledDropdown(label: String, selected: String, options: List<String>, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            OutlinedTextField(
                value = selected,
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth()
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { option ->
                    DropdownMenuItem(text = { Text(option) }, onClick = { onSelect(option); expanded = false })
                }
            }
        }
    }
}
