package co.zw.nissangtr.catalogapk.discovery

import co.zw.nissangtr.catalogapk.data.model.SiteProfileEntity
import co.zw.nissangtr.catalogapk.data.profile.ProfileRepository

class CatalogDiscoveryService(
    private val profileRepository: ProfileRepository,
) {
    suspend fun discoverMakers(profile: SiteProfileEntity): Result<List<DiscoveredMaker>> {
        val client = SiteHttpClient(profile.cloudflareMode, profile.flaresolverrUrl)
        val paths = profileRepository.decodePaths(profile)
        val hub = profile.baseUrl.trimEnd('/') + paths.partsHub.ifBlank { "/parts" }
        val fetch = client.fetch(hub)
        if (!fetch.ok) return Result.failure(IllegalStateException(fetch.error ?: "Failed to fetch makers hub"))
        val makers = when (profile.engine.lowercase()) {
            "megazip", "custom" -> MegazipHtmlDiscovery.parseMakers(fetch.html, profile.baseUrl)
            "partsouq" -> PartSouqHtmlDiscovery.parseMakers(fetch.html, profile.baseUrl)
            else -> MegazipHtmlDiscovery.parseMakers(fetch.html, profile.baseUrl)
        }
        if (makers.isEmpty()) {
            return Result.failure(IllegalStateException("No makers parsed from $hub (CF=${fetch.viaFlareSolverr})"))
        }
        return Result.success(makers)
    }

    suspend fun discoverModels(profile: SiteProfileEntity, maker: DiscoveredMaker): Result<List<DiscoveredModel>> {
        val client = SiteHttpClient(profile.cloudflareMode, profile.flaresolverrUrl)
        val paths = profileRepository.decodePaths(profile)
        val url = profile.baseUrl.trimEnd('/') +
            paths.makerHub.replace("{maker_slug}", maker.slug).ifBlank { "/parts/${maker.slug}" }
        val fetch = client.fetch(url)
        if (!fetch.ok) return Result.failure(IllegalStateException(fetch.error ?: "Failed to fetch maker hub"))
        val models = when (profile.engine.lowercase()) {
            "partsouq" -> PartSouqHtmlDiscovery.parseModels(fetch.html, profile.baseUrl, maker.slug)
            else -> MegazipHtmlDiscovery.parseModels(fetch.html, profile.baseUrl, maker.slug)
        }
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
        val client = SiteHttpClient(profile.cloudflareMode, profile.flaresolverrUrl)
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
        if (!fetch.ok) return Result.failure(IllegalStateException(fetch.error ?: "Failed to fetch model page"))
        val chassis = MegazipHtmlDiscovery.parseChassis(fetch.html, profile.baseUrl)
        if (chassis.isEmpty()) {
            return Result.failure(IllegalStateException("No chassis/variants parsed for ${model.displayName}"))
        }
        return Result.success(chassis)
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
        // Reuse megazip-ish link harvest filtered by maker slug token.
        return MegazipHtmlDiscovery.parseModels(html, baseUrl, makerSlug)
    }
}
