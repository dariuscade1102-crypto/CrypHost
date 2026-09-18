package com.cryptmc.app.tunnel

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.regex.Pattern

/**
 * Requirement #2: zero port-forwarding via reverse tunneling.
 *
 * playit.gg is the best fit of the three options mentioned (Playit.gg,
 * Cloudflare Tunnel, ngrok) for a Minecraft-specific tool: it has a
 * purpose-built TCP/UDP relay for Java+Bedrock traffic and a free tier with
 * no bandwidth cap that suits a hobbyist self-hosting on a phone. The agent
 * ships as a small static ARM binary (~10MB) — bundle it under
 * app/src/main/jniLibs/<abi>/libplayit_agent.so (Android requires native
 * libs to live in jniLibs and follow the lib*.so naming convention even
 * though this isn't actually a shared library — it's just how Android lets
 * you ship an executable ELF binary unmodified inside the APK).
 *
 * Cloudflare Tunnel is a straightforward drop-in alternative if you'd rather
 * not depend on a third-party Minecraft-specific relay — swap the binary and
 * argv below for `cloudflared tunnel --url tcp://localhost:25565`.
 */
class TunnelManager(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private var process: Process? = null

    private val _publicAddress = MutableStateFlow<String?>(null)
    val publicAddress = _publicAddress.asStateFlow()

    private val _status = MutableStateFlow(TunnelStatus.STOPPED)
    val status = _status.asStateFlow()

    private val agentBinary: File
        get() = File(context.applicationInfo.nativeLibraryDir, "libplayit_agent.so")

    /**
     * Starts the tunnel pointed at the given local port (25565 for Java,
     * 19132/udp for Bedrock) and parses the agent's stdout for the assigned
     * public address, which it prints once the tunnel handshake completes.
     */
    fun start(localPort: Int, protocol: TunnelProtocol = TunnelProtocol.TCP) {
        if (_status.value == TunnelStatus.RUNNING) return
        require(agentBinary.exists()) {
            "playit agent binary missing — see README 'Bundling the tunnel agent'"
        }

        _status.value = TunnelStatus.CONNECTING
        val secretFile = File(context.filesDir, "playit.toml")

        val builder = ProcessBuilder(
            agentBinary.absolutePath,
            "--secret-path", secretFile.absolutePath,
            "--local-port", localPort.toString(),
            "--proto", protocol.name.lowercase()
        ).redirectErrorStream(true)

        val proc = builder.start()
        process = proc

        scope.launch(Dispatchers.IO) {
            BufferedReader(InputStreamReader(proc.inputStream)).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val text = line ?: continue

                    // First run: the agent prints a claim URL the host visits
                    // once to link the tunnel to their playit.gg account.
                    CLAIM_URL_PATTERN.matcher(text).let { m ->
                        if (m.find()) _claimUrl.value = m.group()
                    }

                    // Steady state: prints the assigned public host:port.
                    ASSIGNED_ADDR_PATTERN.matcher(text).let { m ->
                        if (m.find()) {
                            _publicAddress.value = m.group(1)
                            _status.value = TunnelStatus.RUNNING
                        }
                    }
                }
            }
            _status.value = TunnelStatus.STOPPED
        }
    }

    fun stop() {
        process?.destroy()
        process = null
        _status.value = TunnelStatus.STOPPED
        _publicAddress.value = null
    }

    private val _claimUrl = MutableStateFlow<String?>(null)
    val claimUrl = _claimUrl.asStateFlow()

    companion object {
        // Adjust these to match the actual log format of the agent version
        // you bundle — playit-agent's exact wording has changed across
        // releases, so treat this as a starting point to verify against the
        // binary you ship, not a guaranteed-stable contract.
        private val CLAIM_URL_PATTERN = Pattern.compile("https://playit\\.gg/claim/\\S+")
        private val ASSIGNED_ADDR_PATTERN = Pattern.compile("assigned\\s+([\\w.-]+:\\d+)")
    }
}

enum class TunnelProtocol { TCP, UDP }
enum class TunnelStatus { STOPPED, CONNECTING, RUNNING }
