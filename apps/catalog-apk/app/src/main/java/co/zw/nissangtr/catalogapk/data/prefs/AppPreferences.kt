package co.zw.nissangtr.catalogapk.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "catalog_apk_prefs")

class AppPreferences(private val context: Context) {
    private val profilesSeededKey = booleanPreferencesKey("profiles_seeded")
    private val maxConcurrentJobsKey = intPreferencesKey("max_concurrent_jobs")
    private val requireChargingKey = booleanPreferencesKey("require_charging")
    private val requireWifiKey = booleanPreferencesKey("require_unmetered_wifi")
    private val debugBypassGatesKey = booleanPreferencesKey("debug_bypass_gates")
    private val staleHeartbeatMsKey = longPreferencesKey("stale_heartbeat_ms")
    private val maxPagesDebugKey = intPreferencesKey("max_pages_debug")

    val profilesSeeded: Flow<Boolean> = context.dataStore.data.map { it[profilesSeededKey] ?: false }
    val maxConcurrentJobs: Flow<Int> = context.dataStore.data.map { it[maxConcurrentJobsKey] ?: 1 }
    val requireCharging: Flow<Boolean> = context.dataStore.data.map { it[requireChargingKey] ?: true }
    val requireUnmeteredWifi: Flow<Boolean> = context.dataStore.data.map { it[requireWifiKey] ?: true }
    val debugBypassGates: Flow<Boolean> = context.dataStore.data.map { it[debugBypassGatesKey] ?: false }
    val staleHeartbeatMs: Flow<Long> = context.dataStore.data.map { it[staleHeartbeatMsKey] ?: 180_000L }
    /** 0 = unlimited; debug smoke bound when > 0 */
    val maxPagesDebug: Flow<Int> = context.dataStore.data.map { it[maxPagesDebugKey] ?: 0 }

    suspend fun setProfilesSeeded(seeded: Boolean) {
        context.dataStore.edit { it[profilesSeededKey] = seeded }
    }

    suspend fun setMaxConcurrentJobs(value: Int) {
        context.dataStore.edit { it[maxConcurrentJobsKey] = value.coerceIn(1, 2) }
    }

    suspend fun setRequireCharging(value: Boolean) {
        context.dataStore.edit { it[requireChargingKey] = value }
    }

    suspend fun setRequireUnmeteredWifi(value: Boolean) {
        context.dataStore.edit { it[requireWifiKey] = value }
    }

    suspend fun setDebugBypassGates(value: Boolean) {
        context.dataStore.edit { it[debugBypassGatesKey] = value }
    }

    suspend fun setStaleHeartbeatMs(value: Long) {
        context.dataStore.edit { it[staleHeartbeatMsKey] = value.coerceAtLeast(60_000L) }
    }

    suspend fun setMaxPagesDebug(value: Int) {
        context.dataStore.edit { it[maxPagesDebugKey] = value.coerceAtLeast(0) }
    }
}
