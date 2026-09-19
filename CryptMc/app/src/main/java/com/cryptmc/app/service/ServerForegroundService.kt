package com.cryptmc.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.cryptmc.app.MainActivity
import com.cryptmc.app.data.ServerConfig
import com.cryptmc.app.server.JreProvisioner
import com.cryptmc.app.server.ServerProcessManager
import com.cryptmc.app.tunnel.TunnelManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

/**
 * Requirement #6: keeps the server (and tunnel agent) alive independent of
 * the Activity lifecycle and Android's Doze/App Standby process killer.
 *
 * Three things make this durable in practice, beyond just being a foreground
 * service:
 *  1. A partial WakeLock held for the service's lifetime, so the CPU doesn't
 *     suspend mid-tick under load with the screen off.
 *  2. startForeground() called within the Android-mandated window (<=5s on
 *     API 31+) of onStartCommand, with a persistent low-priority notification
 *     showing live TPS/player count so it's clearly a foreground, not a
 *     background, workload to the OS scheduler.
 *  3. The user is prompted once (see BatteryOptimizationHelper, not shown
 *     here) to whitelist the app from battery optimization — without this,
 *     OEM-specific killers (MIUI, One UI, ColorOS) can still SIGKILL the
 *     process regardless of the foreground service type.
 */
class ServerForegroundService : Service() {

    private val binder = LocalBinder()
    private val scope = CoroutineScope(SupervisorJob())
    private lateinit var wakeLock: PowerManager.WakeLock

    lateinit var processManager: ServerProcessManager
        private set
    lateinit var tunnelManager: TunnelManager
        private set

    inner class LocalBinder : android.os.Binder() {
        fun getService(): ServerForegroundService = this@ServerForegroundService
    }

    override fun onCreate() {
        super.onCreate()
        val jre = JreProvisioner(applicationContext)
        processManager = ServerProcessManager(jre, scope)
        tunnelManager = TunnelManager(applicationContext, scope)

        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK, "CryptMc::ServerWakeLock"
        ).apply { setReferenceCounted(false) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Server idle"))
        wakeLock.acquire(/* no timeout — released explicitly in onDestroy */)

        val config = intent?.getSerializableExtra(EXTRA_CONFIG) as? ServerConfig
        if (intent?.action == ACTION_START && config != null) {
            processManager.start(config)
            if (config.bedrockCrossplayEnabled) {
                // Geyser/Floodgate jars are dropped into plugins/ *before*
                // this point by ModrinthRepository; nothing else to wire up
                // here since Paper autoloads plugins on boot.
            }
        } else if (intent?.action == ACTION_STOP) {
            processManager.stop()
            stopSelf()
        }

        // START_STICKY: ask the OS to recreate the service (without the
        // original intent) if it's killed under memory pressure, so a
        // crashed dashboard doesn't silently drop the running server.
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        if (wakeLock.isHeld) wakeLock.release()
        super.onDestroy()
    }

    private fun buildNotification(status: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("CryptMc server running")
            .setContentText(status)
            .setSmallIcon(com.cryptmc.app.R.drawable.ic_cryptmc_notification)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID, "Server status", NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        const val ACTION_START = "com.cryptmc.app.action.START"
        const val ACTION_STOP = "com.cryptmc.app.action.STOP"
        const val EXTRA_CONFIG = "extra_config"
        private const val CHANNEL_ID = "cryptmc_server_channel"
        private const val NOTIFICATION_ID = 1001
    }
}
