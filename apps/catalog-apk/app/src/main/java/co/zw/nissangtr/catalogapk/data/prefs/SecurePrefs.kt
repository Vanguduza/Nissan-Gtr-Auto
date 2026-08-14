package co.zw.nissangtr.catalogapk.data.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Encrypted storage for Supabase anon keys + last auth project.
 * Falls back to regular SharedPreferences if crypto init fails (emulator edge cases).
 */
class SecurePrefs(context: Context) {
    private val prefs: SharedPreferences = runCatching {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }.getOrElse {
        context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
    }

    fun putAnonKey(projectId: String, anonKey: String) {
        prefs.edit().putString(key(projectId), anonKey).apply()
    }

    fun getAnonKey(projectId: String): String? = prefs.getString(key(projectId), null)

    fun putSessionProject(projectId: String) {
        prefs.edit().putString(SESSION_PROJECT, projectId).apply()
    }

    fun getSessionProject(): String? = prefs.getString(SESSION_PROJECT, null)

    private fun key(projectId: String) = "anon_$projectId"

    companion object {
        private const val FILE_NAME = "catalog_secure_prefs"
        private const val SESSION_PROJECT = "session_project_id"
    }
}
