package co.zw.nissangtr.management.pos.offline

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.SecureRandom

/**
 * Keystore-wrapped AES passphrase for the SQLCipher POS database.
 * Never stores manager approval tokens — passphrase only.
 */
object OfflinePosPassphrase {
    private const val PREFS = "gtr_pos_offline_cipher"
    private const val KEY = "db_passphrase_b64"

    fun getOrCreate(context: Context): ByteArray {
        val masterKey = MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        val prefs = EncryptedSharedPreferences.create(
            context.applicationContext,
            PREFS,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
        val existing = prefs.getString(KEY, null)
        if (!existing.isNullOrBlank()) {
            return android.util.Base64.decode(existing, android.util.Base64.NO_WRAP)
        }
        val bytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
        prefs.edit()
            .putString(KEY, android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP))
            .apply()
        return bytes
    }
}
