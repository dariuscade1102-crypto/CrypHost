package com.cryptmc.app.ui.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.cryptmc.app.auth.AuthResult
import com.cryptmc.app.auth.GoogleAuthManager
import kotlinx.coroutines.launch

// Replace with the Web client ID from Google Cloud Console for this app's
// project (APIs & Services > Credentials). This is a placeholder — sign-in
// will fail with an auth error until it's swapped for a real one.
private const val GOOGLE_WEB_CLIENT_ID = "YOUR_WEB_CLIENT_ID.apps.googleusercontent.com"

@Composable
fun ProfileScreen(showAccountSection: Boolean, onOpenAiAssistant: () -> Unit = {}) {
    val context = LocalContext.current
    val authManager = remember { GoogleAuthManager(context, GOOGLE_WEB_CLIENT_ID) }
    val user by authManager.currentUser.collectAsState()
    val scope = rememberCoroutineScope()
    var signInError by remember { mutableStateOf<String?>(null) }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text("Profile", style = MaterialTheme.typography.headlineMedium) }

        if (showAccountSection) {
            item {
                if (user == null) {
                    SignedOutCard(
                        onSignIn = {
                            scope.launch {
                                when (val result = authManager.signIn(filterByAuthorizedAccounts = false)) {
                                    is AuthResult.Success -> signInError = null
                                    is AuthResult.Failure -> signInError = result.message
                                    AuthResult.Cancelled -> Unit
                                }
                            }
                        },
                        error = signInError
                    )
                } else {
                    SignedInCard(email = user!!.email, onSignOut = { authManager.signOut() })
                }
            }
        }

        item { UpgradeCard() }

        item {
            SettingsGroupCard {
                SettingsRow(
                    Icons.Filled.AutoAwesome,
                    "AI Assistant",
                    "Ask for help setting up or troubleshooting a server",
                    modifier = Modifier.clickable { onOpenAiAssistant() }
                )
                Divider()
                SettingsRow(Icons.Filled.Dashboard, "Web Dashboard", "Manage your server remotely", trailingIcon = Icons.Filled.OpenInNew)
                Divider()
                SettingsRow(Icons.Filled.Language, "Language", "System Default")
                Divider()
                SettingsRow(Icons.Filled.Contrast, "Theme", "Dark")
                Divider()
                SettingsRow(Icons.Filled.Explore, "Guided tour", "Walk through setting up a server")
            }
        }

        item {
            Text("SUPPORT", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            SettingsGroupCard {
                SettingsRow(Icons.Filled.Search, "FAQ & Help", null)
                Divider()
                SettingsRow(Icons.Filled.Description, "Legal & Licenses", null)
            }
        }

        if (showAccountSection && user != null) {
            item {
                OutlinedButton(
                    onClick = { authManager.signOut() },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("LOG OUT") }
            }
        }

        item {
            Text(
                "CryptMc.com v1.8.9",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
private fun SignedOutCard(onSignIn: () -> Unit, error: String?) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Filled.AccountCircle, contentDescription = null, modifier = Modifier.size(48.dp))
            Spacer(Modifier.height(8.dp))
            Text("Not signed in", style = MaterialTheme.typography.titleMedium)
            Text(
                "Sign in to sync your servers and unlock Premium",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = onSignIn, modifier = Modifier.fillMaxWidth()) {
                Text("Sign in with Google")
            }
            if (error != null) {
                Spacer(Modifier.height(8.dp))
                Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun SignedInCard(email: String, onSignOut: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text(email, style = MaterialTheme.typography.titleMedium)
        Text("Member since 2026", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        AssistChip(onClick = {}, enabled = false, label = { Text("FREE") })
        Spacer(Modifier.height(16.dp))
        SettingsGroupCard {
            SettingsRow(Icons.Filled.Login, "Sign-in Method", "Google Account")
        }
    }
}

@Composable
private fun UpgradeCard() {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("PLAN TIER", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Free Account", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text("Upgrade to Premium to unlock:", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            PerkRow(Icons.Filled.Timer, "Unlimited tunneling", "No 2-hour sessions and priority queue")
            PerkRow(Icons.Filled.Public, "Custom Subdomain", "Your own *.CryptMc.app address")
            PerkRow(Icons.Filled.Forum, "Discord premium role", "Exclusive rank in the CryptMc Discord server")
            Spacer(Modifier.height(12.dp))
            Button(onClick = { /* opens billing flow / Play Billing */ }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.WorkspacePremium, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Upgrade Now")
            }
        }
    }
}

@Composable
private fun PerkRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SettingsGroupCard(content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) { Column { content() } }
}

@Composable
private fun SettingsRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String?,
    trailingIcon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Filled.ChevronRight,
    modifier: Modifier = Modifier
) {
    Row(
        modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Icon(trailingIcon, contentDescription = null)
    }
}
