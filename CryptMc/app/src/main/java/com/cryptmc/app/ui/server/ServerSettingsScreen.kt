package com.cryptmc.app.ui.server

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cryptmc.app.data.BackupRecord
import com.cryptmc.app.data.BackupTrigger
import com.cryptmc.app.data.ServerConfig
import com.cryptmc.app.data.ServerRepository
import com.cryptmc.app.server.BackupManager
import com.cryptmc.app.server.ServerScheduler
import androidx.compose.runtime.collectAsState

private enum class SettingsTab(val label: String) {
    GENERAL("General"), SOFTWARE("Software"), WORLD("World"),
    MODS_PLUGINS("Mods & Plugins"), PERFORMANCE("Performance"), NETWORK("Network"),
    BACKUPS("Backups"), SCHEDULING("Scheduling")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerSettingsScreen(serverId: String, onBack: () -> Unit, onAskAi: () -> Unit = {}) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val servers by ServerRepository.servers.collectAsState()
    val statuses by ServerRepository.statuses.collectAsState()
    val backups by BackupManager.backups.collectAsState()
    val config = servers.firstOrNull { it.id == serverId } ?: run {
        onBack(); return
    }
    var tab by remember { mutableStateOf(SettingsTab.GENERAL) }
    var draft by remember(config.id) { mutableStateOf(config) }

    fun applyChange(updated: ServerConfig) {
        // Backup/restart cadence takes effect immediately rather than waiting for
        // "UPDATE SERVER" below, since a WorkManager job left stale until save
        // would silently keep running the old schedule.
        val scheduleChanged = updated.schedule != draft.schedule
        draft = updated
        if (scheduleChanged) {
            ServerRepository.upsert(updated)
            ServerScheduler.reschedule(context, serverId)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(config.name) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    IconButton(onClick = onAskAi) {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = "Ask AI about this server")
                    }
                }
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Button(
                    onClick = { ServerRepository.upsert(draft); onBack() },
                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                ) { Text("UPDATE SERVER") }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            ScrollableTabRow(selectedTabIndex = tab.ordinal, edgePadding = 16.dp) {
                SettingsTab.entries.forEach { t ->
                    Tab(selected = tab == t, onClick = { tab = t }, text = { Text(t.label) })
                }
            }
            Box(modifier = Modifier.weight(1f).padding(16.dp)) {
                when (tab) {
                    SettingsTab.GENERAL -> GeneralTab(draft) { draft = it }
                    SettingsTab.SOFTWARE -> SoftwareTab(draft) { draft = it }
                    SettingsTab.WORLD -> WorldTab(draft) { draft = it }
                    SettingsTab.MODS_PLUGINS -> ModsPluginsTab(draft) { draft = it }
                    SettingsTab.PERFORMANCE -> PerformanceTab(draft) { draft = it }
                    SettingsTab.NETWORK -> NetworkTab(draft) { draft = it }
                    SettingsTab.BACKUPS -> BackupsTab(
                        config = draft,
                        backups = backups.filter { it.serverId == serverId },
                        isServerRunning = statuses[serverId]?.running == true,
                        onChange = { applyChange(it) },
                        onBackupNow = {
                            BackupManager.createBackup(draft, draft.schedule.backupIncludePlugins, BackupTrigger.MANUAL)
                        },
                        onRestore = { record ->
                            BackupManager.restoreBackup(record, draft.workingDir)
                        },
                        onDelete = { record -> BackupManager.deleteBackup(record) }
                    )
                    SettingsTab.SCHEDULING -> SchedulingTab(draft) { applyChange(it) }
                }
            }
        }
    }
}

/** Shared row style used across every settings tab for a toggle + description. */
@Composable
fun SettingToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

typealias ConfigUpdate = (ServerConfig) -> Unit
