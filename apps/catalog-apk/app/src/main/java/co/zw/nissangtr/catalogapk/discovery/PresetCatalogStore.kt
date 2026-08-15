package co.zw.nissangtr.catalogapk.discovery

import android.content.Context
import co.zw.nissangtr.catalogapk.data.profile.ProfileJsonCodec
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PresetCatalogJson(
    val id: String,
    val makers: List<PresetMakerJson> = emptyList(),
)

@Serializable
data class PresetMakerJson(
    val name: String,
    val slug: String,
    @SerialName("source_url") val sourceUrl: String = "",
    val models: List<PresetModelJson> = emptyList(),
)

@Serializable
data class PresetModelJson(
    @SerialName("display_name") val displayName: String,
    val slug: String,
    @SerialName("source_url") val sourceUrl: String = "",
    val chassis: List<PresetChassisJson> = emptyList(),
)

@Serializable
data class PresetChassisJson(
    val code: String,
    @SerialName("variant_slug") val variantSlug: String = "",
    val frame: String = "",
    @SerialName("year_label") val yearLabel: String = "",
    @SerialName("engine_code") val engineCode: String = "",
    @SerialName("source_url") val sourceUrl: String = "",
)

/**
 * Shipped maker/model/chassis trees for preset scrape targets.
 * Loaded from assets/site_catalogs/{profileId}.json (fallback: {engine}.json).
 */
class PresetCatalogStore(private val context: Context) {
    private val cache = mutableMapOf<String, PresetCatalogJson?>()

    fun load(profileId: String, engine: String): PresetCatalogJson? {
        val keys = listOf(profileId, engine, engine.replace("_", "-")).distinct()
        for (key in keys) {
            if (cache.containsKey(key)) {
                cache[key]?.let { return it }
            }
        }
        for (key in keys) {
            val parsed = runCatching {
                context.assets.open("site_catalogs/$key.json").bufferedReader().use { it.readText() }
                    .let { ProfileJsonCodec.json.decodeFromString(PresetCatalogJson.serializer(), it) }
            }.getOrNull()
            cache[key] = parsed
            if (parsed != null && parsed.makers.isNotEmpty()) return parsed
        }
        return null
    }

    fun makers(profileId: String, engine: String): List<DiscoveredMaker> {
        val cat = load(profileId, engine) ?: return emptyList()
        return cat.makers.map {
            DiscoveredMaker(name = it.name, slug = it.slug, sourceUrl = it.sourceUrl)
        }
    }

    fun models(profileId: String, engine: String, makerSlug: String): List<DiscoveredModel> {
        val maker = findMaker(profileId, engine, makerSlug) ?: return emptyList()
        return maker.models.map {
            DiscoveredModel(displayName = it.displayName, slug = it.slug, sourceUrl = it.sourceUrl)
        }
    }

    fun chassis(profileId: String, engine: String, makerSlug: String, modelSlug: String): List<DiscoveredChassis> {
        val maker = findMaker(profileId, engine, makerSlug) ?: return emptyList()
        val model = maker.models.firstOrNull { it.slug.equals(modelSlug, ignoreCase = true) }
            ?: return emptyList()
        val byCode = linkedMapOf<String, DiscoveredChassis>()
        for (row in model.chassis) {
            val code = row.code.ifBlank { row.variantSlug }.ifBlank { model.slug }
            byCode.putIfAbsent(
                code,
                DiscoveredChassis(
                    code = code,
                    variantSlug = row.variantSlug.ifBlank { model.slug },
                    frame = row.frame.ifBlank { row.code },
                    yearLabel = row.yearLabel,
                    engineCode = row.engineCode,
                    sourceUrl = row.sourceUrl.ifBlank { model.sourceUrl },
                ),
            )
        }
        return byCode.values.toList()
    }

    private fun findMaker(profileId: String, engine: String, makerSlug: String): PresetMakerJson? {
        val cat = load(profileId, engine) ?: return null
        return cat.makers.firstOrNull { it.slug.equals(makerSlug, ignoreCase = true) }
            ?: cat.makers.firstOrNull { it.name.equals(makerSlug, ignoreCase = true) }
    }
}
