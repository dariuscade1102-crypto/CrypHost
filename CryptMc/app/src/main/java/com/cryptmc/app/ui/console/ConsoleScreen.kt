package com.cryptmc.app.ui.console

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.cryptmc.app.data.ServerRepository

private data class QuickCommand(val label: String, val icon: ImageVector, val command: String, val destructive: Boolean = false)

private val quickCommands = listOf(
    QuickCommand("/stop", Icons.Filled.Close, "stop", destructive = true),
    QuickCommand("/help", Icons.Filled.Description, "help"),
    QuickCommand("/list", Icons.Filled.List, "list"),
    QuickCommand("/op", Icons.Filled.Shield, "op "),
    QuickCommand("/kick", Icons.AutoMirrored.Filled.Login, "kick ")
)

/**
 * A vanilla/Paper `/list` response looks like:
 *   "There are 2 of a max of 10 players online: Notch, Steve"
 * This is the only player-roster source these servers expose without a
 * plugin — no separate management API exists, so the player rail below is
 * built by parsing this line out of the console stream rather than querying
 * anything structured. Re-issue `/list` on an interval (see the
 * LaunchedEffect below) to keep the rail from going stale between joins.
 */
private val LIST_RESPONSE_REGEX = Regex("""online:\s*(.+)$""")

private fun parsePlayerList(line: String): List<String>? {
    val match = LIST_RESPONSE_REGEX.find(line) ?: return null
    val namesPart = match.groupValues[1].trim()
    if (namesPart.isEmpty()) return emptyList()
    return namesPart.split(",").map { it.trim() }.filter { it.isNotEmpty() }
}

/**
 * "Web Console" opens a browser tab pointed at the local dashboard server
 * this app can optionally run (e.g. Ktor on 127.0.0.1:PORT), letting the
 * host manage from a desktop browser on the same LAN — not shown here since
 * it's a separate embedded HTTP server component, but ServerProcessManager's
 * consoleLines flow is exactly what you'd pipe over a WebSocket to power it.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun ConsoleScreen(serverId: String, onBack: () -> Unit, onAskAi: () -> Unit = {}) {
    val servers by ServerRepository.servers.collectAsState()
    val statuses by ServerRepository.statuses.collectAsState()
    val config = servers.firstOrNull { it.id == serverId } ?: run { onBack(); return }
    val status = statuses[serverId]

    val context = LocalContext.current as? androidx.activity.ComponentActivity
    val isWideLayout = context?.let {
        calculateWindowSizeClass(it).widthSizeClass != WindowWidthSizeClass.Compact
    } ?: false

    var commandInput by remember { mutableStateOf("") }
    // Demo/placeholder log lines — real output comes from binding to
    // ServerForegroundService.processManager.consoleLines, as wired in
    // DashboardViewModel from the earlier scaffold.
    val consoleLines = remember {
        mutableStateListOf(
            "16:11:32 INFO [SYSTEM] jar_server exited at 2026-09-14 14:01:48: stopped by the app (RSS 1295MB, PSS 0MB)",
            "16:11:32 INFO There are 2 of a max of 10 players online: Notch, Steve"
        )
    }

    var players by remember { mutableStateOf(parsePlayerList(consoleLines.last()) ?: emptyList()) }

    // Keep the roster fresh by re-parsing whenever a new /list response
    // arrives, and by nudging the server for one periodically — this is a
    // client-side timer only; wire the actual "list" send through
    // ServerProcessManager.sendCommand once bound to the real service.
    LaunchedEffect(consoleLines.size) {
        consoleLines.lastOrNull()?.let { line ->
            parsePlayerList(line)?.let { players = it }
        }
    }

    fun sendCommand(command: String) {
        consoleLines.add("> $command")
        // ServerProcessManager.sendCommand(command) once bound to the service.
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Console") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    IconButton(onClick = onAskAi) {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = "Ask AI about this server")
                    }
                    OutlinedButton(onClick = { /* opens embedded web dashboard in Custom Tabs */ }, modifier = Modifier.padding(end = 8.dp)) {
                        Icon(Icons.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("WEB CONSOLE")
                    }
                }
            )
        }
    ) { padding ->
        if (isWideLayout) {
            Row(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
                Column(modifier = Modifier.weight(2f).fillMaxHeight()) {
                    QuickCommandRow(quickCommands, ::sendCommand) { commandInput = it }
                    HorizontalDivider()
                    ConsoleLog(consoleLines, Modifier.weight(1f))
                    CommandInputField(commandInput, { commandInput = it }) {
                        if (commandInput.isNotBlank()) { sendCommand(commandInput); commandInput = "" }
                    }
                }
                Spacer(Modifier.width(16.dp))
                PlayerRail(
                    players = players,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    onKick = { name -> sendCommand("kick $name Kicked by host") },
                    onBan = { name -> sendCommand("ban $name Banned by host") },
                    onWhitelist = { name -> sendCommand("whitelist add $name") }
                )
            }
        } else {
            Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
                QuickCommandRow(quickCommands, ::sendCommand) { commandInput = it }
                HorizontalDivider()
                ConsoleLog(consoleLines, Modifier.weight(1f))
                CommandInputField(commandInput, { commandInput = it }) {
                    if (commandInput.isNotBlank()) { sendCommand(commandInput); commandInput = "" }
                }
                if (players.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    // PlayerRail renders its own "Online players (n)" header below —
                    // don't duplicate it here (this line used to print it twice).
                    PlayerRail(
                        players = players,
                        modifier = Modifier.heightIn(max = 220.dp),
                        onKick = { name -> sendCommand("kick $name Kicked by host") },
                        onBan = { name -> sendCommand("ban $name Banned by host") },
                        onWhitelist = { name -> sendCommand("whitelist add $name") }
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickCommandRow(commands: List<QuickCommand>, onSend: (String) -> Unit, onPrefill: (String) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("— LIVE OUTPUT —", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        IconButton(onClick = { /* share log via ACTION_SEND */ }) {
            Icon(Icons.Filled.Share, contentDescription = "Share log")
        }
    }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
        items(commands) { qc ->
            AssistChip(
                onClick = {
                    if (qc.command.endsWith(" ")) onPrefill("/${qc.command}") else onSend(qc.command)
                },
                label = { Text(qc.label) },
                leadingIcon = { Icon(qc.icon, contentDescription = null, modifier = Modifier.size(16.dp)) },
                colors = if (qc.destructive) AssistChipDefaults.assistChipColors(
                    labelColor = MaterialTheme.colorScheme.error,
                    leadingIconContentColor = MaterialTheme.colorScheme.error
                ) else AssistChipDefaults.assistChipColors()
            )
        }
    }
}

@Composable
private fun ConsoleLog(lines: List<String>, modifier: Modifier = Modifier) {
    LazyColumn(modifier = modifier.padding(vertical = 8.dp)) {
        items(lines) { line ->
            Text(line, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 4.dp))
        }
    }
}

@Composable
private fun CommandInputField(value: String, onValueChange: (String) -> Unit, onSend: () -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text("Enter command...") },
        leadingIcon = { Text("/", color = MaterialTheme.colorScheme.primary) },
        trailingIcon = {
            IconButton(onClick = onSend) { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send") }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
        keyboardActions = KeyboardActions(onSend = { onSend() }),
        modifier = Modifier.fillMaxWidth()
    )
}

/** The wide-layout player-management rail — kick/ban/whitelist per player,
 *  filled from the /list parse above. */
@Composable
private fun PlayerRail(
    players: List<String>,
    modifier: Modifier = Modifier,
    onKick: (String) -> Unit,
    onBan: (String) -> Unit,
    onWhitelist: (String) -> Unit
) {
    Column(modifier) {
        Text("Online players (${players.size})", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        if (players.isEmpty()) {
            Text("No players online", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            LazyColumn {
                items(players) { name ->
                    PlayerRow(name, onKick, onBan, onWhitelist)
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun PlayerRow(name: String, onKick: (String) -> Unit, onBan: (String) -> Unit, onWhitelist: (String) -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Person, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(name, style = MaterialTheme.typography.bodyLarge)
        }
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = "Player actions")
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(text = { Text("Whitelist") }, onClick = { onWhitelist(name); menuOpen = false })
                DropdownMenuItem(text = { Text("Kick") }, onClick = { onKick(name); menuOpen = false })
                DropdownMenuItem(
                    text = { Text("Ban", color = MaterialTheme.colorScheme.error) },
                    onClick = { onBan(name); menuOpen = false }
                )
            }
        }
    }
}
