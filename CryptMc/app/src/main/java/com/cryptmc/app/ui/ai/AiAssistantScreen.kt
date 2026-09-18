package com.cryptmc.app.ui.ai

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.cryptmc.app.ai.AiAssistantRepository
import com.cryptmc.app.ai.AiEngineMode
import com.cryptmc.app.ai.AiPlatform
import com.cryptmc.app.ai.AiServerContext
import com.cryptmc.app.ai.ChatMessage
import com.cryptmc.app.ai.ChatRole
import com.cryptmc.app.ai.OnDeviceAiEngine
import com.cryptmc.app.auth.SecureCredentialStore
import com.cryptmc.app.data.ServerRepository
import com.cryptmc.app.network.DownloadProgress
import com.cryptmc.app.network.ModelDownloader
import kotlinx.coroutines.launch

private val quickPrompts = listOf(
    "Why can't my friends join?",
    "Which RAM settings should I use?",
    "How do I let Bedrock players in?",
    "Set up a server from scratch"
)

/**
 * The "AI" surface: a chat over the current server's live config (RAM,
 * loader, port, tunnel mode, recent console lines) so answers are concrete
 * rather than generic Minecraft-hosting trivia — see
 * ai/AiAssistantRepository.buildSystemPrompt for exactly what's sent.
 *
 * Reached two ways: Home's top bar (serverId = null, general questions/
 * "help me set up my first server") and a server's Console/Settings screens
 * ("Ask AI" action, serverId set — pre-loads that server's context).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiAssistantScreen(serverId: String?, onBack: () -> Unit) {
    val context = LocalContext.current
    val credentialStore = remember { SecureCredentialStore(context) }
    val repository = remember { AiAssistantRepository() }
    val onDeviceEngine = remember { OnDeviceAiEngine(context) }
    val modelDownloader = remember { ModelDownloader() }
    val servers by ServerRepository.servers.collectAsState()
    val statuses by ServerRepository.statuses.collectAsState()
    val config = serverId?.let { id -> servers.firstOrNull { it.id == id } }

    var engineMode by remember {
        mutableStateOf(
            runCatching { AiEngineMode.valueOf(credentialStore.getAiEngineMode() ?: "") }
                .getOrDefault(AiEngineMode.CLOUD_API)
        )
    }
    var apiKey by remember { mutableStateOf(credentialStore.getAnthropicApiKey().orEmpty()) }
    var keyDraft by remember { mutableStateOf(apiKey) }
    var editingKey by remember { mutableStateOf(apiKey.isBlank()) }
    var modelDownloaded by remember { mutableStateOf(onDeviceEngine.isModelDownloaded()) }
    var modelUrlDraft by remember { mutableStateOf("") }
    var downloadProgress by remember { mutableStateOf<DownloadProgress?>(null) }
    val ready = when (engineMode) {
        AiEngineMode.CLOUD_API -> apiKey.isNotBlank()
        AiEngineMode.ON_DEVICE -> modelDownloaded
    }

    fun setMode(mode: AiEngineMode) {
        engineMode = mode
        credentialStore.setAiEngineMode(mode.name)
    }

    DisposableEffect(Unit) { onDispose { onDeviceEngine.close() } }

    val messages = remember { mutableStateListOf<ChatMessage>() }
    var input by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    fun send(text: String) {
        if (text.isBlank() || sending || !ready) return
        error = null
        val userTurn = ChatMessage(ChatRole.USER, text)
        messages.add(userTurn)
        input = ""
        sending = true
        scope.launch {
            val aiContext = config?.let {
                AiServerContext(
                    config = it,
                    status = statuses[it.id],
                    // Real console tail comes from ServerProcessManager/ServerForegroundService's
                    // consoleLines flow once this screen is opened from a live Console session —
                    // left empty here since this scaffold's ConsoleScreen uses placeholder lines.
                    recentConsoleLines = emptyList()
                )
            }
            val result = when (engineMode) {
                AiEngineMode.CLOUD_API -> repository.ask(
                    apiKey = apiKey,
                    platform = AiPlatform.ANDROID,
                    history = messages.dropLast(1),
                    userMessage = text,
                    context = aiContext
                )
                AiEngineMode.ON_DEVICE -> onDeviceEngine.ask(
                    platform = AiPlatform.ANDROID,
                    history = messages.dropLast(1),
                    userMessage = text,
                    serverContext = aiContext
                )
            }
            result.onSuccess { reply ->
                messages.add(ChatMessage(ChatRole.ASSISTANT, reply))
            }.onFailure { e ->
                error = e.message ?: "Something went wrong talking to the AI assistant."
            }
            sending = false
            listState.animateScrollToItem((messages.size - 1).coerceAtLeast(0))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("AI Assistant")
                        if (config != null) {
                            Text(
                                config.name,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    IconButton(onClick = { editingKey = true }) {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = "AI settings")
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (editingKey) {
                AiSettingsCard(
                    mode = engineMode,
                    onModeChange = ::setMode,
                    keyDraft = keyDraft,
                    onKeyDraftChange = { keyDraft = it },
                    onSaveKey = {
                        apiKey = keyDraft.trim()
                        credentialStore.setAnthropicApiKey(apiKey)
                        editingKey = false
                    },
                    onCancel = { keyDraft = apiKey; editingKey = false },
                    hasExistingKey = apiKey.isNotBlank(),
                    modelDownloaded = modelDownloaded,
                    modelSizeMb = if (modelDownloaded) onDeviceEngine.modelFile().length() / (1024 * 1024) else null,
                    modelUrlDraft = modelUrlDraft,
                    onModelUrlDraftChange = { modelUrlDraft = it },
                    downloadProgress = downloadProgress,
                    onDownloadModel = {
                        scope.launch {
                            modelDownloader.download(modelUrlDraft.trim(), onDeviceEngine.modelFile())
                                .collect { progress ->
                                    downloadProgress = progress
                                    if (progress is DownloadProgress.Done) {
                                        modelDownloaded = true
                                        downloadProgress = null
                                    }
                                    if (progress is DownloadProgress.Failed) {
                                        error = progress.message
                                    }
                                }
                        }
                    },
                    onDeleteModel = {
                        onDeviceEngine.deleteModel()
                        modelDownloaded = false
                    }
                )
            }

            if (messages.isEmpty() && !editingKey) {
                EmptyChatState(configName = config?.name, onQuickPrompt = { send(it) })
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(messages) { message -> ChatBubble(message) }
                if (sending) item { TypingIndicator() }
            }

            if (error != null) {
                Text(
                    error ?: "",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            Surface(tonalElevation = 2.dp) {
                Row(
                    Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text(if (!ready) "Set up AI Assistant above first…" else "Ask about setup, mods, RAM, crossplay…") },
                        enabled = ready && !sending,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { send(input) })
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(
                        onClick = { send(input) },
                        enabled = ready && !sending && input.isNotBlank()
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                    }
                }
            }
        }
    }
}

@Composable
private fun AiSettingsCard(
    mode: AiEngineMode,
    onModeChange: (AiEngineMode) -> Unit,
    keyDraft: String,
    onKeyDraftChange: (String) -> Unit,
    onSaveKey: () -> Unit,
    onCancel: () -> Unit,
    hasExistingKey: Boolean,
    modelDownloaded: Boolean,
    modelSizeMb: Long?,
    modelUrlDraft: String,
    onModelUrlDraftChange: (String) -> Unit,
    downloadProgress: DownloadProgress?,
    onDownloadModel: () -> Unit,
    onDeleteModel: () -> Unit
) {
    Card(Modifier.fillMaxWidth().padding(16.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text("AI Assistant", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = mode == AiEngineMode.CLOUD_API,
                    onClick = { onModeChange(AiEngineMode.CLOUD_API) },
                    shape = SegmentedButtonDefaults.itemShape(0, 2)
                ) { Text("Cloud API") }
                SegmentedButton(
                    selected = mode == AiEngineMode.ON_DEVICE,
                    onClick = { onModeChange(AiEngineMode.ON_DEVICE) },
                    shape = SegmentedButtonDefaults.itemShape(1, 2)
                ) { Text("On-device") }
            }
            Spacer(Modifier.height(12.dp))

            if (mode == AiEngineMode.CLOUD_API) {
                Text(
                    "Stored encrypted on this device only (Android Keystore), never bundled with the " +
                        "app or synced anywhere. Get a key at console.anthropic.com.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = keyDraft,
                    onValueChange = onKeyDraftChange,
                    singleLine = true,
                    label = { Text("sk-ant-…") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    if (hasExistingKey) {
                        TextButton(onClick = onCancel) { Text("Cancel") }
                        Spacer(Modifier.width(8.dp))
                    }
                    Button(onClick = onSaveKey, enabled = keyDraft.isNotBlank()) { Text("Save") }
                }
            } else {
                Text(
                    "Runs a downloaded model entirely on this device — no network call and no " +
                        "API key at chat time, but it needs a one-time model download (1-4 GB) " +
                        "and more RAM than the cloud option. Point this at a MediaPipe \".task\" " +
                        "build (search \"MediaPipe Gemma task file\" for ready-made ones).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                if (modelDownloaded) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Model ready" + (modelSizeMb?.let { " (~${it} MB)" } ?: ""),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = onDeleteModel) { Text("Remove") }
                    }
                } else {
                    OutlinedTextField(
                        value = modelUrlDraft,
                        onValueChange = onModelUrlDraftChange,
                        singleLine = true,
                        label = { Text("Model .task download URL") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = downloadProgress !is DownloadProgress.InProgress
                    )
                    Spacer(Modifier.height(8.dp))
                    when (val progress = downloadProgress) {
                        is DownloadProgress.InProgress -> {
                            val fraction = progress.totalBytes?.let { progress.bytesRead.toFloat() / it }
                            if (fraction != null) {
                                LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                            } else {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "${progress.bytesRead / (1024 * 1024)} MB downloaded…",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        else -> {
                            Button(
                                onClick = onDownloadModel,
                                enabled = modelUrlDraft.isNotBlank(),
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Download model") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyChatState(configName: String?, onQuickPrompt: (String) -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(40.dp))
        Spacer(Modifier.height(8.dp))
        Text(
            if (configName != null) "Ask anything about $configName" else "Ask anything about hosting a server",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(Modifier.height(12.dp))
        FlowRowFallback(quickPrompts, onQuickPrompt)
    }
}

/** Compose Foundation's FlowRow isn't in this project's BOM version — a simple wrap column of chips instead. */
@Composable
private fun FlowRowFallback(prompts: List<String>, onQuickPrompt: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        prompts.forEach { prompt ->
            AssistChip(onClick = { onQuickPrompt(prompt) }, label = { Text(prompt) })
        }
    }
}

@Composable
private fun ChatBubble(message: ChatMessage) {
    val isUser = message.role == ChatRole.USER
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start) {
        Surface(
            color = if (isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth(0.85f)
        ) {
            Text(
                message.text,
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TypingIndicator() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(8.dp))
        Text("Thinking…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
