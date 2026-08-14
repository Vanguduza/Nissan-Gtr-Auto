package co.zw.nissangtr.catalogapk.discovery

data class DiscoveredMaker(val name: String, val slug: String, val sourceUrl: String)
data class DiscoveredModel(val displayName: String, val slug: String, val sourceUrl: String)
data class DiscoveredChassis(
    val code: String,
    val variantSlug: String,
    val frame: String,
    val yearLabel: String,
    val engineCode: String,
    val sourceUrl: String,
)

/**
 * Port of Megazip hub/variant HTML patterns used by data_pipeline.megazip.parse_html.
 */
object MegazipHtmlDiscovery {
    private val makerLink = Regex(
        """href="(/parts/([a-z0-9-]+)|/zapchasti-dlya-avtomobilej/([a-z0-9-]+))(?:/?)(?:["?])""",
        RegexOption.IGNORE_CASE,
    )
    private val modelSilCard = Regex(
        """<a\s+([^>]*?\shref="(/(?:parts|zapchasti-dlya-avtomobilej)/[^"]+)"[^>]*)>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )
    private val variantItem = Regex(
        """<li[^>]*class="[^"]*s-catalog__body-variants-item[^"]*"[^>]*data-id="(\d+)"[^>]*>(.*?)</li>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )
    private val variantHref = Regex(
        """href="([^"]+)"[^>]*class="[^"]*s-catalog__body-variants-name|class="[^"]*s-catalog__body-variants-name[^"]*"[^>]*href="([^"]+)"""",
        RegexOption.IGNORE_CASE,
    )
    private val attrTerm = Regex(
        """<dt[^>]*class="[^"]*s-catalog__attrs-term[^"]*"[^>]*>([^<]+)</dt>\s*<dd[^>]*class="[^"]*s-catalog__attrs-data[^"]*"[^>]*>(.*?)</dd>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )
    private val modelCatalogVariant = Regex(
        """<li[^>]*class="[^"]*filtred_item[^"]*"[^>]*data-id="(\d+)"[^>]*>\s*<a[^>]*class="[^"]*s-catalog__model-link[^"]*"[^>]*href="([^"]+)"[^>]*>([^<]+)</a>""",
        RegexOption.IGNORE_CASE,
    )

    fun parseMakers(html: String, baseUrl: String): List<DiscoveredMaker> {
        val bySlug = linkedMapOf<String, DiscoveredMaker>()
        for (m in makerLink.findAll(html)) {
            val path = m.groupValues[1]
            val slug = (m.groupValues[2].ifBlank { m.groupValues[3] }).lowercase()
            if (slug.isBlank() || slug in SKIP_SLUGS) continue
            // Only hub depth /parts/{slug}
            val depth = path.trim('/').count { it == '/' }
            if (depth != 1 && !path.matches(Regex("""/parts/[a-z0-9-]+/?""", RegexOption.IGNORE_CASE))) {
                if (!path.matches(Regex("""/zapchasti-dlya-avtomobilej/[a-z0-9-]+/?""", RegexOption.IGNORE_CASE))) {
                    continue
                }
            }
            val name = slug.replace('-', ' ').replaceFirstChar { it.uppercase() }
            bySlug.putIfAbsent(
                slug,
                DiscoveredMaker(
                    name = name,
                    slug = slug,
                    sourceUrl = join(baseUrl, "/parts/$slug"),
                ),
            )
        }
        // Also scan anchor text near /parts/
        val anchor = Regex("""<a[^>]+href="(/parts/([a-z0-9-]+))/?"[^>]*>([^<]{2,40})</a>""", RegexOption.IGNORE_CASE)
        for (m in anchor.findAll(html)) {
            val slug = m.groupValues[2].lowercase()
            if (slug in SKIP_SLUGS) continue
            val label = clean(m.groupValues[3])
            bySlug[slug] = DiscoveredMaker(
                name = label.ifBlank { slug },
                slug = slug,
                sourceUrl = join(baseUrl, m.groupValues[1]),
            )
        }
        return bySlug.values.sortedBy { it.name.lowercase() }
    }

    fun parseModels(html: String, baseUrl: String, makerSlug: String): List<DiscoveredModel> {
        val bySlug = linkedMapOf<String, DiscoveredModel>()
        fun add(href: String, label: String) {
            if (!href.contains(makerSlug, ignoreCase = true)) return
            val path = href.split("?")[0]
            val slug = path.trimEnd('/').substringAfterLast('/')
            if (slug.isBlank() || slug.equals(makerSlug, ignoreCase = true)) return
            val name = clean(label).ifBlank { slug.replace('-', ' ') }
            val url = if (href.startsWith("http")) href else join(baseUrl, path)
            val existing = bySlug[slug]
            if (existing == null || name.length > existing.displayName.length) {
                bySlug[slug] = DiscoveredModel(displayName = name, slug = slug, sourceUrl = url)
            }
        }
        for (m in modelSilCard.findAll(html)) {
            val attrs = m.groupValues[1]
            val href = m.groupValues[2]
            if ("sil-card" !in attrs && "s-catalog__model-link" !in attrs) continue
            val dataName = Regex("""data-name="([^"]+)"""", RegexOption.IGNORE_CASE)
                .find(attrs)?.groupValues?.get(1).orEmpty()
            var name = dataName
            if (name.isBlank()) {
                val chunk = html.substring(m.range.last.coerceAtMost(html.lastIndex), (m.range.last + 800).coerceAtMost(html.length))
                name = Regex("""<h3[^>]*>([^<]+)</h3>""", RegexOption.IGNORE_CASE).find(chunk)?.groupValues?.get(1).orEmpty()
            }
            add(href, name)
        }
        val modelLink = Regex(
            """href="(/(?:parts|zapchasti-dlya-avtomobilej)/$makerSlug/[^"]+)"[^>]*>([^<]+)<""",
            RegexOption.IGNORE_CASE,
        )
        for (m in modelLink.findAll(html)) {
            add(m.groupValues[1], m.groupValues[2])
        }
        return bySlug.values.sortedBy { it.displayName.lowercase() }
    }

    fun parseChassis(html: String, baseUrl: String): List<DiscoveredChassis> {
        val out = linkedMapOf<String, DiscoveredChassis>()
        fun addFromBlock(block: String, hrefFallback: String = "") {
            val hrefM = variantHref.find(block)
            val href = (hrefM?.groupValues?.get(1)?.ifBlank { hrefM.groupValues.getOrNull(2) } ?: hrefFallback)
                .ifBlank { return }
            val attrs = parseAttrs(block)
            val frame = attrs["frame"] ?: attrs["frame_"] ?: ""
            val code = normalizeChassis(frame).ifBlank { return }
            val slug = href.trimEnd('/').substringAfterLast('/').split("?")[0]
            out[code] = DiscoveredChassis(
                code = code,
                variantSlug = slug,
                frame = frame,
                yearLabel = attrs["year"].orEmpty(),
                engineCode = engineFromAttrs(attrs),
                sourceUrl = if (href.startsWith("http")) href else join(baseUrl, href),
            )
        }
        for (m in variantItem.findAll(html)) {
            addFromBlock(m.groupValues[2])
        }
        for (m in modelCatalogVariant.findAll(html)) {
            val href = m.groupValues[2]
            val label = clean(m.groupValues[3])
            val code = normalizeChassis(label).ifBlank { normalizeChassis(label.substringBefore(' ')) }
            if (code.isBlank()) continue
            out[code] = DiscoveredChassis(
                code = code,
                variantSlug = href.trimEnd('/').substringAfterLast('/'),
                frame = label,
                yearLabel = "",
                engineCode = "",
                sourceUrl = if (href.startsWith("http")) href else join(baseUrl, href),
            )
        }
        return out.values.sortedBy { it.code }
    }

    private fun parseAttrs(block: String): Map<String, String> {
        val out = mutableMapOf<String, String>()
        for (m in attrTerm.findAll(block)) {
            val key = clean(m.groupValues[1]).lowercase().replace(' ', '_')
            out[key] = clean(m.groupValues[2].replace(Regex("<[^>]+>"), ""))
        }
        return out
    }

    private fun engineFromAttrs(attrs: Map<String, String>): String {
        for (key in listOf("engine", "engine_code", "engine_model", "двигатель")) {
            val v = attrs[key]?.trim().orEmpty()
            if (v.isNotEmpty()) return v
        }
        return attrs.entries.firstOrNull { "engine" in it.key || it.key.startsWith("двиг") }?.value.orEmpty()
    }

    private fun normalizeChassis(raw: String): String {
        val t = clean(raw).uppercase()
        if (t.isBlank()) return ""
        val m = Regex("""\b([A-Z]{1,3}\d{1,3}[A-Z]?)\b""").find(t)
        return m?.groupValues?.get(1) ?: t.take(12)
    }

    private fun clean(s: String): String =
        s.replace("&nbsp;", " ").replace(Regex("\\s+"), " ").trim()

    private fun join(base: String, path: String): String {
        val b = base.trimEnd('/')
        return if (path.startsWith("/")) "$b$path" else "$b/$path"
    }

    private val SKIP_SLUGS = setOf("parts", "zapchasti-dlya-avtomobilej", "catalog", "search", "en", "ru")
}
