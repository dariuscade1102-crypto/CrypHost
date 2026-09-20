package com.cryptmc.app.ui.setup

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cryptmc.app.data.ServerLoader
import com.cryptmc.app.data.ServerRepository

/**
 * Mirrors the reference app's 8-step wizard. Steps 1, 4-8 (server name,
 * Minecraft version, world options, RAM, network, review) follow the same
 * one-question-per-screen pattern as Step 2 shown here — extend STEPS below
 * to flesh them out; the scaffold wires the full flow through to
 * ServerRepository.createDraft so "New Server" from any entry point ends up
 * in the same place.
 */
private enum class WizardStep(val title: String, val subtitle: String) {
    NAME("Name Your Server", "What should we call it?"),
    SOFTWARE("Choose Software", "Pick the server software that fits your needs."),
    VERSION("Minecraft Version", "Choose which version to run."),
    RAM("Allocate RAM", "How much memory can this device spare?"),
    NETWORK("Network Setup", "Zero port-forwarding — pick how players connect."),
    REVIEW("Review & Create", "Confirm your choices.")
}

@Composable
fun GuidedSetupScreen(onFinished: (serverId: String) -> Unit) {
    var stepIndex by remember { mutableIntStateOf(0) }
    var serverName by remember { mutableStateOf("") }
    var selectedLoader by remember { mutableStateOf(ServerLoader.PAPER) }
    var selectedVersion by remember { mutableStateOf("1.21.1") }
    var ramGb by remember { mutableFloatStateOf(4f) }

    val step = WizardStep.entries[stepIndex]

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Guided Setup") },
                navigationIcon = {
                    if (stepIndex > 0) {
                        IconButton(onClick = { stepIndex-- }) {
                            Icon(ArrowBack, contentDescription = "Back")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp).fillMaxSize()) {
            Text(
                "STEP ${stepIndex + 1} OF ${WizardStep.entries.size}",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelMedium
            )
            LinearProgressIndicator(
                progress = { (stepIndex + 1f) / WizardStep.entries.size },
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            )
            Text(step.title, style = MaterialTheme.typography.headlineSmall)
            Text(step.subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))

            Box(modifier = Modifier.weight(1f)) {
                when (step) {
                    WizardStep.NAME -> NameStep(serverName) { serverName = it }
                    WizardStep.SOFTWARE -> SoftwareStep(selectedLoader) { selectedLoader = it }
                    WizardStep.VERSION -> VersionStep(selectedVersion) { selectedVersion = it }
                    WizardStep.RAM -> RamStep(ramGb) { ramGb = it }
                    WizardStep.NETWORK -> NetworkStep()
                    WizardStep.REVIEW -> ReviewStep(serverName, selectedLoader, selectedVersion, ramGb)
                }
            }

            Button(
                onClick = {
                    if (stepIndex < WizardStep.entries.lastIndex) {
                        stepIndex++
                    } else {
                        val config = ServerRepository.createDraft(
                            name = serverName.ifBlank { "New Server" },
                            loader = selectedLoader,
                            mcVersion = selectedVersion
                        )
                        onFinished(config.id)
                    }
                },
                enabled = step != WizardStep.NAME || serverName.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (step == WizardStep.REVIEW) "Create Server" else "Next")
                Spacer(Modifier.width(6.dp))
                Icon(Icons.Filled.ArrowForward, contentDescription = null)
            }
        }
    }
}

@Composable
private fun NameStep(value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text("Server name") },
        placeholder = { Text("e.g. Buret SMP") },
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun SoftwareStep(selected: ServerLoader, onSelect: (ServerLoader) -> Unit) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(0.dp)) {
        items(ServerLoader.entries) { loader ->
            SoftwareOptionRow(loader, loader == selected) { onSelect(loader) }
            Divider()
        }
    }
}

@Composable
private fun SoftwareOptionRow(loader: ServerLoader, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(loader.displayName, style = MaterialTheme.typography.titleMedium)
            Text(loaderDescription(loader), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        AssistChip(onClick = {}, label = { Text(loader.badge) }, enabled = false)
    }
}

private fun loaderDescription(loader: ServerLoader) = when (loader) {
    ServerLoader.PAPER -> "High performance, best for survival and plugins"
    ServerLoader.VANILLA -> "Pure Minecraft, exactly as Mojang ships it — no mods or plugins"
    ServerLoader.FABRIC -> "Lightweight modding platform, great for performance mods"
    ServerLoader.NEOFORGE -> "Modern Forge fork, best for large mod packs and Forge mods"
    ServerLoader.PURPUR -> "Paper with extra gameplay settings, same plugins"
    ServerLoader.POWERNUKKITX -> "Bedrock Edition only — for phones, tablets and consoles"
}

@Composable
private fun VersionStep(selected: String, onSelect: (String) -> Unit) {
    val versions = listOf("1.21.1", "1.20.4", "1.20.1", "1.19.4", "1.18.2")
    LazyColumn {
        items(versions) { version ->
            Row(
                modifier = Modifier.fillMaxWidth().selectable(selected == version) { onSelect(version) }.padding(vertical = 12.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                RadioButton(selected = selected == version, onClick = { onSelect(version) })
                Spacer(Modifier.width(8.dp))
                Text(version, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun RamStep(gb: Float, onChange: (Float) -> Unit) {
    Column {
        Text("Allocated RAM: ${gb} GB", style = MaterialTheme.typography.titleMedium)
        Slider(value = gb, onValueChange = onChange, valueRange = 0.5f..6f)
        Text(
            "Allocating too much RAM may cause your device to freeze or become unresponsive.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )
    }
}

@Composable
private fun NetworkStep() {
    Column {
        Text(
            "By default your server tunnels through playit.gg so friends can join without you forwarding any router ports. You can change this later from Home.",
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun ReviewStep(name: String, loader: ServerLoader, version: String, ramGb: Float) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ReviewRow("Name", name.ifBlank { "New Server" })
        ReviewRow("Software", loader.displayName)
        ReviewRow("Version", version)
        ReviewRow("RAM", "$ramGb GB")
    }
}

@Composable
private fun ReviewRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleSmall)
    }
}
