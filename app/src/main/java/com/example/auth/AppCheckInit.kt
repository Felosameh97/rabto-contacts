package com.example.auth

import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.BuildConfig
import com.example.R
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.recaptcha.RecaptchaAppCheckProviderFactory
import java.util.UUID

private const val TAG = "AppCheckInit"

private const val DEBUG_TOKEN_EXTRA = "FIREBASE_APPCHECK_DEBUG_TOKEN"
private const val DEBUG_STORE_PREFIX = "com.google.firebase.appcheck.debug.store."
private const val DEBUG_SECRET_KEY = "com.google.firebase.appcheck.debug.DEBUG_SECRET"
private const val RECAPTCHA_SITE_KEY_RES = "recaptcha_site_key"

private const val HARNESS_PREFS = "rabta_appcheck_meta"
private const val KEY_HARNESS_TOKEN_INJECTED = "harness_token_injected"
private const val KEY_MANUAL_DEBUG_TOKEN = "manual_debug_token"

enum class AppCheckActiveMode {
    RECAPTCHA_ENTERPRISE,
    DEBUG_HARNESS,
    DEBUG_MANUAL,
    DEBUG_FALLBACK,
    UNCONFIGURED,
}

@Volatile
var currentAppCheckMode: AppCheckActiveMode = AppCheckActiveMode.UNCONFIGURED
    private set

fun configureAppCheck(context: Context, intent: Intent?) {
    try {
        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(context)
        }

        val metaPrefs = context.getSharedPreferences(HARNESS_PREFS, Context.MODE_PRIVATE)
        val intentDebugToken = intent?.getStringExtra(DEBUG_TOKEN_EXTRA)?.trim().orEmpty()
        val manualDebugToken = metaPrefs.getString(KEY_MANUAL_DEBUG_TOKEN, null)?.trim().orEmpty()
        val hasHarnessToken = metaPrefs.getBoolean(KEY_HARNESS_TOKEN_INJECTED, false)

        if (BuildConfig.DEBUG && intentDebugToken.isNotEmpty()) {
            // 1. AI Studio Streaming Emulator / Test Harness injected a registered debug token
            writeDebugSecretSynchronously(context, intentDebugToken)
            metaPrefs.edit().putBoolean(KEY_HARNESS_TOKEN_INJECTED, true).commit()
            FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                DebugAppCheckProviderFactory.getInstance()
            )
            currentAppCheckMode = AppCheckActiveMode.DEBUG_HARNESS
            Log.d(TAG, "DebugAppCheckProviderFactory installed with harness debug token.")
            return
        }

        if (BuildConfig.DEBUG && manualDebugToken.isNotEmpty()) {
            // 2. User manually registered and saved a Firebase App Check debug token in Settings
            writeDebugSecretSynchronously(context, manualDebugToken)
            FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                DebugAppCheckProviderFactory.getInstance()
            )
            currentAppCheckMode = AppCheckActiveMode.DEBUG_MANUAL
            Log.d(TAG, "DebugAppCheckProviderFactory installed with manual debug token.")
            return
        }

        if (BuildConfig.DEBUG && hasHarnessToken) {
            // 3. Subsequent launch on the test emulator where harness already injected a valid token
            FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                DebugAppCheckProviderFactory.getInstance()
            )
            currentAppCheckMode = AppCheckActiveMode.DEBUG_HARNESS
            Log.d(TAG, "DebugAppCheckProviderFactory installed from persisted harness debug token.")
            return
        }

        // 4. Real user / colleague phone (or Release build) with no harness debug token:
        // Use reCAPTCHA Enterprise so Firebase does not reject an unregistered local Debug UUID.
        val siteKey = runCatching { context.getString(R.string.recaptcha_site_key).trim() }.getOrDefault("")
            .ifBlank {
                val resId = context.resources.getIdentifier(RECAPTCHA_SITE_KEY_RES, "string", context.packageName)
                if (resId != 0) context.getString(resId).trim() else ""
            }
        if (siteKey.isNotBlank()) {
            FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                RecaptchaAppCheckProviderFactory.getInstance(siteKey)
            )
            currentAppCheckMode = AppCheckActiveMode.RECAPTCHA_ENTERPRISE
            Log.d(TAG, "RecaptchaAppCheckProviderFactory installed.")
            return
        } else {
            Log.e(TAG, "recaptcha_site_key resource string is blank or missing.")
        }

        if (BuildConfig.DEBUG) {
            FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                DebugAppCheckProviderFactory.getInstance()
            )
            currentAppCheckMode = AppCheckActiveMode.DEBUG_FALLBACK
            Log.w(TAG, "Fell back to DebugAppCheckProviderFactory because recaptcha_site_key was missing.")
        }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to initialize Firebase App Check", e)
    }
}

private fun writeDebugSecretSynchronously(context: Context, token: String) {
    val persistenceKey = FirebaseApp.getInstance().persistenceKey
    val debugPrefs = context.getSharedPreferences(
        "$DEBUG_STORE_PREFIX$persistenceKey",
        Context.MODE_PRIVATE
    )
    // Synchronous commit() is required before DebugAppCheckProviderFactory initializes.
    debugPrefs.edit()
        .putString(DEBUG_SECRET_KEY, token)
        .commit()
}

fun getManualDebugToken(context: Context): String {
    return context.getSharedPreferences(HARNESS_PREFS, Context.MODE_PRIVATE)
        .getString(KEY_MANUAL_DEBUG_TOKEN, null)
        .orEmpty()
}

fun saveManualDebugToken(context: Context, token: String) {
    val trimmed = token.trim()
    context.getSharedPreferences(HARNESS_PREFS, Context.MODE_PRIVATE)
        .edit()
        .putString(KEY_MANUAL_DEBUG_TOKEN, trimmed)
        .commit()
    if (trimmed.isNotEmpty()) {
        runCatching {
            if (FirebaseApp.getApps(context).isEmpty()) {
                FirebaseApp.initializeApp(context)
            }
            writeDebugSecretSynchronously(context, trimmed)
        }
    }
}

fun getOrCreateLocalDebugUuid(context: Context): String {
    return runCatching {
        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(context)
        }
        val persistenceKey = FirebaseApp.getInstance().persistenceKey
        val debugPrefs = context.getSharedPreferences(
            "$DEBUG_STORE_PREFIX$persistenceKey",
            Context.MODE_PRIVATE
        )
        val existing = debugPrefs.getString(DEBUG_SECRET_KEY, null)?.trim()
        if (!existing.isNullOrEmpty()) {
            existing
        } else {
            val generated = UUID.randomUUID().toString()
            debugPrefs.edit().putString(DEBUG_SECRET_KEY, generated).commit()
            generated
        }
    }.getOrElse { "" }
}

fun getAppCheckModeLabelAr(): String = when (currentAppCheckMode) {
    AppCheckActiveMode.RECAPTCHA_ENTERPRISE -> "reCAPTCHA Enterprise (تلقائي للأجهزة الحقيقية)"
    AppCheckActiveMode.DEBUG_HARNESS -> "Debug Token (بيئة الاختبار)"
    AppCheckActiveMode.DEBUG_MANUAL -> "Debug Token (مخصص يدوياً)"
    AppCheckActiveMode.DEBUG_FALLBACK -> "Debug Fallback (يحتاج تسجيل UUID)"
    AppCheckActiveMode.UNCONFIGURED -> "غير مهيأ"
}
