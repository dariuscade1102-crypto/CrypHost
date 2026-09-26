package com.cryptmc.app.ui.console

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.cryptmc.app.data.ServerRepository
import com.cryptmc.app.service.ServerForegroundService

@Composable
fun ConsoleScreen(serverId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val servers by ServerRepository.servers.collectAsState()
    val statuses by ServerRepository.statuses.collectAsState()
    val config = servers.firstOrNull { it.id == serverId }
    val running = statuses[serverId]?.running == true
    var command by remember { mutableStateOf("") }
    val lines = remember(serverId) { mutableStateListOf<String>() }

    LaunchedEffect(config) {
        if (config == null) onBack()
    }
    LaunchedEffect(serverId) {
        ServerForegroundService.consoleLinesFromUi().collect { line ->
            lines.add(line)
            while (lines.size > 300) lines.removeAt(0)
        }
    }

    fun send(raw: String) {
        val normalized = raw.trim().removePrefix("/")
        if (normalized.isBlank()) return
        if (ServerForegroundService.sendCommandFromUi(normalized)) {
            lines.add("> $normalized")
        } else {
            lines.add("[CryptHost] Server is not running")
        }
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(config?.name ?: "Console") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }
        )
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(if (running) "RUNNING" else "STOPPED", style = MaterialTheme.typography.titleMedium, color = if (running) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    if (!running) {
                        Text("The server is stopped. Start it from here or go back.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = {
                                config?.let {
                                    context.startForegroundService(Intent(context, ServerForegroundService::class.java).setAction(ServerForegroundService.ACTION_START).putExtra(ServerForegroundService.EXTRA_CONFIG, it))
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Start server") }
                    } else {
                        OutlinedButton(
                            onClick = { context.startService(Intent(context, ServerForegroundService::class.java).setAction(ServerForegroundService.ACTION_STOP)) },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Stop server") }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("Console output", style = MaterialTheme.typography.titleMedium)
            Card(Modifier.fillMaxWidth().weight(1f)) {
                if (lines.isEmpty()) {
                    Text("Waiting for server output…", modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    LazyColumn(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(lines) { line -> Text(line, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = command,
                    onValueChange = { command = it },
                    placeholder = { Text("Command, e.g. list") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { send(command); command = "" }),
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { send(command); command = "" }, enabled = command.isNotBlank()) {
                    Icon(Icons.AutoMirrored.Filled.Send, "Send command")
                }
            }
        }
    }
}
