package co.zw.nissangtr.pos.domain.model

enum class PinKind { PART, MODEL, CATEGORY, SUBCATEGORY }

/** Operator pin, server-persisted per operator (Blueprint §7.4, `list_pos_popular_pins`). */
data class PopularPin(
    val kind: PinKind,
    val key: String,
    val label: String,
    val subtitle: String?,
    val searchQuery: String,
    val oemPartNumber: String? = null,
    val imageUrl: String? = null,
    val modelSlug: String? = null,
    val categoryName: String? = null,
) {
    val stableKey: String get() = "${kind.name.lowercase()}:$key"

    companion object {
        fun forPart(part: CatalogPart): PopularPin = PopularPin(
            kind = PinKind.PART,
            key = part.oemKey,
            label = part.name,
            subtitle = part.oemPartNumber,
            searchQuery = part.oemPartNumber,
            oemPartNumber = part.oemPartNumber,
            imageUrl = part.imageUrl,
        )

        fun forCategory(category: Category): PopularPin = PopularPin(
            kind = PinKind.CATEGORY,
            key = "category:${category.id}",
            label = category.label,
            subtitle = null,
            searchQuery = category.query,
            categoryName = category.label,
        )

        fun forVehicle(vehicle: VehicleSelection): PopularPin = PopularPin(
            kind = PinKind.MODEL,
            key = "${vehicle.modelSlug}:${vehicle.chassisCode}:${vehicle.engineCode}",
            label = "${vehicle.modelName} ${vehicle.chassisCode}",
            subtitle = vehicle.engineCode,
            searchQuery = vehicle.label,
            modelSlug = vehicle.modelSlug,
        )
    }
}

/** One card in the Popular Items row: an operator pin, or a server best seller. */
sealed interface PopularRowItem {
    val stableKey: String

    data class Pinned(val pin: PopularPin) : PopularRowItem {
        override val stableKey: String get() = "pin:${pin.stableKey}"
    }

    data class BestSeller(val part: CatalogPart) : PopularRowItem {
        override val stableKey: String get() = "seller:${part.stockItemId ?: part.oemKey}"
    }
}

/**
 * Owner decision D1 (delta D-014): pins first in pin order, then server best sellers in server rank,
 * minus best sellers already pinned as parts and minus those this operator hid. Same rule as the web
 * POS (`apps/web/lib/pos/popular.ts`), so both surfaces show the same row.
 */
fun buildPopularRow(
    pins: List<PopularPin>,
    bestSellers: List<CatalogPart>,
    hiddenStockItemIds: Set<String>,
): List<PopularRowItem> {
    val pinnedOems = pins.asSequence()
        .filter { it.kind == PinKind.PART }
        .mapNotNull { it.oemPartNumber?.trim()?.uppercase() }
        .toSet()
    val sellers = bestSellers
        .filterNot { it.oemKey in pinnedOems }
        .filterNot { it.stockItemId != null && it.stockItemId in hiddenStockItemIds }
        .map { PopularRowItem.BestSeller(it) }
    return pins.map { PopularRowItem.Pinned(it) } + sellers
}
