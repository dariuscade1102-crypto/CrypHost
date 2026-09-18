package com.cryptmc.app.ui.server

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.cryptmc.app.auth.SecureCredentialStore
import com.cryptmc.app.data.ServerConfig
import com.cryptmc.app.data.TunnelMode
import com.cryptmc.app.network.HotspotManager
import com.cryptmc.app.network.HotspotState
import com.cryptmc.app.network.ModrinthRepository
import kotlinx.coroutines.launch

/**
 * Requirement #3's toggle: flipping "Bedrock Support" on calls
 * ModrinthRepository.installBedrockCrossplay, dropping Geyser+Floodgate into
 * plugins/ so the host never has to know those projects exist by name.
 */
@Composable
fun NetworkTab(config: ServerConfig, onChange: (ServerConfig) -> Unit) {
    val repository = remember { ModrinthRepository() }
    val scope = rememberCoroutineScope()
    var installingCrossplay by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val credentialStore = remember { SecureCredentialStore(context) }
    var rconPassword by remember(config.id) { mutableStateOf(credentialStore.getRconPassword(config.id) ?: "") }
    var rconPasswordVisible by remember { mutableStateOf(false) }
    var rconEnabled by remember { mutableStateOf(rconPassword.isNotEmpty()) }

    val hotspotManager = remember { HotspotManager(context) }
    val hotspotState by hotspotManager.state.collectAsState()
    val clipboard = LocalClipboardManager.current
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) hotspotManager.start()
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            SectionCard(title = "Network") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Server Port", style = MaterialTheme.typography.titleMedium)
                    Text(config.serverPort.toString(), style = MaterialTheme.typography.titleMedium)
                }

                Divider(modifier = Modifier.padding(vertical = 12.dp))

                SettingToggleRow(
                    title = "Block Cracked Players",
                    description = "Verify users with Mojang (online mode)",
                    checked = config.onlineMode,
                    onCheckedChange = { onChange(config.copy(onlineMode = it)) }
                )

                if (!config.onlineMode) {
                    WarningBanner(
                        "Account verification is off. Anyone can log in as any username, so others could impersonate you or your players. Use a whitelist to stay protected."
                    )
                }

                Divider(modifier = Modifier.padding(vertical = 12.dp))

                SettingToggleRow(
                    title = "Whitelist",
                    description = "Only allow specific players to join",
                    checked = config.whitelistEnabled,
                    onCheckedChange = { onChange(config.copy(whitelistEnabled = it)) }
                )
            }
        }

        item {
            SectionCard(title = "") {
                SettingToggleRow(
                    title = "Bedrock Support (Geyser)",
                    description = "Let Bedrock Edition players join your server",
                    checked = config.bedrockCrossplayEnabled,
                    onCheckedChange = { enabled ->
                        onChange(config.copy(bedrockCrossplayEnabled = enabled))
                        if (enabled) {
                            installingCrossplay = true
                            scope.launch {
                                runCatching {
                                    repository.installBedrockCrossplay(
                                        mcVersion = config.minecraftVersion,
                                        pluginsDir = java.io.File("${config.workingDir}/plugins")
                                    )
                                }
                                installingCrossplay = false
                            }
                        }
                    }
                )
                if (installingCrossplay) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                }
                InfoNote("Bedrock players connect on a different IP than Java")
                InfoNote("ViaVersion will be added so older Java clients can connect")
                InfoNote("Geyser requires Java 17 or higher")
            }
        }

        item {
            SectionCard(title = "Local Hotspot Hosting") {
                Text(
                    "Turn this device into its own Wi-Fi network — no router, guest network, or " +
                        "internet connection needed. Players connect their phones to this hotspot, " +
                        "then Direct Connect to the address below. Phone/tablet only.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))

                when (val s = hotspotState) {
                    is HotspotState.Idle -> {
                        Button(
                            onClick = {
                                onChange(config.copy(tunnelMode = TunnelMode.HOTSPOT))
                                if (hotspotManager.hasRequiredPermissions()) {
                                    hotspotManager.start()
                                } else {
                                    permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.WifiTethering, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Start Hotspot")
                        }
                    }
                    is HotspotState.Starting -> {
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(12.dp))
                            Text("Starting hotspot…")
                        }
                    }
                    is HotspotState.Active -> {
                        HotspotCredentialRow(label = "Network name", value = s.ssid, clipboard = clipboard)
                        HotspotCredentialRow(label = "Password", value = s.password, clipboard = clipboard)
                        HotspotCredentialRow(
                            label = "Direct Connect address",
                            value = "${s.localIp ?: "check Settings > Wi-Fi Hotspot"}:${config.serverPort}",
                            clipboard = clipboard
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = { hotspotManager.stop() }, modifier = Modifier.fillMaxWidth()) {
                            Text("Stop Hotspot")
                        }
                        InfoNote("Have players join this Wi-Fi network first, then use the Direct Connect address above in Minecraft's Multiplayer screen")
                        InfoNote("Android stops the hotspot automatically if this app is closed or backgrounded")
                    }
                    is HotspotState.Failed -> {
                        WarningBanner(s.reason)
                        Button(onClick = { hotspotManager.start() }, modifier = Modifier.fillMaxWidth()) {
                            Text("Retry")
                        }
                    }
                }
            }
        }

        item {
            SectionCard(title = "Remote Console (RCON)") {
                SettingToggleRow(
                    title = "Enable RCON",
                    description = "Lets external tools (a desktop panel, Discord bot) send console commands remotely",
                    checked = rconEnabled,
                    onCheckedChange = { enabled ->
                        rconEnabled = enabled
                        if (!enabled) {
                            credentialStore.clearRconPassword(config.id)
                            rconPassword = ""
                        }
                    }
                )
                if (rconEnabled) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = rconPassword,
                        onValueChange = {
                            rconPassword = it
                            // Written straight to the Keystore-encrypted
                            // store — never into ServerConfig/ServerRepository,
                            // which isn't encrypted at rest (see README).
                            credentialStore.setRconPassword(config.id, it)
                        },
                        label = { Text("RCON password") },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
                        trailingIcon = {
                            IconButton(onClick = { rconPasswordVisible = !rconPasswordVisible }) {
                                Icon(
                                    if (rconPasswordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                    contentDescription = if (rconPasswordVisible) "Hide password" else "Show password"
                                )
                            }
                        },
                        visualTransformation = if (rconPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    InfoNote("Stored encrypted on this device only — never synced or sent anywhere but into server.properties on server start")
                }
            }
        }
    }
}

@Composable
private fun WarningBanner(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
    ) {
        Row(Modifier.padding(12.dp)) {
            Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
            Spacer(Modifier.width(8.dp))
            Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
        }
    }
}

@Composable
private fun HotspotCredentialRow(label: String, value: String, clipboard: ClipboardManager) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        Column {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleSmall)
        }
        IconButton(onClick = { clipboard.setText(AnnotatedString(value)) }) {
            Icon(Icons.Filled.ContentCopy, contentDescription = "Copy $label")
        }
    }
}

@Composable
private fun InfoNote(text: String) {
    Row(Modifier.padding(top = 6.dp)) {
        Icon(Icons.Filled.Info, contentDescription = null, modifier = Modifier.padding(top = 2.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
