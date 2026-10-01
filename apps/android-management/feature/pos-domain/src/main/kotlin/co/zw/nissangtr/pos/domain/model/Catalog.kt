package co.zw.nissangtr.pos.domain.model

/**
 * A sellable part as the catalogue returns it. [price] is null when the shop has no price for it
 * yet; [saleableQty] is null when stock is unknown (e.g. an EPC-only part).
 */
data class CatalogPart(
    val stockItemId: String?,
    val oemPartNumber: String,
    val name: String,
    val price: Money?,
    val saleableQty: Double?,
    val imageUrl: String?,
) {
    /** Normalised OEM number used to match pins, best sellers and cart lines. */
    val oemKey: String get() = oemPartNumber.trim().uppercase()

    val canAdd: Boolean get() = stockItemId != null && price != null
}

/** Discovery category tile. [query] seeds a real catalogue search — never canned results. */
data class Category(
    val id: String,
    val label: String,
    val query: String,
)
