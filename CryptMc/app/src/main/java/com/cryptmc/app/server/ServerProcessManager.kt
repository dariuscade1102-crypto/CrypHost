package com.cryptmc.app.server

import com.cryptmc.app.data.ServerConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter

/**
 * Owns the lifecycle of exactly one running server process. Runs inside
 * ServerForegroundService so the process survives Activity destruction.
 *
 * Requirement #1 (local self-hosting engine) and the "live console" half of
 * requirement #4 live here.
 */
class ServerProcessManager(
    private val jreProvisioner: JreProvisioner,
    private val scope: CoroutineScope
) {
    private var process: Process? = null
    private var stdin: OutputStreamWriter? = null

    private val _consoleLines = MutableSharedFlow<String>(replay = 500)
    val consoleLines = _consoleLines.asSharedFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning = _isRunning.asStateFlow()

    fun start(config: ServerConfig) {
        check(!_isRunning.value) { "Server already running" }
        require(jreProvisioner.isProvisioned()) { "JRE not provisioned yet" }

        val workDir = File(config.workingDir).apply { mkdirs() }
        ensureEula(workDir, config.eulaAccepted)

        val command = buildCommand(config)
        val builder = ProcessBuilder(command)
            .directory(workDir)
            .redirectErrorStream(true)

        val proc = builder.start()
        process = proc
        stdin = OutputStreamWriter(proc.outputStream)
        _isRunning.value = true

        // Pump stdout -> shared flow the dashboard console collects from
        scope.launch(Dispatchers.IO) {
            BufferedReader(InputStreamReader(proc.inputStream)).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    _consoleLines.emit(line ?: continue)
                }
            }
            _isRunning.value = false
        }

        // Watch for the process dying on its own (crash, OOM-kill, `stop`)
        scope.launch(Dispatchers.IO) {
            val exitCode = proc.waitFor()
            _isRunning.value = false
            _consoleLines.emit("[CryptMc] Server process exited with code $exitCode")
        }
    }

    /** Sends a raw command to the server console, e.g. "kick Steve griefing" */
    fun sendCommand(command: String) {
        val writer = stdin ?: return
        writer.write("$command\n")
        writer.flush()
    }

    /** Graceful shutdown via the server's own "stop" command; falls back to
     *  destroy() if it hasn't exited within [gracePeriodMs]. */
    fun stop(gracePeriodMs: Long = 15_000) {
        val proc = process ?: return
        sendCommand("stop")
        scope.launch(Dispatchers.IO) {
            val exited = proc.waitFor(gracePeriodMs, java.util.concurrent.TimeUnit.MILLISECONDS)
            if (!exited) {
                proc.destroyForcibly()
            }
            process = null
            stdin = null
        }
    }

    private fun buildCommand(config: ServerConfig): List<String> {
        val java = jreProvisioner.javaBinary.absolutePath
        return buildList {
            add(java)
            add("-Xms${config.minRamMb}M")
            add("-Xmx${config.maxRamMb}M")
            addAll(config.javaFlags.split(Regex("\\s+")).filter { it.isNotBlank() })
            add("-jar")
            add(config.jarPath ?: "")
            add("nogui")
        }
    }

    private fun ensureEula(workDir: File, accepted: Boolean) {
        // Mojang requires an explicit EULA acceptance file; we only ever
        // write "true" here if the user has actually agreed to it in-app.
        val eulaFile = File(workDir, "eula.txt")
        eulaFile.writeText("eula=${accepted}\n")
    }
}
