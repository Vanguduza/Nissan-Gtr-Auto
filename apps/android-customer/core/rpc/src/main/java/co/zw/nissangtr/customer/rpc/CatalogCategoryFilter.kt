package co.zw.nissangtr.customer.rpc

/**
 * Merchandising / shop category filter ↔ EPC category_name (web `@gtr/shared`
 * `categoryMatchesFilter` parity).
 */
object CatalogCategoryFilter {
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

    fun needles(filter: String): List<String> {
        val raw = filter.trim().lowercase()
        if (raw.isEmpty()) return emptyList()
        val out = linkedSetOf<String>()
        out += raw
        stem(raw)?.let { out += it }
        aliases[raw]?.let { out += it }
        for (part in raw.split(Regex("[^a-z0-9]+"))) {
            if (part.length < 3) continue
            out += part
            stem(part)?.let { out += it }
            aliases[part]?.let { out += it }
        }
        return out.sortedByDescending { it.length }
    }

    fun matches(filter: String, vararg fields: String?): Boolean {
        val ns = needles(filter)
        if (ns.isEmpty()) return false
        val haystacks = fields.mapNotNull { it?.trim()?.lowercase()?.takeIf(String::isNotEmpty) }
        if (haystacks.isEmpty()) return false
        for (hay in haystacks) {
            for (needle in ns) {
                if (hay == needle || hay.contains(needle)) return true
            }
        }
        return false
    }

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
