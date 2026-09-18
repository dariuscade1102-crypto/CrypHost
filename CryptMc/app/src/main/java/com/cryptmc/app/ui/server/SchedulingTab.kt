package com.cryptmc.app.ui.server

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cryptmc.app.data.RestartFrequency
import com.cryptmc.app.data.ServerConfig

/**
 * Scheduled-restart settings. Backup scheduling lives in BackupsTab
 * alongside the manual "back up now" button and history — this tab is
 * restarts + the Discord bridge, so it's the natural home for "things that
 * happen on a timer or automatically" that aren't backups.
 */
@Composable
fun SchedulingTab(config: ServerConfig, onChange: (ServerConfig) -> Unit) {
    val schedule = config.schedule
    val bridge = config.discordBridge

    LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            SectionCard(title = "Scheduled Restarts") {
                Text(
                    "Regular restarts clear memory leaks/lag that build up over long uptimes.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                LabeledDropdown(
                    label = "Frequency",
                    selected = schedule.restartFrequency.name.replace("_", " ").lowercase()
                        .replaceFirstChar { it.uppercase() },
                    options = RestartFrequency.entries.map {
                        it.name.replace("_", " ").lowercase().replaceFirstChar { c -> c.uppercase() }
                    }
                ) { chosen ->
                    val value = RestartFrequency.valueOf(chosen.uppercase().replace(" ", "_"))
                    onChange(config.copy(schedule = schedule.copy(restartFrequency = value)))
                }
                if (schedule.restartFrequency != RestartFrequency.OFF) {
                    Spacer(Modifier.height(12.dp))
                    Text("Warn players ${schedule.restartWarningSeconds}s before restart", style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = schedule.restartWarningSeconds.toFloat(),
                        onValueChange = {
                            onChange(config.copy(schedule = schedule.copy(restartWarningSeconds = it.toInt())))
                        },
                        valueRange = 10f..300f,
                        steps = 28
                    )
                }
            }
        }

        item {
            SectionCard(title = "Discord Chat Bridge") {
                SettingToggleRow(
                    title = "Enable Bridge",
                    description = "Relay chat and join/leave to a Discord channel",
                    checked = bridge.enabled,
                    onCheckedChange = { onChange(config.copy(discordBridge = bridge.copy(enabled = it))) }
                )
                if (bridge.enabled) {
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = bridge.webhookUrl,
                        onValueChange = { onChange(config.copy(discordBridge = bridge.copy(webhookUrl = it))) },
                        label = { Text("Webhook URL") },
                        placeholder = { Text("https://discord.com/api/webhooks/…") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Discord: Channel Settings → Integrations → Webhooks → New Webhook → Copy URL",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    SettingToggleRow(
                        title = "Chat messages",
                        description = "In-game chat → Discord",
                        checked = bridge.relayChat,
                        onCheckedChange = { onChange(config.copy(discordBridge = bridge.copy(relayChat = it))) }
                    )
                    SettingToggleRow(
                        title = "Join / leave",
                        description = "Player connect/disconnect → Discord",
                        checked = bridge.relayJoinLeave,
                        onCheckedChange = { onChange(config.copy(discordBridge = bridge.copy(relayJoinLeave = it))) }
                    )
                    SettingToggleRow(
                        title = "Server start / stop",
                        description = "Online/offline status → Discord",
                        checked = bridge.relayServerStartStop,
                        onCheckedChange = { onChange(config.copy(discordBridge = bridge.copy(relayServerStartStop = it))) }
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "One-way relay only — see DiscordBridge.kt's class doc for why two-way chat " +
                            "needs a different architecture (a bot process, not this app).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
