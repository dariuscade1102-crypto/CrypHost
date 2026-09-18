package com.cryptmc.app

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.cryptmc.app.server.JreProvisioner
import com.cryptmc.app.service.ServerForegroundService
import com.cryptmc.app.ui.nav.AppRoot

/**
 * Entry point. Owns exactly one job: bind to ServerForegroundService (so it
 * starts if not already running) and host the Compose navigation graph.
 * Per-server process/tunnel control lives in the service and its
 * managers — see ServerForegroundService, ServerProcessManager,
 * TunnelManager — not here, so backgrounding or destroying this Activity
 * never interrupts a running server (requirement #6).
 */
class MainActivity : ComponentActivity() {

    private var boundService: ServerForegroundService? = null

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            boundService = (binder as ServerForegroundService.LocalBinder).getService()
        }
        override fun onServiceDisconnected(name: ComponentName?) { boundService = null }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Kick off JRE unpack on first run (idempotent, see JreProvisioner).
        JreProvisioner(applicationContext).provisionIfNeeded()

        val serviceIntent = Intent(this, ServerForegroundService::class.java)
        startForegroundService(serviceIntent)
        bindService(serviceIntent, connection, Context.BIND_AUTO_CREATE)

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppRoot()
                }
            }
        }
    }

    override fun onDestroy() {
        unbindService(connection)
        super.onDestroy()
        // Deliberately not stopping the service here — see class doc.
    }
}
