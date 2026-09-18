package com.cryptmc.app.ui.server

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cryptmc.app.data.BackupRecord
import com.cryptmc.app.data.BackupTrigger
import com.cryptmc.app.data.ServerConfig
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/**
 * Manual "back up now" + the scheduled-backup history list. Frequency and
 * retention are edited here too — actually applying them to WorkManager
 * happens via ServerScheduler.reschedule() in onScheduleChange, called from
 * the screen that hosts this tab (mirrors how onChange bubbles up in every
 * other *Tab.kt here).
 */
@Composable
fun BackupsTab(
    config: ServerConfig,
    backups: List<BackupRecord>,
    isServerRunning: Boolean,
    onChange: (ServerConfig) -> Unit,
    onBackupNow: suspend () -> Unit,
    onRestore: (BackupRecord) -> Unit,
    onDelete: (BackupRecord) -> Unit
) {
    val scope = rememberCoroutineScope()
    var backingUp by remember { mutableStateOf(false) }
    var confirmRestore by remember { mutableStateOf<BackupRecord?>(null) }
    val schedule = config.schedule

    LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            SectionCard(title = "Manual Backup") {
                if (!isServerRunning) {
                    Text(
                        "Server is offline — safest time to back up (no live world writes).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Text(
                        "Server is running. Backing up will briefly pause world saves first.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        backingUp = true
                        scope.launch {
                            onBackupNow()
                            backingUp = false
                        }
                    },
                    enabled = !backingUp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (backingUp) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Backing up…")
                    } else {
                        Icon(Icons.Filled.CloudUpload, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Back Up Now")
                    }
                }
            }
        }

        item {
            SectionCard(title = "Automatic Backups") {
                LabeledDropdown(
                    label = "Frequency",
                    selected = schedule.backupFrequency.name.replace("_", " ").lowercase()
                        .replaceFirstChar { it.uppercase() },
                    options = com.cryptmc.app.data.BackupFrequency.entries.map {
                        it.name.replace("_", " ").lowercase().replaceFirstChar { c -> c.uppercase() }
                    }
                ) { chosen ->
                    val value = com.cryptmc.app.data.BackupFrequency.valueOf(
                        chosen.uppercase().replace(" ", "_")
                    )
                    onChange(config.copy(schedule = schedule.copy(backupFrequency = value)))
                }
                Spacer(Modifier.height(12.dp))
                SettingToggleRow(
                    title = "Include Plugins/Mods",
                    description = "Larger backups, but restores a fully working install",
                    checked = schedule.backupIncludePlugins,
                    onCheckedChange = { onChange(config.copy(schedule = schedule.copy(backupIncludePlugins = it))) }
                )
                Spacer(Modifier.height(4.dp))
                Text("Keep last ${schedule.backupRetentionCount} scheduled backups", style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = schedule.backupRetentionCount.toFloat(),
                    onValueChange = {
                        onChange(config.copy(schedule = schedule.copy(backupRetentionCount = it.toInt())))
                    },
                    valueRange = 1f..20f,
                    steps = 18
                )
            }
        }

        item {
            Text("Backup History", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp))
        }

        if (backups.isEmpty()) {
            item {
                Text(
                    "No backups yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(backups, key = { it.id }) { record ->
                BackupRow(
                    record = record,
                    onRestore = { confirmRestore = record },
                    onDelete = { onDelete(record) }
                )
            }
        }
    }

    confirmRestore?.let { record ->
        AlertDialog(
            onDismissRequest = { confirmRestore = null },
            title = { Text("Restore this backup?") },
            text = {
                Text(
                    "This overwrites the current world with \"${record.fileName}\". " +
                        "Stop the server first if it's running. This can't be undone."
                )
            },
            confirmButton = {
                TextButton(onClick = { onRestore(record); confirmRestore = null }) { Text("Restore") }
            },
            dismissButton = { TextButton(onClick = { confirmRestore = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun BackupRow(record: BackupRecord, onRestore: () -> Unit, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(record.fileName, style = MaterialTheme.typography.bodyLarge)
                val dateStr = DateFormat.getDateTimeInstance().format(Date(record.createdAtEpochMs))
                val sizeStr = "%.1f MB".format(record.sizeBytes / 1024f / 1024f)
                val triggerStr = when (record.trigger) {
                    BackupTrigger.MANUAL -> "Manual"
                    BackupTrigger.SCHEDULED -> "Scheduled"
                    BackupTrigger.PRE_UPDATE -> "Pre-update"
                }
                Text(
                    "$dateStr · $sizeStr · $triggerStr",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onRestore) { Icon(Icons.Filled.Restore, contentDescription = "Restore") }
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Delete") }
        }
    }
}
