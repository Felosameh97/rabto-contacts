package com.example.auth

import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.example.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        trySend(firebaseAuth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

object GoogleAuthHelper {
    private const val TAG = "GoogleAuthHelper"

    private fun resolveWebClientId(context: Context): String {
        val direct = runCatching { context.getString(R.string.default_web_client_id).trim() }.getOrDefault("")
        if (direct.isNotBlank()) return direct
        val resId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
        return if (resId != 0) context.getString(resId).trim() else ""
    }

    suspend fun attemptAutoSignIn(context: Context, auth: FirebaseAuth): FirebaseUser? {
        if (auth.currentUser != null) return auth.currentUser
        val webClientId = resolveWebClientId(context)
        if (webClientId.isBlank()) return null

        return try {
            val credentialManager = CredentialManager.create(context)
            val option = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(true)
                .setServerClientId(webClientId)
                .setAutoSelectEnabled(true)
                .build()
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(option)
                .build()
            val result = credentialManager.getCredential(context, request)
            handleCredential(result.credential, auth)
        } catch (_: NoCredentialException) {
            null
        } catch (e: Exception) {
            Log.d(TAG, "Silent sign-in skipped: ${e.message}")
            null
        }
    }

    suspend fun signInWithGoogleButton(context: Context, auth: FirebaseAuth): Result<FirebaseUser> = runCatching {
        val webClientId = resolveWebClientId(context)
        require(webClientId.isNotBlank()) { "Web Client ID غير مهيأ في google-services.json" }
        val credentialManager = CredentialManager.create(context)
        val option = GetSignInWithGoogleOption.Builder(webClientId).build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(option)
            .build()
        val result = credentialManager.getCredential(context, request)
        handleCredential(result.credential, auth)
            ?: throw IllegalStateException("تعذر إتمام تسجيل الدخول عبر Google")
    }.recoverCatching { e ->
        if (e is GetCredentialCancellationException) {
            Log.w(TAG, "Google Sign-In cancelled by user or credential configuration: ${e.message}", e)
            throw IllegalStateException("تم إلغاء تسجيل الدخول")
        }
        throw e
    }

    private suspend fun handleCredential(
        credential: androidx.credentials.Credential,
        auth: FirebaseAuth,
    ): FirebaseUser? {
        if (credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            val googleIdCred = GoogleIdTokenCredential.createFrom(credential.data)
            val firebaseCred = GoogleAuthProvider.getCredential(googleIdCred.idToken, null)
            val authResult = auth.signInWithCredential(firebaseCred).await()
            return authResult.user
        }
        return null
    }
}
