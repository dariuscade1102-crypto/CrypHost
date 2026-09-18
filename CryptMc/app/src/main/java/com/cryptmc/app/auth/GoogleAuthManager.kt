package com.cryptmc.app.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.SecureRandom

data class CryptMcUser(
    val email: String,
    val displayName: String?,
    val idToken: String
)

sealed class AuthResult {
    data class Success(val user: CryptMcUser) : AuthResult()
    data class Failure(val message: String) : AuthResult()
    data object Cancelled : AuthResult()
}

/**
 * Wraps Credential Manager's Sign in with Google flow. This gets you an
 * identity (email + a verifiable ID token) — it is NOT itself an account
 * system. If this app has its own backend for premium tiers / synced
 * server configs, send the returned idToken there over HTTPS and let the
 * backend verify it against Google's public keys and mint its own session;
 * never trust an ID token's claims client-side for anything security-
 * sensitive beyond "which email is this."
 *
 * Requires a Web client ID from Google Cloud Console (APIs & Services >
 * Credentials > OAuth 2.0 Client IDs > Web application) — pass it as
 * [serverClientId]. This is intentionally the *web* client ID even for an
 * Android app; that's how Google's ID-token flow is designed; the Android
 * client ID (tied to your package name + SHA-1) is registered in the same
 * Cloud Console project but isn't passed in code.
 */
class GoogleAuthManager(
    private val context: Context,
    private val serverClientId: String
) {
    private val credentialManager = CredentialManager.create(context)

    private val _currentUser = MutableStateFlow<CryptMcUser?>(null)
    val currentUser: StateFlow<CryptMcUser?> = _currentUser.asStateFlow()

    /**
     * Launches the Google account picker. [filterByAuthorizedAccounts] true
     * shows only accounts that have already signed into this app before —
     * use false for the first-ever sign-in attempt, true for silent
     * re-auth on subsequent app launches.
     */
    suspend fun signIn(filterByAuthorizedAccounts: Boolean): AuthResult {
        val nonce = generateNonce()
        val googleIdOption = GetSignInWithGoogleOption.Builder(serverClientId)
            .setNonce(nonce)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        return try {
            val response = credentialManager.getCredential(context, request)
            val credential = response.credential

            if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val user = CryptMcUser(
                    email = googleIdTokenCredential.id,
                    displayName = googleIdTokenCredential.displayName,
                    idToken = googleIdTokenCredential.idToken
                )
                _currentUser.value = user
                AuthResult.Success(user)
            } else {
                AuthResult.Failure("Unexpected credential type returned")
            }
        } catch (e: GetCredentialException) {
            // NoCredentialException specifically means "no Google account on
            // this device matched" — surfaced as a normal failure rather
            // than a crash, since a device with no Google account at all is
            // a completely valid state for a self-hosting app to run in.
            AuthResult.Failure(e.message ?: "Sign-in failed")
        }
    }

    fun signOut() {
        _currentUser.value = null
        // Credential Manager has no server-side session to revoke here —
        // this only clears local app state. If you add a backend session
        // token on top of the ID token (see class doc), invalidate that
        // token server-side too on sign-out.
    }

    private fun generateNonce(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
