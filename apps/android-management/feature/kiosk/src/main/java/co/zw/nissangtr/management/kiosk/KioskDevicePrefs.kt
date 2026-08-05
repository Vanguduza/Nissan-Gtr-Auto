package co.zw.nissangtr.management.kiosk

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.kioskDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "gtr_kiosk_device_prefs",
)

/**
 * Per-device kiosk preferences (DataStore).
 * Engine audio defaults OFF; idle defaults to 3 minutes (Device Admin override 1–15).
 */
class KioskDevicePrefs(private val context: Context) {
    val engineAudioEnabled: Flow<Boolean> = context.kioskDataStore.data.map { prefs ->
        prefs[KEY_ENGINE_AUDIO] ?: DEFAULT_ENGINE_AUDIO_ENABLED
    }

    val idleMinutes: Flow<Int> = context.kioskDataStore.data.map { prefs ->
        clampIdleMinutes(prefs[KEY_IDLE_MINUTES] ?: DEFAULT_IDLE_MINUTES)
    }

    suspend fun setEngineAudioEnabled(enabled: Boolean) {
        context.kioskDataStore.edit { it[KEY_ENGINE_AUDIO] = enabled }
    }

    suspend fun setIdleMinutes(minutes: Int) {
        context.kioskDataStore.edit { it[KEY_IDLE_MINUTES] = clampIdleMinutes(minutes) }
    }

    companion object {
        const val DEFAULT_ENGINE_AUDIO_ENABLED: Boolean = false
        const val DEFAULT_IDLE_MINUTES: Int = 3
        const val MIN_IDLE_MINUTES: Int = 1
        const val MAX_IDLE_MINUTES: Int = 15

        private val KEY_ENGINE_AUDIO = booleanPreferencesKey("engine_audio_enabled")
        private val KEY_IDLE_MINUTES = intPreferencesKey("idle_minutes")

        fun clampIdleMinutes(minutes: Int): Int =
            minutes.coerceIn(MIN_IDLE_MINUTES, MAX_IDLE_MINUTES)
    }
}
