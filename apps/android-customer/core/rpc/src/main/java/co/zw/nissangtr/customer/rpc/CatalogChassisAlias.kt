package co.zw.nissangtr.customer.rpc

/**
 * Chassis codes present in vehicle_master / fixtures that lack catalog_variants
 * rows on Megazip — map to the published EPC chassis for browse / fitment.
 * Keep in sync with packages/shared catalog-chassis-alias.ts.
 */
object CatalogChassisAlias {
    private val aliases: Map<String, String> = mapOf(
        "D40" to "D22",
        "T32" to "T31",
    )

    /** Ordered unique codes to try (requested first, then alias). */
    fun lookupCodes(chassis: String): List<String> {
        val primary = chassis.trim().uppercase()
        if (primary.isEmpty()) return emptyList()
        val out = mutableListOf(primary)
        aliases[primary]?.trim()?.uppercase()?.takeIf { it.isNotEmpty() && it != primary }?.let {
            out += it
        }
        return out
    }
}
