package co.zw.nissangtr.catalogapk.data.profile

import co.zw.nissangtr.catalogapk.data.db.SiteProfileDao
import co.zw.nissangtr.catalogapk.data.model.SiteProfileEntity
import kotlinx.coroutines.flow.Flow

class ProfileRepository(private val dao: SiteProfileDao) {
    fun observeProfiles(): Flow<List<SiteProfileEntity>> = dao.observeAll()

    suspend fun getProfile(id: String): SiteProfileEntity? = dao.getById(id)

    suspend fun saveProfile(entity: SiteProfileEntity) {
        dao.upsert(entity)
    }

    suspend fun deleteCustomProfile(id: String) {
        dao.deleteCustom(id)
    }

    suspend fun createCustomProfile(
        displayName: String,
        engine: String,
        baseUrl: String,
        makerHub: String,
        partsHub: String,
        cloudflareMode: String,
        flaresolverrUrl: String,
    ): SiteProfileEntity {
        val id = "custom-" + java.util.UUID.randomUUID().toString().take(8)
        val paths = ProfilePathsJson(
            partsHub = partsHub.ifBlank { "/parts" },
            makerHub = makerHub.ifBlank { "/parts/{maker_slug}" },
            catalogPrefix = "/zapchasti-dlya-avtomobilej",
            model = "/zapchasti-dlya-avtomobilej/{maker_slug}/{model_slug}",
            variant = "/zapchasti-dlya-avtomobilej/{maker_slug}/{model_slug}/{variant_slug}",
            section = "/zapchasti-dlya-avtomobilej/{maker_slug}/{model_slug}/{variant_slug}/{section_slug}",
        )
        val entity = SiteProfileEntity(
            id = id,
            displayName = displayName.trim().ifBlank { "Custom target" },
            engine = engine.ifBlank { "custom" },
            baseUrl = baseUrl.trim().trimEnd('/'),
            pathsJson = encodePaths(paths),
            cloudflareMode = cloudflareMode.ifBlank { "auto" },
            flaresolverrUrl = flaresolverrUrl.ifBlank { "http://127.0.0.1:8191/v1" },
            isPreset = false,
        )
        dao.upsert(entity)
        return entity
    }

    fun decodePaths(entity: SiteProfileEntity): ProfilePathsJson =
        ProfileJsonCodec.json.decodeFromString(ProfilePathsJson.serializer(), entity.pathsJson)

    fun encodePaths(paths: ProfilePathsJson): String =
        ProfileJsonCodec.json.encodeToString(ProfilePathsJson.serializer(), paths)

    fun resolveMakerHub(entity: SiteProfileEntity, maker: String): String {
        val paths = decodePaths(entity)
        val slug = paths.makerSlugMap[maker] ?: maker.lowercase()
        return entity.baseUrl.trimEnd('/') +
            paths.makerHub.replace("{maker_slug}", slug)
    }
}
