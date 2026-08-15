package co.zw.nissangtr.catalogapk.data.profile

import android.content.Context
import co.zw.nissangtr.catalogapk.data.db.SiteProfileDao
import co.zw.nissangtr.catalogapk.data.prefs.AppPreferences
import kotlinx.coroutines.flow.first

class ProfileSeeder(
    private val context: Context,
    private val siteProfileDao: SiteProfileDao,
    private val appPreferences: AppPreferences,
) {
    suspend fun seedIfNeeded() {
        val current = appPreferences.profilesSeedVersion.first()
        if (current >= PRESET_VERSION) return
        val presets = PRESET_FILES.mapNotNull { fileName ->
            runCatching {
                val text = context.assets.open("site_profiles/$fileName").bufferedReader().readText()
                ProfileJsonCodec.json.decodeFromString(SiteProfileJson.serializer(), text)
            }.getOrNull()
        }
        // Refresh presets only — keep user custom targets.
        siteProfileDao.upsertAll(presets.map { it.toEntity(isPreset = true) })
        appPreferences.setProfilesSeeded(true)
        appPreferences.setProfilesSeedVersion(PRESET_VERSION)
    }

    companion object {
        /** Bump when shipped preset path templates change (e.g. Megazip /parts → catalog hub). */
        const val PRESET_VERSION = 5

        val PRESET_FILES = listOf(
            "megazip.json",
            "partsouq.json",
            "7zap.json",
            "catcar.json",
            "japancats.json",
            "japan_parts.json",
        )
    }
}
