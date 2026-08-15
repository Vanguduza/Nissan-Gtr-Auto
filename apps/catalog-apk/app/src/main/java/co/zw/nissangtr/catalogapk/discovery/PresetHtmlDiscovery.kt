package co.zw.nissangtr.catalogapk.discovery

/**
 * HTML discovery parsers for shipped non-Megazip / non-PartSouq presets.
 * Patterns verified against live pages (2026-08-14).
 */
object SevenZapHtmlDiscovery {
    private val jsonLdBrand = Regex(
        """"url"\s*:\s*"(https://([a-z0-9-]+)\.7zap\.com/en/[^"]+)"\s*,\s*"name"\s*:\s*"([^"]+)"""",
        RegexOption.IGNORE_CASE,
    )
    private val pathBrand = Regex(
        """href="((?:https://7zap\.com)?/en/catalog/cars/([a-z0-9-]+)(?:/[a-z0-9-]+)?/?)"""",
        RegexOption.IGNORE_CASE,
    )
    private val modelCatalog = Regex(
        """href="((?:https://7zap\.com)?/en/catalog/cars/([a-z0-9-]+)/([a-z0-9-]+)/([a-z0-9%-]+)-parts-catalog/?)"""",
        RegexOption.IGNORE_CASE,
    )
    private val chassisFromSlug = Regex("""([a-z]{1,4}\d{1,3}[a-z]?)(?:-facelift)?$""", RegexOption.IGNORE_CASE)

    fun parseMakers(html: String, baseUrl: String): List<DiscoveredMaker> {
        val by = linkedMapOf<String, DiscoveredMaker>()
        for (m in jsonLdBrand.findAll(html)) {
            val slug = m.groupValues[2].lowercase()
            if (slug in SKIP) continue
            val name = clean(m.groupValues[3].removeSuffix(" OEM Parts Catalog"))
            val pathUrl = join(baseUrl, "/en/catalog/cars/$slug/europe/")
            by.putIfAbsent(slug, DiscoveredMaker(name = name.ifBlank { slug }, slug = slug, sourceUrl = pathUrl))
        }
        for (m in pathBrand.findAll(html)) {
            val slug = m.groupValues[2].lowercase()
            if (slug in SKIP) continue
            val href = m.groupValues[1]
            val url = if (href.startsWith("http")) href else join(baseUrl, href)
            by.putIfAbsent(
                slug,
                DiscoveredMaker(
                    name = slug.replace('-', ' ').replaceFirstChar { it.uppercase() },
                    slug = slug,
                    sourceUrl = url,
                ),
            )
        }
        return by.values.sortedBy { it.name.lowercase() }
    }

    fun parseModels(html: String, baseUrl: String, makerSlug: String): List<DiscoveredModel> {
        val by = linkedMapOf<String, DiscoveredModel>()
        for (m in modelCatalog.findAll(html)) {
            if (!m.groupValues[2].equals(makerSlug, ignoreCase = true)) continue
            val href = m.groupValues[1]
            val slug = m.groupValues[4].lowercase()
            val url = if (href.startsWith("http")) href else join(baseUrl, href)
            val label = slug.replace('-', ' ')
            by.putIfAbsent(slug, DiscoveredModel(displayName = label, slug = slug, sourceUrl = url))
        }
        return by.values.sortedBy { it.displayName.lowercase() }
    }

    fun parseChassis(html: String, baseUrl: String): List<DiscoveredChassis> {
        val out = linkedMapOf<String, DiscoveredChassis>()
        for (m in modelCatalog.findAll(html)) {
            val href = m.groupValues[1]
            val slug = m.groupValues[4].lowercase()
            val code = chassisFromSlug.find(slug)?.groupValues?.get(1)?.uppercase().orEmpty()
                .ifBlank { slug.take(12).uppercase() }
            val url = if (href.startsWith("http")) href else join(baseUrl, href)
            out.putIfAbsent(
                code,
                DiscoveredChassis(
                    code = code,
                    variantSlug = slug,
                    frame = slug,
                    yearLabel = "",
                    engineCode = "",
                    sourceUrl = url,
                ),
            )
        }
        return out.values.sortedBy { it.code }
    }

    private val SKIP = setOf("cars", "catalog", "en", "parts")
}

object CatcarHtmlDiscovery {
    private val makerPath = Regex(
        """href="(/([a-z0-9_]+)(?:/\?[^"]*)?|/([a-z0-9_]+)/?)"[^>]*>""",
        RegexOption.IGNORE_CASE,
    )
    private val makerList = Regex(
        """href="(https?://(?:www\.)?catcar\.info)?/([a-z0-9_]+)/?(?:\?[^"]*)?"""",
        RegexOption.IGNORE_CASE,
    )
    private val marketOrModel = Regex(
        """href="((?:https?://(?:www\.)?catcar\.info)?/[a-z0-9_]+/\?[^"]*?l=([^"'&]+))"""",
        RegexOption.IGNORE_CASE,
    )
    private val chassisInLabel = Regex("""\(([A-Z0-9]{2,12})\)""")

    private val KNOWN_MAKERS = setOf(
        "nissan", "toyota", "honda", "mazda", "subaru", "mitsubishi", "suzuki",
        "mercedes", "bmw", "renault", "opel", "ford", "volvo", "peugeot", "citroen",
        "jaguar", "lexus", "infiniti", "audi", "vw", "audivw", "ssangyong",
    )

    fun parseMakers(html: String, baseUrl: String): List<DiscoveredMaker> {
        val by = linkedMapOf<String, DiscoveredMaker>()
        for (m in makerList.findAll(html)) {
            val slug = m.groupValues[2].lowercase()
            if (slug in SKIP || slug !in KNOWN_MAKERS) continue
            by.putIfAbsent(
                slug,
                DiscoveredMaker(
                    name = slug.replace('_', ' ').replaceFirstChar { it.uppercase() },
                    slug = slug,
                    sourceUrl = join(baseUrl, "/$slug/?lang=en"),
                ),
            )
        }
        if (by.size < 3) {
            for (m in makerPath.findAll(html)) {
                val slug = (m.groupValues[2].ifBlank { m.groupValues[3] }).lowercase()
                if (slug in SKIP || slug !in KNOWN_MAKERS) continue
                by.putIfAbsent(
                    slug,
                    DiscoveredMaker(
                        name = slug.replaceFirstChar { it.uppercase() },
                        slug = slug,
                        sourceUrl = join(baseUrl, "/$slug/?lang=en"),
                    ),
                )
            }
        }
        return by.values.sortedBy { it.name.lowercase() }
    }

    fun parseModels(html: String, baseUrl: String, makerSlug: String): List<DiscoveredModel> {
        val by = linkedMapOf<String, DiscoveredModel>()
        for (m in marketOrModel.findAll(html)) {
            val href = m.groupValues[1]
            if (!href.contains("/$makerSlug/", ignoreCase = true)) continue
            val token = m.groupValues[2]
            val abs = when {
                href.startsWith("http") -> href
                href.startsWith("/") -> join(baseUrl, href)
                else -> join(baseUrl, "/$href")
            }
            val label = decodeMarketLabel(token) ?: "market-${token.take(8)}"
            val slug = label.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
            by.putIfAbsent(slug, DiscoveredModel(displayName = label, slug = slug, sourceUrl = abs))
        }
        return by.values.sortedBy { it.displayName.lowercase() }
    }

    fun parseChassis(html: String, baseUrl: String): List<DiscoveredChassis> {
        val out = linkedMapOf<String, DiscoveredChassis>()
        val row = Regex(
            """<a[^>]+href="([^"]*[?&]l=([^"'&]+)[^"]*)"[^>]*>([^<]{2,80})</a>""",
            RegexOption.IGNORE_CASE,
        )
        for (m in row.findAll(html)) {
            val href = m.groupValues[1]
            val label = clean(m.groupValues[3])
            val code = chassisInLabel.find(label)?.groupValues?.get(1)
                ?: Regex("""\b([A-Z]{1,3}\d{1,3}[A-Z]?)\b""").find(label.uppercase())?.groupValues?.get(1)
                ?: continue
            val url = if (href.startsWith("http")) href else join(baseUrl, href)
            out[code] = DiscoveredChassis(
                code = code,
                variantSlug = m.groupValues[2],
                frame = label,
                yearLabel = "",
                engineCode = "",
                sourceUrl = url,
            )
        }
        return out.values.sortedBy { it.code }
    }

    private fun decodeMarketLabel(token: String): String? {
        return runCatching {
            val raw = java.net.URLDecoder.decode(token, "UTF-8")
            val padded = raw + "=".repeat((4 - raw.length % 4) % 4)
            val decoded = String(java.util.Base64.getDecoder().decode(padded))
            Regex("""["']20["']\s*:\s*["']([^"']+)["']""").find(decoded)?.groupValues?.get(1)
        }.getOrNull()
    }

    private val SKIP = setOf(
        "css", "en", "ru", "setlang", "moto", "totalcatalog", "usa_oem", "usa_noem",
        "images", "js", "font", "fonts",
    )
}

object JapancatsHtmlDiscovery {
    private val makerHref = Regex(
        """href="(/([A-Za-z][A-Za-z0-9-]*)/)"""",
        RegexOption.IGNORE_CASE,
    )
    private val regionRadio = Regex(
        """<input[^>]+name=["']r["'][^>]+value=["']([A-Z]{1,4})["'][^>]*>\s*<label[^>]*>([^<]+)</label>""",
        RegexOption.IGNORE_CASE,
    )
    private val regionQuery = Regex(
        """[?&]Region=([A-Za-z0-9]+)""",
        RegexOption.IGNORE_CASE,
    )

    private val SKIP = setOf("css", "moto", "js", "images")

    fun parseMakers(html: String, baseUrl: String): List<DiscoveredMaker> {
        val by = linkedMapOf<String, DiscoveredMaker>()
        for (m in makerHref.findAll(html)) {
            val pathSlug = m.groupValues[2]
            if (pathSlug.lowercase() in SKIP) continue
            val slug = pathSlug
            by.putIfAbsent(
                slug.lowercase(),
                DiscoveredMaker(
                    name = slug.replaceFirstChar { it.uppercase() },
                    slug = slug,
                    sourceUrl = join(baseUrl, "/$slug/"),
                ),
            )
        }
        return by.values.sortedBy { it.name.lowercase() }
    }

    fun parseModels(html: String, baseUrl: String, makerSlug: String): List<DiscoveredModel> {
        val by = linkedMapOf<String, DiscoveredModel>()
        for (m in regionRadio.findAll(html)) {
            val code = m.groupValues[1]
            val label = clean(m.groupValues[2]).ifBlank { code }
            by[code] = DiscoveredModel(
                displayName = label,
                slug = code,
                sourceUrl = join(baseUrl, "/$makerSlug/?Region=$code"),
            )
        }
        for (m in regionQuery.findAll(html)) {
            val code = m.groupValues[1]
            by.putIfAbsent(
                code,
                DiscoveredModel(
                    displayName = code,
                    slug = code,
                    sourceUrl = join(baseUrl, "/$makerSlug/?Region=$code"),
                ),
            )
        }
        if (by.isEmpty()) {
            for (code in listOf("EL", "US", "CA", "GL", "JP")) {
                by[code] = DiscoveredModel(
                    displayName = code,
                    slug = code,
                    sourceUrl = join(baseUrl, "/$makerSlug/?Region=$code"),
                )
            }
        }
        return by.values.sortedBy { it.displayName.lowercase() }
    }

    fun parseChassis(html: String, baseUrl: String): List<DiscoveredChassis> {
        val out = linkedMapOf<String, DiscoveredChassis>()
        val modelLink = Regex(
            """href="([^"]*(?:Models\.aspx|Parts\.aspx)\?[^"]*Model=([a-f0-9-]{8,})[^"]*)"[^>]*>([^<]{2,80})<""",
            RegexOption.IGNORE_CASE,
        )
        for (m in modelLink.findAll(html)) {
            val href = m.groupValues[1]
            val modelId = m.groupValues[2]
            val label = clean(m.groupValues[3])
            val code = Regex("""\b([A-Z]{1,3}\d{1,3}[A-Z]?)\b""").find(label.uppercase())?.groupValues?.get(1)
                ?: modelId.take(8).uppercase()
            val url = if (href.startsWith("http")) href else join(baseUrl, href)
            out[code] = DiscoveredChassis(
                code = code,
                variantSlug = modelId,
                frame = label,
                yearLabel = "",
                engineCode = "",
                sourceUrl = url,
            )
        }
        return out.values.sortedBy { it.code }
    }
}

object JapanPartsHtmlDiscovery {
    private val makerHref = Regex(
        """href="(/(toyota|lexus)(?:/)?)"""",
        RegexOption.IGNORE_CASE,
    )
    private val yearHref = Regex(
        """href="(/(toyota|lexus)/([a-z]{2})/(\d{4})/?)"""",
        RegexOption.IGNORE_CASE,
    )
    private val modelHref = Regex(
        """href="(/(toyota|lexus)/([a-z]{2})/(\d{4})/([a-z0-9-]+)/?)"""",
        RegexOption.IGNORE_CASE,
    )
    private val chassisToken = Regex("""\b([a-z]{1,3}\d{1,3}[a-z0-9]*)\b""", RegexOption.IGNORE_CASE)

    fun parseMakers(html: String, baseUrl: String): List<DiscoveredMaker> {
        val by = linkedMapOf<String, DiscoveredMaker>()
        for (m in makerHref.findAll(html)) {
            val slug = m.groupValues[2].lowercase()
            by[slug] = DiscoveredMaker(
                name = slug.replaceFirstChar { it.uppercase() },
                slug = slug,
                sourceUrl = join(baseUrl, "/$slug"),
            )
        }
        if (by.isEmpty()) {
            by["toyota"] = DiscoveredMaker("Toyota", "toyota", join(baseUrl, "/toyota"))
            by["lexus"] = DiscoveredMaker("Lexus", "lexus", join(baseUrl, "/lexus"))
        }
        return by.values.sortedBy { it.name.lowercase() }
    }

    fun parseModels(html: String, baseUrl: String, makerSlug: String): List<DiscoveredModel> {
        val by = linkedMapOf<String, DiscoveredModel>()
        for (m in yearHref.findAll(html)) {
            if (!m.groupValues[2].equals(makerSlug, ignoreCase = true)) continue
            val year = m.groupValues[4]
            val region = m.groupValues[3]
            val href = m.groupValues[1]
            val url = if (href.startsWith("http")) href else join(baseUrl, href)
            val slug = if (region.equals("eu", true)) year else "$region-$year"
            by.putIfAbsent(slug, DiscoveredModel(displayName = "$region $year", slug = slug, sourceUrl = url))
        }
        for (m in modelHref.findAll(html)) {
            if (!m.groupValues[2].equals(makerSlug, ignoreCase = true)) continue
            val year = m.groupValues[4]
            val name = m.groupValues[5]
            val href = m.groupValues[1]
            val url = if (href.startsWith("http")) href else join(baseUrl, href)
            val slug = "$year/$name"
            by.putIfAbsent(slug, DiscoveredModel(displayName = "$name ($year)", slug = slug, sourceUrl = url))
        }
        return by.values.sortedByDescending { it.displayName }
    }

    fun parseChassis(html: String, baseUrl: String): List<DiscoveredChassis> {
        val out = linkedMapOf<String, DiscoveredChassis>()
        for (m in modelHref.findAll(html)) {
            val href = m.groupValues[1]
            val name = m.groupValues[5]
            val code = chassisToken.findAll(name).map { it.groupValues[1].uppercase() }.lastOrNull()
                ?: name.take(12).uppercase()
            val url = if (href.startsWith("http")) href else join(baseUrl, href)
            out[code] = DiscoveredChassis(
                code = code,
                variantSlug = name,
                frame = name,
                yearLabel = m.groupValues[4],
                engineCode = "",
                sourceUrl = url,
            )
        }
        return out.values.sortedBy { it.code }
    }
}

private fun clean(s: String): String =
    s.replace("&nbsp;", " ").replace(Regex("\\s+"), " ").trim()

private fun join(base: String, path: String): String {
    if (path.startsWith("http")) return path
    val b = base.trimEnd('/')
    return if (path.startsWith("/")) "$b$path" else "$b/$path"
}
