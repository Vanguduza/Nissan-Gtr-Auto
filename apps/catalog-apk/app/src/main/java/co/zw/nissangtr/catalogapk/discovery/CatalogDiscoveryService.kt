package co.zw.nissangtr.catalogapk.discovery

import android.content.Context
import co.zw.nissangtr.catalogapk.data.model.SiteProfileEntity
import co.zw.nissangtr.catalogapk.data.profile.ProfilePathsJson
import co.zw.nissangtr.catalogapk.data.profile.ProfileRepository

class CatalogDiscoveryService(
    private val profileRepository: ProfileRepository,
    private val flareLifecycle: FlareSolverrLifecycle? = null,
    context: Context? = null,
) {
    private val presets = context?.let { PresetCatalogStore(it) }

    private fun presetMakers(profile: SiteProfileEntity): List<DiscoveredMaker> =
        presets?.makers(profile.id, profile.engine).orEmpty()

    private fun presetModels(profile: SiteProfileEntity, maker: DiscoveredMaker): List<DiscoveredModel> =
        presets?.models(profile.id, profile.engine, maker.slug).orEmpty()

    private fun presetChassis(
        profile: SiteProfileEntity,
        maker: DiscoveredMaker,
        model: DiscoveredModel,
    ): List<DiscoveredChassis> =
        presets?.chassis(profile.id, profile.engine, maker.slug, model.slug).orEmpty()

    private fun httpClient(profile: SiteProfileEntity): SiteHttpClient =
        SiteHttpClient(
            cloudflareMode = profile.cloudflareMode.ifBlank { "auto" },
            flaresolverrUrl = profile.flaresolverrUrl.ifBlank { FlareSolverrLifecycle.DEFAULT_FLARE_API },
            lifecycle = flareLifecycle,
        )

    suspend fun discoverMakers(profile: SiteProfileEntity): Result<List<DiscoveredMaker>> {
        val shipped = presetMakers(profile)
        if (shipped.isNotEmpty()) return Result.success(shipped)
        val client = httpClient(profile)
        val paths = profileRepository.decodePaths(profile)
        val candidates = makerHubCandidates(profile.baseUrl, paths, profile.engine)
        var lastError: String? = null
        for (hub in candidates) {
            val fetch = client.fetch(hub)
            if (!fetch.ok) {
                lastError = "${fetch.error ?: "HTTP ${fetch.statusCode}"} @ $hub"
                continue
            }
            val makers = parseMakers(profile.engine, fetch.html, profile.baseUrl)
            if (makers.isNotEmpty()) {
                return Result.success(makers)
            }
            lastError = "No makers parsed from $hub (CF=${fetch.viaFlareSolverr})"
        }
        return Result.failure(IllegalStateException(lastError ?: "Maker discovery failed"))
    }

    suspend fun discoverModels(profile: SiteProfileEntity, maker: DiscoveredMaker): Result<List<DiscoveredModel>> {
        val shipped = presetModels(profile, maker)
        if (shipped.isNotEmpty()) return Result.success(shipped)
        val client = httpClient(profile)
        val paths = profileRepository.decodePaths(profile)
        val url = if (maker.sourceUrl.startsWith("http")) {
            maker.sourceUrl
        } else {
            profile.baseUrl.trimEnd('/') +
                paths.makerHub.replace("{maker_slug}", maker.slug).ifBlank { "/parts/${maker.slug}" }
        }
        val fetch = client.fetch(url)
        if (!fetch.ok) {
            return Result.failure(
                IllegalStateException(fetch.error ?: "Failed to fetch maker hub ($url)"),
            )
        }
        val models = parseModels(profile.engine, fetch.html, profile.baseUrl, maker.slug)
        if (models.isEmpty()) {
            return Result.failure(IllegalStateException("No models parsed for ${maker.name}"))
        }
        return Result.success(models)
    }

    suspend fun discoverChassis(
        profile: SiteProfileEntity,
        maker: DiscoveredMaker,
        model: DiscoveredModel,
    ): Result<List<DiscoveredChassis>> {
        val shipped = presetChassis(profile, maker, model)
        if (shipped.isNotEmpty()) return Result.success(shipped)
        val client = httpClient(profile)
        val paths = profileRepository.decodePaths(profile)
        val url = if (model.sourceUrl.startsWith("http")) {
            model.sourceUrl
        } else {
            val template = paths.model.ifBlank {
                "/zapchasti-dlya-avtomobilej/{maker_slug}/{model_slug}"
            }
            profile.baseUrl.trimEnd('/') + template
                .replace("{maker_slug}", maker.slug)
                .replace("{model_slug}", model.slug)
        }
        val fetch = client.fetch(url)
        if (!fetch.ok) {
            return Result.failure(
                IllegalStateException(fetch.error ?: "Failed to fetch model page ($url)"),
            )
        }
        val chassis = parseChassis(profile.engine, fetch.html, profile.baseUrl)
        if (chassis.isEmpty()) {
            return Result.failure(IllegalStateException("No chassis/variants parsed for ${model.displayName}"))
        }
        return Result.success(chassis)
    }

    companion object {
        fun parseMakers(engine: String, html: String, baseUrl: String): List<DiscoveredMaker> =
            when (engine.lowercase()) {
                "partsouq" -> PartSouqHtmlDiscovery.parseMakers(html, baseUrl)
                "7zap" -> SevenZapHtmlDiscovery.parseMakers(html, baseUrl)
                "catcar" -> CatcarHtmlDiscovery.parseMakers(html, baseUrl)
                "japancats" -> JapancatsHtmlDiscovery.parseMakers(html, baseUrl)
                "japan_parts", "japan-parts" -> JapanPartsHtmlDiscovery.parseMakers(html, baseUrl)
                else -> MegazipHtmlDiscovery.parseMakers(html, baseUrl)
            }

        fun parseModels(engine: String, html: String, baseUrl: String, makerSlug: String): List<DiscoveredModel> =
            when (engine.lowercase()) {
                "partsouq" -> PartSouqHtmlDiscovery.parseModels(html, baseUrl, makerSlug)
                "7zap" -> SevenZapHtmlDiscovery.parseModels(html, baseUrl, makerSlug)
                "catcar" -> CatcarHtmlDiscovery.parseModels(html, baseUrl, makerSlug)
                "japancats" -> JapancatsHtmlDiscovery.parseModels(html, baseUrl, makerSlug)
                "japan_parts", "japan-parts" -> JapanPartsHtmlDiscovery.parseModels(html, baseUrl, makerSlug)
                else -> MegazipHtmlDiscovery.parseModels(html, baseUrl, makerSlug)
            }

        fun parseChassis(engine: String, html: String, baseUrl: String): List<DiscoveredChassis> =
            when (engine.lowercase()) {
                "7zap" -> SevenZapHtmlDiscovery.parseChassis(html, baseUrl)
                "catcar" -> CatcarHtmlDiscovery.parseChassis(html, baseUrl)
                "japancats" -> JapancatsHtmlDiscovery.parseChassis(html, baseUrl)
                "japan_parts", "japan-parts" -> JapanPartsHtmlDiscovery.parseChassis(html, baseUrl)
                else -> MegazipHtmlDiscovery.parseChassis(html, baseUrl)
            }

        /**
         * Hub candidates per engine. Megazip retired bare `/parts` (404); maker indexes live on
         * homepage and `/zapchasti-dlya-avtomobilej`; per-maker hubs remain `/parts/{slug}`.
         */
        fun makerHubCandidates(
            baseUrl: String,
            paths: ProfilePathsJson,
            engine: String = "megazip",
        ): List<String> {
            val root = baseUrl.trimEnd('/')
            val primary = paths.partsHub.trim().ifBlank {
                when (engine.lowercase()) {
                    "7zap" -> "/en/catalog/cars/"
                    "catcar", "japancats", "japan_parts", "japan-parts" -> "/"
                    "partsouq" -> "/en/catalog"
                    else -> "/zapchasti-dlya-avtomobilej"
                }
            }
            val catalog = paths.catalogPrefix.trim()
            val extras = when (engine.lowercase()) {
                "7zap" -> listOf("/en/catalog/cars/", "/")
                "catcar" -> listOf("/", "/en/")
                "japancats" -> listOf("/")
                "japan_parts", "japan-parts" -> listOf("/", "/toyota/")
                "partsouq" -> listOf("/en/catalog", "/")
                else -> listOf("/", "/zapchasti-dlya-avtomobilej", "/parts")
            }
            return (listOf(primary, catalog) + extras)
                .filter { it.isNotBlank() }
                .map { path ->
                    if (path == "/" || path.isBlank()) {
                        "$root/"
                    } else {
                        root + (if (path.startsWith("/")) path else "/$path")
                    }
                }
                .distinct()
        }
    }
}

/** Best-effort PartSouq locate-page maker links; refine as HTML samples land. */
object PartSouqHtmlDiscovery {
    private val makerHref = Regex(
        """href="([^"]*(?:/maker/|/parts/|/catalog/)([A-Za-z0-9%+-]+)[^"]*)"[^>]*>([^<]{2,48})<""",
        RegexOption.IGNORE_CASE,
    )

    fun parseMakers(html: String, baseUrl: String): List<DiscoveredMaker> {
        val by = linkedMapOf<String, DiscoveredMaker>()
        for (m in makerHref.findAll(html)) {
            val slug = java.net.URLDecoder.decode(m.groupValues[2], "UTF-8")
                .lowercase()
                .replace(' ', '-')
            if (slug.length < 2) continue
            val name = m.groupValues[3].trim()
            val href = m.groupValues[1]
            val url = if (href.startsWith("http")) href else baseUrl.trimEnd('/') + href
            by.putIfAbsent(slug, DiscoveredMaker(name = name.ifBlank { slug }, slug = slug, sourceUrl = url))
        }
        return by.values.sortedBy { it.name.lowercase() }
    }

    fun parseModels(html: String, baseUrl: String, makerSlug: String): List<DiscoveredModel> {
        return MegazipHtmlDiscovery.parseModels(html, baseUrl, makerSlug)
    }
}
