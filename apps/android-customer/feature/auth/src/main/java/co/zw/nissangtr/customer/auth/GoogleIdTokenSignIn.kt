package co.zw.nissangtr.customer.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import java.security.MessageDigest
import java.util.UUID

/**
 * Credential Manager → Google ID token for Supabase [IDToken] sign-in.
 * [serverClientId] must be the **Web** OAuth client ID (not the Android client).
 */
object GoogleIdTokenSignIn {
    data class Result(val idToken: String, val rawNonce: String)

    suspend fun requestIdToken(context: Context, serverClientId: String): Result {
        require(serverClientId.isNotBlank()) { "GOOGLE_WEB_CLIENT_ID / GOOGLE_SERVER_CLIENT_ID required" }

        val rawNonce = UUID.randomUUID().toString()
        val hashedNonce = sha256Hex(rawNonce)

        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(serverClientId)
            .setNonce(hashedNonce)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        val credentialManager = CredentialManager.create(context)
        val result = credentialManager.getCredential(request = request, context = context)
        val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(result.credential.data)
        return Result(idToken = googleIdTokenCredential.idToken, rawNonce = rawNonce)
    }

    fun userMessage(e: Throwable): String = when (e) {
        is GetCredentialCancellationException -> "Google sign-in cancelled"
        is NoCredentialException ->
            "No Google account available. Add an account on this device, or check SHA-1 / Android OAuth client setup."
        is GoogleIdTokenParsingException -> "Could not parse Google ID token"
        is GetCredentialException ->
            co.zw.nissangtr.customer.rpc.UserFacingErrors.from(e, "Google sign-in failed")
        else -> co.zw.nissangtr.customer.rpc.UserFacingErrors.from(e, "Google sign-in failed")
    }

    private fun sha256Hex(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}
