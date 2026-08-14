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
        if (appPreferences.profilesSeeded.first()) return
        val presets = listOf("megazip.json", "partsouq.json").mapNotNull { fileName ->
            runCatching {
                val text = context.assets.open("site_profiles/$fileName").bufferedReader().readText()
                ProfileJsonCodec.json.decodeFromString(SiteProfileJson.serializer(), text)
            }.getOrNull()
        }
        siteProfileDao.upsertAll(presets.map { it.toEntity(isPreset = true) })
        appPreferences.setProfilesSeeded(true)
    }
}
