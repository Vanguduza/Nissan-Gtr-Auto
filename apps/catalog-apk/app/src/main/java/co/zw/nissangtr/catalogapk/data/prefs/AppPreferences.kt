package co.zw.nissangtr.catalogapk.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import co.zw.nissangtr.catalogapk.BuildConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "catalog_apk_prefs")

class AppPreferences(private val context: Context) {
    private val profilesSeededKey = booleanPreferencesKey("profiles_seeded")
    private val profilesSeedVersionKey = intPreferencesKey("profiles_seed_version")
    private val maxConcurrentJobsKey = intPreferencesKey("max_concurrent_jobs")
    private val requireChargingKey = booleanPreferencesKey("require_charging")
    private val requireWifiKey = booleanPreferencesKey("require_unmetered_wifi")
    private val debugBypassGatesKey = booleanPreferencesKey("debug_bypass_gates")
    private val staleHeartbeatMsKey = longPreferencesKey("stale_heartbeat_ms")
    private val maxPagesDebugKey = intPreferencesKey("max_pages_debug")
    private val sidecarAgentUrlKey = stringPreferencesKey("sidecar_agent_url")
    private val sidecarAgentTokenKey = stringPreferencesKey("sidecar_agent_token")
    private val sidecarExtraHostsKey = stringPreferencesKey("sidecar_extra_hosts")

    val profilesSeeded: Flow<Boolean> = context.dataStore.data.map { it[profilesSeededKey] ?: false }
    val profilesSeedVersion: Flow<Int> = context.dataStore.data.map { it[profilesSeedVersionKey] ?: 0 }
    val maxConcurrentJobs: Flow<Int> = context.dataStore.data.map { it[maxConcurrentJobsKey] ?: 1 }
    val requireCharging: Flow<Boolean> = context.dataStore.data.map { it[requireChargingKey] ?: false }
    val requireUnmeteredWifi: Flow<Boolean> = context.dataStore.data.map { it[requireWifiKey] ?: false }
    val debugBypassGates: Flow<Boolean> = context.dataStore.data.map {
        it[debugBypassGatesKey] ?: BuildConfig.DEBUG
    }
    val staleHeartbeatMs: Flow<Long> = context.dataStore.data.map { it[staleHeartbeatMsKey] ?: 180_000L }
    /** 0 = unlimited; debug smoke bound when > 0 */
    val maxPagesDebug: Flow<Int> = context.dataStore.data.map { it[maxPagesDebugKey] ?: 0 }
    /** Host control agent, e.g. http://10.0.2.2:8192 or http://127.0.0.1:8192 after adb reverse */
    val sidecarAgentUrl: Flow<String> = context.dataStore.data.map {
        it[sidecarAgentUrlKey] ?: "http://127.0.0.1:8192"
    }
    val sidecarAgentToken: Flow<String> = context.dataStore.data.map {
        it[sidecarAgentTokenKey] ?: "catalog-apk-dev"
    }
    /** Extra FlareSolverr API bases (comma-separated), e.g. http://192.168.1.10:8191/v1 */
    val sidecarExtraHosts: Flow<String> = context.dataStore.data.map {
        it[sidecarExtraHostsKey] ?: ""
    }

    suspend fun setProfilesSeeded(seeded: Boolean) {
        context.dataStore.edit { it[profilesSeededKey] = seeded }
    }

    suspend fun setProfilesSeedVersion(version: Int) {
        context.dataStore.edit { it[profilesSeedVersionKey] = version }
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

    suspend fun setSidecarAgentUrl(value: String) {
        context.dataStore.edit { it[sidecarAgentUrlKey] = value.trim() }
    }

    suspend fun setSidecarAgentToken(value: String) {
        context.dataStore.edit { it[sidecarAgentTokenKey] = value.trim().ifBlank { "catalog-apk-dev" } }
    }

    suspend fun setSidecarExtraHosts(value: String) {
        context.dataStore.edit { it[sidecarExtraHostsKey] = value.trim() }
    }
}
