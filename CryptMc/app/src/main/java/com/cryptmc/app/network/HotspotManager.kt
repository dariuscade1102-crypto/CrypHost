package com.cryptmc.app.network

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.Inet4Address
import java.net.NetworkInterface

sealed class HotspotState {
    data object Idle : HotspotState()
    data object Starting : HotspotState()
    data class Active(val ssid: String, val password: String, val localIp: String?) : HotspotState()
    data class Failed(val reason: String) : HotspotState()
}

/**
 * Mobile-only "hotspot hosting": instead of relying on an existing router
 * (LOCAL_ONLY tunnel mode) or a public tunnel (PLAYIT_GG/CLOUDFLARE), the
 * phone/tablet itself becomes the Wi-Fi access point via
 * WifiManager.startLocalOnlyHotspot — no internet connection or router
 * required at all, which is the point: a field, a car trip, a venue with no
 * guest network. Other players join THIS device's Wi-Fi network, then use
 * Minecraft's "Direct Connect" with this device's local IP and the
 * server's port.
 *
 * There is no desktop equivalent wired up (see TunnelMode.HOTSPOT's class
 * doc + README) — Windows/macOS/Linux don't expose one portable "become an
 * access point" API the way Android's LocalOnlyHotspot does, and a desktop
 * companion is far more likely to already be tethered to real Wi-Fi/Ethernet
 * anyway, where LOCAL_ONLY tunnel mode already covers "same network" play.
 *
 * Requirements this depends on, all Android-side, none of which this class
 * can grant itself:
 *  - ACCESS_FINE_LOCATION must be granted at runtime before calling start()
 *    — required by the platform API itself (hotspot scanning has historically
 *    been gated behind location permission), not an CryptMc-specific choice.
 *  - The app must be in the foreground when starting the hotspot; Android
 *    tears down LocalOnlyHotspot automatically if the app backgrounds, which
 *    is a platform limitation this class can't work around. If you want the
 *    hotspot to survive backgrounding the way the server process itself does
 *    (ServerForegroundService), that's the one piece this scaffold doesn't
 *    solve — flagging it rather than silently pretending it's covered.
 *  - Only one LocalOnlyHotspot reservation can be active per app at a time;
 *    calling start() while already Active is a no-op that re-emits the
 *    current state.
 */
class HotspotManager(private val context: Context) {

    private val wifiManager: WifiManager by lazy {
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    }

    private var reservation: WifiManager.LocalOnlyHotspotReservation? = null

    private val _state = MutableStateFlow<HotspotState>(HotspotState.Idle)
    val state: StateFlow<HotspotState> = _state.asStateFlow()

    fun hasRequiredPermissions(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /** Call from an Activity/foreground context only — see class doc. */
    fun start() {
        if (_state.value is HotspotState.Active || _state.value is HotspotState.Starting) return
        if (!hasRequiredPermissions()) {
            _state.value = HotspotState.Failed("Location permission is required to start a hotspot on Android.")
            return
        }

        _state.value = HotspotState.Starting
        wifiManager.startLocalOnlyHotspot(object : WifiManager.LocalOnlyHotspotCallback() {
            override fun onStarted(res: WifiManager.LocalOnlyHotspotReservation) {
                reservation = res
                val (ssid, password) = credentialsFrom(res)
                _state.value = HotspotState.Active(
                    ssid = ssid ?: "(unknown SSID)",
                    password = password ?: "(unknown password)",
                    localIp = currentLocalIpAddress()
                )
            }

            override fun onStopped() {
                reservation = null
                _state.value = HotspotState.Idle
            }

            override fun onFailed(reason: Int) {
                reservation = null
                _state.value = HotspotState.Failed(failureReasonText(reason))
            }
        }, null)
    }

    fun stop() {
        reservation?.close()
        reservation = null
        _state.value = HotspotState.Idle
    }

    @Suppress("DEPRECATION")
    private fun credentialsFrom(res: WifiManager.LocalOnlyHotspotReservation): Pair<String?, String?> {
        // SoftApConfiguration is the non-deprecated surface from API 30 on;
        // WifiConfiguration still works below that and as a fallback if a
        // vendor build's SoftApConfiguration.getWifiSsid() ever returns null.
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val softApConfig = res.softApConfiguration
            val ssid = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                softApConfig?.wifiSsid?.toString()
            } else {
                softApConfig?.ssid
            }
            (ssid ?: res.wifiConfiguration?.SSID) to (softApConfig?.passphrase ?: res.wifiConfiguration?.preSharedKey)
        } else {
            res.wifiConfiguration?.SSID to res.wifiConfiguration?.preSharedKey
        }
    }

    /**
     * Best-effort local IP so the UI can tell the host "give players this
     * address": scans network interfaces for a non-loopback IPv4 address.
     * Android doesn't hand LocalOnlyHotspot's own interface IP back
     * directly, so this is the standard workaround — it can occasionally
     * pick up a different active interface's address on a device with
     * several radios up at once (e.g. Wi-Fi + Ethernet dock); worth
     * double-checking against Settings > Wi-Fi > hotspot details if a
     * player reports it not working.
     */
    private fun currentLocalIpAddress(): String? = runCatching {
        NetworkInterface.getNetworkInterfaces().asSequence()
            .flatMap { it.inetAddresses.asSequence() }
            .filterIsInstance<Inet4Address>()
            .firstOrNull { !it.isLoopbackAddress }
            ?.hostAddress
    }.getOrNull()

    private fun failureReasonText(reason: Int): String = when (reason) {
        WifiManager.LocalOnlyHotspotCallback.ERROR_NO_CHANNEL -> "No Wi-Fi channel available for a hotspot right now."
        WifiManager.LocalOnlyHotspotCallback.ERROR_GENERIC -> "The hotspot failed to start (generic platform error)."
        WifiManager.LocalOnlyHotspotCallback.ERROR_INCOMPATIBLE_MODE -> "Wi-Fi is in a mode that's incompatible with a local hotspot right now (e.g. already tethering)."
        WifiManager.LocalOnlyHotspotCallback.ERROR_TETHERING_DISALLOWED -> "This device/account isn't allowed to start tethering or a hotspot (MDM/carrier restriction)."
        else -> "The hotspot failed to start (code $reason)."
    }
}
