package co.zw.nissangtr.catalogapk.data.profile

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class SiteProfileJson(
    val id: String,
    @SerialName("display_name") val displayName: String,
    val engine: String,
    @SerialName("base_url") val baseUrl: String,
    val cloudflare: CloudflareJson = CloudflareJson(),
    val paths: ProfilePathsJson = ProfilePathsJson(),
    val selectors: SelectorPresetJson = SelectorPresetJson(),
    @SerialName("rate_limit_seconds") val rateLimitSeconds: Double = 0.35,
)

@Serializable
data class CloudflareJson(
    val mode: String = "auto",
    @SerialName("flaresolverr_url") val flaresolverrUrl: String = "http://127.0.0.1:8191/v1",
)

@Serializable
data class ProfilePathsJson(
    @SerialName("parts_hub") val partsHub: String = "/parts",
    @SerialName("maker_hub") val makerHub: String = "/parts/{maker_slug}",
    @SerialName("catalog_prefix") val catalogPrefix: String = "",
    val model: String = "",
    val variant: String = "",
    val section: String = "",
    @SerialName("maker_slug_map") val makerSlugMap: Map<String, String> = emptyMap(),
)

@Serializable
data class SelectorPresetJson(
    val preset: String = "megazip_v1",
)

object ProfileJsonCodec {
    val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }
}

fun SiteProfileJson.toEntity(isPreset: Boolean): co.zw.nissangtr.catalogapk.data.model.SiteProfileEntity =
    co.zw.nissangtr.catalogapk.data.model.SiteProfileEntity(
        id = id,
        displayName = displayName,
        engine = engine,
        baseUrl = baseUrl,
        pathsJson = ProfileJsonCodec.json.encodeToString(ProfilePathsJson.serializer(), paths),
        cloudflareMode = cloudflare.mode,
        flaresolverrUrl = cloudflare.flaresolverrUrl,
        isPreset = isPreset,
    )

fun co.zw.nissangtr.catalogapk.data.model.SiteProfileEntity.toJson(): SiteProfileJson {
    val paths = ProfileJsonCodec.json.decodeFromString(ProfilePathsJson.serializer(), pathsJson)
    return SiteProfileJson(
        id = id,
        displayName = displayName,
        engine = engine,
        baseUrl = baseUrl,
        cloudflare = CloudflareJson(mode = cloudflareMode, flaresolverrUrl = flaresolverrUrl),
        paths = paths,
    )
}
