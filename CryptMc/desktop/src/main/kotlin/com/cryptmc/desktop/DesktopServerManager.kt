package com.cryptmc.desktop

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.concurrent.ConcurrentHashMap

/**
 * Desktop's answer to app's server/ServerProcessManager.kt — same shape
 * (ProcessBuilder around a `java -jar` invocation, stdout pumped to a
 * SharedFlow the console collects from), but supports running SEVERAL
 * servers at once (indexed by server id), since a desktop isn't fighting
 * OS foreground-service memory limits the way a phone is.
 *
 * Uses whatever `java` is on PATH/JAVA_HOME rather than an embedded JRE —
 * see build.gradle.kts' class doc for why that's fine here but not on
 * Android.
 */
class DesktopServerManager(private val scope: CoroutineScope) {

    private val processes = ConcurrentHashMap<String, Process>()
    private val stdins = ConcurrentHashMap<String, OutputStreamWriter>()

    private val _consoleLines = MutableSharedFlow<Pair<String, String>>(replay = 1000) // serverId to line
    val consoleLines = _consoleLines.asSharedFlow()

    private val _statuses = MutableStateFlow<Map<String, DesktopServerRuntimeStatus>>(emptyMap())
    val statuses: StateFlow<Map<String, DesktopServerRuntimeStatus>> = _statuses.asStateFlow()

    fun start(config: DesktopServerConfig) {
        check(processes[config.id] == null) { "${config.name} is already running" }
        val workDir = File(config.workingDir).apply { mkdirs() }
        val jar = File(workDir, config.jarFileName)
        require(jar.exists()) { "Server jar not found: ${jar.path}. Download or copy one in first." }

        ensureEula(workDir, config.eulaAccepted)

        val javaBinary = resolveJavaBinary()
        val command = listOf(
            javaBinary,
            "-Xms${config.minRamMb}M",
            "-Xmx${config.maxRamMb}M",
            "-jar", jar.absolutePath,
            "nogui"
        )

        val process = ProcessBuilder(command).directory(workDir).redirectErrorStream(true).start()
        processes[config.id] = process
        stdins[config.id] = OutputStreamWriter(process.outputStream)
        setStatus(config.id) { it.copy(running = true, pid = process.pid()) }

        scope.launch(Dispatchers.IO) {
            BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    _consoleLines.emit(config.id to (line ?: continue))
                }
            }
        }

        scope.launch(Dispatchers.IO) {
            val exitCode = process.waitFor()
            setStatus(config.id) { it.copy(running = false, pid = null) }
            processes.remove(config.id)
            stdins.remove(config.id)
            _consoleLines.emit(config.id to "[CryptMc] Server process exited with code $exitCode")
        }
    }

    fun sendCommand(serverId: String, command: String) {
        stdins[serverId]?.let { it.write("$command\n"); it.flush() }
    }

    fun stop(serverId: String, gracePeriodMs: Long = 15_000) {
        val process = processes[serverId] ?: return
        sendCommand(serverId, "stop")
        scope.launch(Dispatchers.IO) {
            val exited = process.waitFor(gracePeriodMs, java.util.concurrent.TimeUnit.MILLISECONDS)
            if (!exited) process.destroyForcibly()
        }
    }

    fun isRunning(serverId: String): Boolean = statuses.value[serverId]?.running == true

    private fun setStatus(serverId: String, transform: (DesktopServerRuntimeStatus) -> DesktopServerRuntimeStatus) {
        val current = _statuses.value[serverId] ?: DesktopServerRuntimeStatus()
        _statuses.value = _statuses.value + (serverId to transform(current))
    }

    private fun ensureEula(workDir: File, accepted: Boolean) {
        File(workDir, "eula.txt").writeText("eula=$accepted\n")
    }

    /** JAVA_HOME first (explicit, matches what jpackage's bundled runtime would set), falls back to PATH's `java`. */
    private fun resolveJavaBinary(): String {
        val javaHome = System.getenv("JAVA_HOME")
        if (javaHome != null) {
            val candidate = File(javaHome, if (isWindows()) "bin/java.exe" else "bin/java")
            if (candidate.exists()) return candidate.absolutePath
        }
        return if (isWindows()) "java.exe" else "java"
    }

    private fun isWindows() = System.getProperty("os.name").lowercase().contains("win")
}
