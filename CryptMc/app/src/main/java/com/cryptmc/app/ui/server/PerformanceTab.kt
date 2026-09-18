package com.cryptmc.app.ui.server

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.cryptmc.app.data.ServerConfig

@Composable
fun PerformanceTab(config: ServerConfig, onChange: (ServerConfig) -> Unit) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            SectionCard(title = "Performance") {
                SettingToggleRow(
                    title = "Performance Mode",
                    description = "Allocates more memory up front for smoother, more consistent performance.",
                    checked = config.performanceModeEnabled,
                    onCheckedChange = { onChange(config.copy(performanceModeEnabled = it)) }
                )
                WarningNote("Only recommended for devices with plenty of free memory. On low-RAM devices, the system may stop the server if it runs out of memory.")

                Divider(modifier = Modifier.padding(vertical = 8.dp))

                SettingToggleRow(
                    title = "Optimize for phone",
                    description = "Tunes game settings (mobs, redstone, autosave) to run smoother on your phone",
                    checked = config.optimizeForPhone,
                    onCheckedChange = { onChange(config.copy(optimizeForPhone = it)) }
                )
            }
        }

        item {
            SectionCard(title = "") {
                Text(
                    "Allocating too much RAM may cause your phone to freeze or become unresponsive.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                LabeledSlider(
                    label = "Allocated RAM",
                    valueLabel = "${"%.1f".format(config.maxRamMb / 1024f)} GB",
                    value = config.maxRamMb.toFloat(),
                    range = 512f..6144f,
                    onValueChange = { onChange(config.copy(maxRamMb = it.toInt())) }
                )
            }
        }

        item {
            SectionCard(title = "") {
                Text(
                    "High view distance increases memory usage and network latency. It is recommended to keep the default values.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                LabeledSlider(
                    label = "View Distance",
                    valueLabel = "${config.viewDistanceChunks} chunks",
                    value = config.viewDistanceChunks.toFloat(),
                    range = 3f..32f,
                    valueColor = if (config.viewDistanceChunks > 12) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    onValueChange = { onChange(config.copy(viewDistanceChunks = it.toInt())) }
                )
                if (config.viewDistanceChunks > 12) {
                    WarningNote("Above 12 chunks is strongly discouraged. Only the fastest phones can handle it — expect heavy lag or crashes on most devices.")
                }

                Spacer(Modifier.height(16.dp))
                LabeledSlider(
                    label = "Simulation Distance",
                    valueLabel = "${config.simulationDistanceChunks} chunks",
                    value = config.simulationDistanceChunks.toFloat(),
                    range = 3f..32f,
                    onValueChange = { onChange(config.copy(simulationDistanceChunks = it.toInt())) }
                )

                Spacer(Modifier.height(16.dp))
                LabeledSlider(
                    label = "Max Players",
                    valueLabel = "${config.maxPlayers} players",
                    value = config.maxPlayers.toFloat(),
                    range = 1f..50f,
                    onValueChange = { onChange(config.copy(maxPlayers = it.toInt())) }
                )
            }
        }
    }
}

@Composable
private fun LabeledSlider(
    label: String,
    valueLabel: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    valueColor: Color = MaterialTheme.colorScheme.primary,
    onValueChange: (Float) -> Unit
) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.titleMedium)
            Text(valueLabel, style = MaterialTheme.typography.titleMedium, color = valueColor)
        }
        Slider(value = value, onValueChange = onValueChange, valueRange = range)
    }
}

@Composable
private fun WarningNote(text: String) {
    Row(modifier = Modifier.padding(top = 8.dp)) {
        Icon(Icons.Filled.Info, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 2.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
}
