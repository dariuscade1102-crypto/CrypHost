package com.cryptmc.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.cryptmc.app.ui.nav.AppRoot

/**
 * Launcher activity for the CryptMc dashboard.
 *
 * The activity deliberately does not provision the optional embedded JRE or
 * start the server foreground service during app launch. The source checkout
 * does not include the large ABI-specific JRE assets, and an idle dashboard
 * should not create a foreground service. Server startup owns those actions
 * when a user actually starts a configured server.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppRoot()
                }
            }
        }
    }
}
