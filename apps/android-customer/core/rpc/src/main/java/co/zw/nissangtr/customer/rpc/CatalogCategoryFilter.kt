package co.zw.nissangtr.customer.rpc

/**
 * Merchandising / shop category filter ↔ EPC category_name (web `@gtr/shared`
 * `catalog-category-filter` parity).
 *
 * FILTERS → CATEGORY lists curated parents/leaves — never raw Megazip
 * `pnc_categories.category_name` assembly dumps.
 */
object CatalogCategoryFilter {
    data class MerchSub(val slug: String, val label: String, val stems: List<String> = emptyList())
    data class MerchParent(val slug: String, val label: String, val subcategories: List<MerchSub>)
    data class FacetOption(val slug: String, val label: String)
    data class ResolveResult(val parent: MerchParent, val sub: MerchSub? = null)

    val taxonomy: List<MerchParent> = listOf(
        MerchParent(
            "brakes", "Brakes",
            listOf(
                MerchSub("brake-pads", "Brake pads", listOf("pad", "lining")),
                MerchSub("brake-discs", "Brake discs", listOf("disc", "rotor")),
                MerchSub("calipers", "Calipers", listOf("caliper")),
                MerchSub("brake-hoses", "Brake hoses & lines", listOf("hose", "piping", "brake line", "brake tube")),
                MerchSub("brake-fluid", "Brake fluid", listOf("brake fluid")),
            ),
        ),
        MerchParent(
            "filters", "Filters",
            listOf(
                MerchSub("oil-filters", "Oil filters", listOf("oil filter")),
                MerchSub("air-filters", "Air filters", listOf("air filter", "air cleaner", "cleaner")),
                MerchSub("cabin-filters", "Cabin filters", listOf("cabin", "pollen", "cabin filter")),
                MerchSub("fuel-filters", "Fuel filters", listOf("fuel filter")),
            ),
        ),
        MerchParent(
            "engine", "Engine",
            listOf(
                MerchSub("gaskets", "Gaskets", listOf("gasket", "seal")),
                MerchSub("timing", "Timing belts & chains", listOf("timing", "cam belt", "timing chain")),
                MerchSub("pulleys", "Pulleys", listOf("pulley", "idler")),
                MerchSub("engine-sensors", "Engine sensors", listOf("sensor", "o2", "oxygen", "knock")),
                MerchSub("spark-plugs", "Spark plugs", listOf("spark", "glow plug")),
            ),
        ),
        MerchParent(
            "suspension", "Suspension",
            listOf(
                MerchSub("shock-absorbers", "Shock absorbers", listOf("shock", "strut", "damper", "absorber")),
                MerchSub("coil-springs", "Coil springs", listOf("coil spring", "spring")),
                MerchSub("control-arms", "Control arms", listOf("control arm", "wishbone", "lateral link", "trailing arm")),
                MerchSub("ball-joints", "Ball joints", listOf("ball joint", "ball-joint")),
                MerchSub("tie-rod-ends", "Tie rod ends", listOf("tie rod", "tie-rod", "outer socket", "inner socket")),
                MerchSub("bushings", "Bushings", listOf("bushing", "arm bush")),
            ),
        ),
        MerchParent(
            "electrical", "Electrical",
            listOf(
                MerchSub("batteries", "Batteries", listOf("battery")),
                MerchSub("alternators", "Alternators", listOf("alternator")),
                MerchSub("starters", "Starters", listOf("starter")),
                MerchSub("ignition", "Ignition", listOf("ignition", "coil", "distributor")),
                MerchSub("wiring", "Wiring", listOf("wiring", "harness")),
            ),
        ),
        MerchParent(
            "cooling", "Cooling",
            listOf(
                MerchSub("radiators", "Radiators", listOf("radiator")),
                MerchSub("water-pumps", "Water pumps", listOf("water pump")),
                MerchSub("thermostats", "Thermostats", listOf("thermostat")),
                MerchSub("cooling-hoses", "Cooling hoses", listOf("radiator hose", "coolant hose", "heater hose")),
                MerchSub("heater", "Heater & A/C", listOf("heater", "evaporator", "condenser", "a/c", "ac ")),
            ),
        ),
        MerchParent(
            "body", "Body",
            listOf(
                MerchSub("body-panels", "Body panels", listOf("fender", "bonnet", "hood", "door panel", "quarter")),
                MerchSub("bumpers", "Bumpers", listOf("bumper")),
                MerchSub("mirrors", "Mirrors", listOf("mirror")),
                MerchSub("exhaust", "Exhaust", listOf("exhaust", "muffler", "silencer", "catalytic")),
            ),
        ),
        MerchParent(
            "transmission", "Drivetrain",
            listOf(
                MerchSub("clutch", "Clutch kits", listOf("clutch")),
                MerchSub("flywheels", "Flywheels", listOf("flywheel")),
                MerchSub("gearbox-mounts", "Gearbox mounts", listOf("mount", "transmission mount")),
                MerchSub("driveshaft", "Driveshaft & CV", listOf("driveshaft", "drive shaft", "cv joint", "axle")),
                MerchSub("transfer", "Transfer & differential", listOf("transfer", "differential", "diff ")),
            ),
        ),
    )

    private val aliases: Map<String, List<String>> = mapOf(
        "brakes" to listOf("brake"),
        "braking" to listOf("brake"),
        "filters" to listOf("filter", "cleaner"),
        "engine" to listOf("engine"),
        "engine parts" to listOf("engine"),
        "cooling" to listOf("cool", "radiator", "thermostat"),
        "cooling & heating" to listOf("cool", "radiator", "heater", "heating"),
        "suspension" to listOf("suspension", "strut"),
        "steering & suspension" to listOf("steering", "suspension", "strut"),
        "electrical" to listOf("electric", "wiring"),
        "body" to listOf("body", "bumper"),
        "body & exhaust" to listOf("body", "exhaust", "bumper"),
        "transmission" to listOf("transmission", "clutch", "transfer"),
        "drivetrain" to listOf("transmission", "drivetrain", "transfer", "power train"),
        "fuel system" to listOf("fuel"),
        "lighting" to listOf("lamp", "light", "headlamp"),
        "service parts" to listOf("filter", "oil", "spark", "service"),
    )

    private val parentSynonyms: Map<String, String> = mapOf(
        "braking" to "brakes",
        "drivetrain" to "transmission",
        "engine parts" to "engine",
        "cooling & heating" to "cooling",
        "steering & suspension" to "suspension",
        "body & exhaust" to "body",
    )

    fun stripEpcVehicleSuffix(name: String): String {
        val s = name.trim()
        if (s.isEmpty()) return s
        Regex("""^(.+?)\s+for\s+\d""", RegexOption.IGNORE_CASE).find(s)?.let {
            return it.groupValues[1].trim()
        }
        Regex(
            """^(.+?)\s+for\s+(?:the\s+)?(?:\d{4}|nissan|toyota|honda|suzuki|subaru|mitsubishi|lexus)\b""",
            RegexOption.IGNORE_CASE,
        ).find(s)?.let {
            return it.groupValues[1].trim()
        }
        Regex("""^(.+?)\s+FOR\s+""").find(s)?.let {
            if (it.groupValues[1].any { ch -> ch.isUpperCase() }) {
                return it.groupValues[1].trim()
            }
        }
        return s
    }

    fun resolve(filter: String): ResolveResult? {
        val raw = filter.trim()
        if (raw.isEmpty()) return null
        val key = normKey(raw)
        val slug = slugKey(raw)

        for (parent in taxonomy) {
            for (sub in parent.subcategories) {
                if (sub.slug == slug || normKey(sub.label) == key || slugKey(sub.label) == slug) {
                    return ResolveResult(parent, sub)
                }
            }
        }
        for (parent in taxonomy) {
            if (parent.slug == slug || normKey(parent.label) == key || slugKey(parent.label) == slug) {
                return ResolveResult(parent)
            }
        }
        val mapped = parentSynonyms[key] ?: parentSynonyms[slug]
        if (mapped != null) {
            val parent = taxonomy.find { it.slug == mapped }
            if (parent != null) return ResolveResult(parent)
        }
        return null
    }

    /** FILTERS → CATEGORY options (never raw EPC names). */
    fun facetOptions(activeFilter: String?, activeSubfilter: String? = null): List<FacetOption> {
        val parentFilter = activeFilter?.trim().orEmpty()
        if (parentFilter.isEmpty() && activeSubfilter.isNullOrBlank()) {
            return taxonomy.map { FacetOption(it.slug, it.label) }
        }
        val resolved = resolve(parentFilter.ifEmpty { activeSubfilter!!.trim() })
            ?: activeSubfilter?.trim()?.takeIf { it.isNotEmpty() }?.let { resolve(it) }
        if (resolved == null) return emptyList()
        return resolved.parent.subcategories.map { FacetOption(it.slug, it.label) }
    }

    fun facetLabels(activeFilter: String?, activeSubfilter: String? = null): List<String> =
        facetOptions(activeFilter, activeSubfilter).map { it.label }

    fun needles(filter: String): List<String> {
        val raw = filter.trim().lowercase()
        if (raw.isEmpty()) return emptyList()
        val out = linkedSetOf<String>()
        val resolved = resolve(filter)
        if (resolved?.sub != null) {
            collectNeedles(normKey(resolved.sub.label), resolved.sub.stems, out)
            collectNeedles(resolved.sub.slug.replace('-', ' '), emptyList(), out)
        } else if (resolved != null) {
            collectNeedles(normKey(resolved.parent.label), emptyList(), out)
            collectNeedles(resolved.parent.slug, emptyList(), out)
            aliases[resolved.parent.slug]?.let { out += it }
            aliases[raw]?.let { out += it }
        } else {
            collectNeedles(raw, emptyList(), out)
        }
        return out.sortedByDescending { it.length }
    }

    fun matches(filter: String, vararg fields: String?): Boolean {
        val ns = needles(filter)
        if (ns.isEmpty()) return false
        val haystacks = fields.mapNotNull {
            it?.trim()?.takeIf(String::isNotEmpty)?.let { v ->
                stripEpcVehicleSuffix(v).trim().lowercase()
            }?.takeIf(String::isNotEmpty)
        }
        if (haystacks.isEmpty()) return false
        for (hay in haystacks) {
            for (needle in ns) {
                if (hay == needle || hay.contains(needle)) return true
            }
        }
        return false
    }

    private fun collectNeedles(raw: String, extra: List<String>, out: MutableSet<String>) {
        out += raw
        stem(raw)?.let { out += it }
        aliases[raw]?.let { out += it }
        for (part in raw.split(Regex("[^a-z0-9]+"))) {
            if (part.length < 3) continue
            out += part
            stem(part)?.let { out += it }
            aliases[part]?.let { out += it }
        }
        for (e in extra) {
            val n = e.trim().lowercase()
            if (n.isEmpty()) continue
            out += n
            stem(n)?.let { out += it }
        }
    }

    private fun normKey(raw: String): String =
        raw.trim().lowercase().replace('_', ' ').replace(Regex("\\s+"), " ")

    private fun slugKey(raw: String): String =
        raw.trim().lowercase()
            .replace("&", " and ")
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')

    private fun stem(raw: String): String? {
        val t = raw.trim().lowercase()
        if (t.length < 4) return null
        val stemmed = when {
            t.endsWith("ies") && t.length > 4 -> t.dropLast(3) + "y"
            t.endsWith("ses") && t.length > 4 -> t.dropLast(2)
            t.endsWith("s") && !t.endsWith("ss") -> t.dropLast(1)
            else -> t
        }
        return stemmed.takeIf { it.length >= 3 }
    }
}
