package com.cryptmc.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberTrayState
import androidx.compose.ui.window.Window
import com.cryptmc.desktop.ai.AiAssistantManager
import com.cryptmc.desktop.ai.AiServerContext
import com.cryptmc.desktop.ai.AiSettingsStore
import com.cryptmc.desktop.ai.ChatMessage
import com.cryptmc.desktop.ai.ChatRole
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Entry point. `./gradlew :desktop:run` for dev, `:desktop:packageDistributionForCurrentOS`
 * for a shippable .dmg/.msi/.deb (see build.gradle.kts). Intentionally minimal next to
 * the Android app's full nav graph (Home/Setup/Console/Files/Profile/Admin) — this covers
 * server list + start/stop + live console, which is the part that's actually different
 * on desktop (no foreground-service/battery-optimization dance needed here). Bring over
 * more of :app's screens as this grows; they're good reference implementations even though
 * the code isn't shared yet.
 */
private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
private val manager = DesktopServerManager(appScope)

fun main() = application {
    val trayState = rememberTrayState()
    Tray(state = trayState, tooltip = "CryptMc")

    Window(onCloseRequest = ::exitApplication, title = "CryptMc Desktop") {
        MaterialTheme {
            CryptMcDesktopApp()
        }
    }
}

@Composable
fun CryptMcDesktopApp() {
    var servers by remember { mutableStateOf(listOf<DesktopServerConfig>()) }
    var selectedServerId by remember { mutableStateOf<String?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showAiDialog by remember { mutableStateOf(false) }
    val statuses by manager.statuses.collectAsState()
    val consoleLines = remember { mutableStateListOf<String>() }

    LaunchedEffect(selectedServerId) {
        consoleLines.clear()
        manager.consoleLines.collect { (serverId, line) ->
            if (serverId == selectedServerId) {
                consoleLines.add(line)
                if (consoleLines.size > 500) consoleLines.removeAt(0)
            }
        }
    }

    Row(Modifier.fillMaxSize()) {
        // Server list rail
        Column(
            Modifier.width(280.dp).fillMaxHeight().padding(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("Servers", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                IconButton(onClick = { showAddDialog = true }) {
                    Text("+", style = MaterialTheme.typography.titleLarge)
                }
            }
            Spacer(Modifier.height(8.dp))
            LazyColumn {
                items(servers, key = { it.id }) { server ->
                    val running = statuses[server.id]?.running == true
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        onClick = { selectedServerId = server.id }
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(server.name, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    if (running) "Online · port ${server.serverPort}" else "Offline",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (running) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        Divider(modifier = Modifier.fillMaxHeight().width(1.dp))

        // Detail / console pane
        val selected = servers.firstOrNull { it.id == selectedServerId }
        Column(Modifier.weight(1f).fillMaxHeight().padding(16.dp)) {
            if (selected == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Select or add a server to get started", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(12.dp))
                        OutlinedButton(onClick = { showAiDialog = true }) { Text("Ask AI") }
                    }
                }
            } else {
                val running = statuses[selected.id]?.running == true
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(selected.name, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                    OutlinedButton(onClick = { showAiDialog = true }, modifier = Modifier.padding(end = 8.dp)) {
                        Text("Ask AI")
                    }
                    Button(
                        onClick = {
                            if (running) manager.stop(selected.id) else manager.start(selected)
                        }
                    ) {
                        Text(if (running) "Stop" else "Start")
                    }
                }
                Spacer(Modifier.height(12.dp))
                Card(Modifier.weight(1f).fillMaxWidth()) {
                    LazyColumn(Modifier.padding(8.dp)) {
                        items(consoleLines) { line ->
                            Text(line, style = MaterialTheme.typography.bodySmall, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                var commandInput by remember { mutableStateOf("") }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = commandInput,
                        onValueChange = { commandInput = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        enabled = running,
                        placeholder = { Text("Type a server command…") }
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(
                        enabled = running && commandInput.isNotBlank(),
                        onClick = { manager.sendCommand(selected.id, commandInput); commandInput = "" }
                    ) { Text("Send") }
                }
            }
        }
    }

    if (showAddDialog) {
        AddServerDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { name, workingDir, jarFileName, port ->
                val config = DesktopServerConfig(
                    id = UUID.randomUUID().toString(),
                    name = name,
                    workingDir = workingDir,
                    jarFileName = jarFileName,
                    serverPort = port,
                    eulaAccepted = true // dialog's confirm button IS the explicit consent step
                )
                servers = servers + config
                selectedServerId = config.id
                showAddDialog = false
            }
        )
    }

    if (showAiDialog) {
        val selected = servers.firstOrNull { it.id == selectedServerId }
        AiChatDialog(
            selectedServer = selected,
            serverStatus = selected?.let { statuses[it.id] },
            consoleTail = consoleLines,
            onDismiss = { showAiDialog = false }
        )
    }
}

/**
 * Desktop's "Ask AI" surface — a lighter-weight cousin of the phone app's
 * ui/ai/AiAssistantScreen.kt (same Anthropic call underneath via
 * ai/AiAssistantManager.kt), shown as a modal Dialog rather than its own
 * window/screen since this app doesn't have a nav graph to route into.
 */
@Composable
private fun AiChatDialog(
    selectedServer: DesktopServerConfig?,
    serverStatus: DesktopServerRuntimeStatus?,
    consoleTail: List<String>,
    onDismiss: () -> Unit
) {
    val aiManager = remember { AiAssistantManager() }
    var apiKey by remember { mutableStateOf(AiSettingsStore.getApiKey().orEmpty()) }
    var keyDraft by remember { mutableStateOf(apiKey) }
    var editingKey by remember { mutableStateOf(apiKey.isBlank()) }

    val messages = remember { mutableStateListOf<ChatMessage>() }
    var input by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    fun send(text: String) {
        if (text.isBlank() || sending) return
        error = null
        messages.add(ChatMessage(ChatRole.USER, text))
        input = ""
        sending = true
        scope.launch {
            val context = selectedServer?.let {
                AiServerContext(config = it, status = serverStatus, recentConsoleLines = consoleTail)
            }
            val result = aiManager.ask(
                apiKey = apiKey,
                history = messages.dropLast(1),
                userMessage = text,
                context = context
            )
            result.onSuccess { reply -> messages.add(ChatMessage(ChatRole.ASSISTANT, reply)) }
                .onFailure { e -> error = e.message ?: "Something went wrong talking to the AI assistant." }
            sending = false
            if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(modifier = Modifier.width(520.dp).height(620.dp), shape = MaterialTheme.shapes.large, tonalElevation = 4.dp) {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("AI Assistant", style = MaterialTheme.typography.titleLarge)
                        if (selectedServer != null) {
                            Text(selectedServer.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    TextButton(onClick = { editingKey = true }) { Text("API Key") }
                    TextButton(onClick = onDismiss) { Text("Close") }
                }
                Spacer(Modifier.height(8.dp))

                if (editingKey) {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text(
                                "Stored via this OS's local preferences store (not encrypted — see " +
                                    "AiSettingsStore.kt). Get a key at console.anthropic.com.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = keyDraft,
                                onValueChange = { keyDraft = it },
                                singleLine = true,
                                label = { Text("sk-ant-…") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                                if (apiKey.isNotBlank()) {
                                    TextButton(onClick = { keyDraft = apiKey; editingKey = false }) { Text("Cancel") }
                                    Spacer(Modifier.width(8.dp))
                                }
                                Button(
                                    enabled = keyDraft.isNotBlank(),
                                    onClick = {
                                        apiKey = keyDraft.trim()
                                        AiSettingsStore.setApiKey(apiKey)
                                        editingKey = false
                                    }
                                ) { Text("Save") }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(messages) { message ->
                        val isUser = message.role == ChatRole.USER
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start) {
                            Surface(
                                color = if (isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                shape = MaterialTheme.shapes.medium,
                                modifier = Modifier.fillMaxWidth(0.85f)
                            ) {
                                Text(message.text, modifier = Modifier.padding(10.dp), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                    if (sending) {
                        item {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                                Text("Thinking…", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }

                if (error != null) {
                    Text(error ?: "", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }

                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        enabled = apiKey.isNotBlank() && !sending,
                        placeholder = { Text(if (apiKey.isBlank()) "Add your API key above first…" else "Ask about setup, RAM, ports…") }
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { send(input) }, enabled = apiKey.isNotBlank() && !sending && input.isNotBlank()) {
                        Text("Send")
                    }
                }
            }
        }
    }
}

@Composable
private fun AddServerDialog(
    onDismiss: () -> Unit,
    onAdd: (name: String, workingDir: String, jarFileName: String, port: Int) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var workingDir by remember { mutableStateOf(System.getProperty("user.home") + "/CryptMc/servers/new-server") }
    var jarFileName by remember { mutableStateOf("server.jar") }
    var portText by remember { mutableStateOf("25565") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Server") },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = workingDir, onValueChange = { workingDir = it }, label = { Text("Folder") }, singleLine = true)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = jarFileName, onValueChange = { jarFileName = it },
                    label = { Text("Jar filename (already in that folder)") }, singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = portText, onValueChange = { portText = it.filter(Char::isDigit) },
                    label = { Text("Port") }, singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "By continuing you accept Mojang's EULA for this server.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && workingDir.isNotBlank() && jarFileName.isNotBlank(),
                onClick = { onAdd(name, workingDir, jarFileName, portText.toIntOrNull() ?: 25565) }
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
