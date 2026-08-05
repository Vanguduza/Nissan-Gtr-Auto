package co.zw.nissangtr.customer.rpc

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Mirrors web `SearchMode` / `search_catalog` p_mode. */
enum class SearchMode(val rpcValue: String) {
    PART("part"),
    VIN("vin"),
    MODEL("model"),
    PNC("pnc"),
    ;

    companion object {
        fun fromRpc(value: String?): SearchMode =
            entries.find { it.rpcValue == value?.lowercase() } ?: PART
    }
}

/** Mirrors web catalog stock badge. */
enum class StockState {
    IN_STOCK,
    LOW,
    BACKORDER,
    ;

    fun label(): String = when (this) {
        IN_STOCK -> "In stock"
        LOW -> "Low stock"
        BACKORDER -> "Backorder"
    }
}

data class CatalogPartHit(
    val oemPartNumber: String,
    val pncCode: String? = null,
    val categoryName: String? = null,
    val subcategoryName: String? = null,
    val chassisCode: String? = null,
    val engineCode: String? = null,
)

data class SearchCatalogResponse(
    val mode: SearchMode,
    val query: String,
    val parts: List<CatalogPartHit>,
    /** `meili` when Edge proxy succeeded; `fts` on Postgres fallback. */
    val backend: String? = null,
    /** Meili facet counts — category / model / part facets when available. */
    val facetDistribution: Map<String, Map<String, Int>> = emptyMap(),
)

data class CatalogListItem(
    val stockItemId: String,
    val oem: String,
    val name: String,
    val stock: StockState,
    val usd: Double?,
    val category: String? = null,
)

data class CatalogBrowseResult(
    val items: List<CatalogListItem>,
    val categories: List<String> = emptyList(),
)

/** PDP subset aligned with web `CatalogProduct`. */
data class CatalogProduct(
    val stockItemId: String,
    val baseUomId: String,
    val oem: String,
    val name: String,
    val brand: String? = null,
    val category: String? = null,
    val usd: Double?,
    val stock: StockState,
    val coreCharge: Double = 0.0,
    val fitmentLines: List<String> = emptyList(),
    /** OEM / product photo URLs when published (Storage or CDN). */
    val imageUrls: List<String> = emptyList(),
    /** EPC diagram public URL from `catalog-diagrams` when `diagram_path` is set. */
    val diagramUrl: String? = null,
    val specs: List<String> = emptyList(),
    val replaces: List<String> = emptyList(),
)

/** Rich expandable PDP copy — name + metadata when present. */
fun CatalogProduct.descriptionText(): String = buildString {
    append(name.trim())
    brand?.trim()?.takeIf { it.isNotEmpty() }?.let { append("\nBrand: $it") }
    category?.trim()?.takeIf { it.isNotEmpty() }?.let { append("\nCategory: $it") }
    specs.forEach { append("\n· $it") }
    if (replaces.isNotEmpty()) {
        append("\nReplaces / cross-ref: ${replaces.joinToString(", ")}")
    }
}.trim()

fun stockStateFromQty(qty: Double, reorderPoint: Double?): StockState {
    if (qty <= 0) return StockState.BACKORDER
    if (reorderPoint != null && qty <= reorderPoint) return StockState.LOW
    return StockState.IN_STOCK
}

/** Flatten mixed search JSON into part hits (web / Meili Edge parity). */
fun parseSearchCatalogJson(element: JsonElement): SearchCatalogResponse {
    val obj = element as? JsonObject ?: return SearchCatalogResponse(SearchMode.PART, "", emptyList())
    val mode = SearchMode.fromRpc(obj["mode"]?.jsonPrimitive?.contentOrNull)
    val query = obj["query"]?.jsonPrimitive?.contentOrNull.orEmpty()
    val results = obj["results"]?.jsonArray ?: JsonArray(emptyList())
    val parts = mutableListOf<CatalogPartHit>()
    for (row in results) {
        collectPartHits(row, parts)
    }
    val distinct = parts.distinctBy { it.oemPartNumber.uppercase() }
    val backend = obj["backend"]?.jsonPrimitive?.contentOrNull
    return SearchCatalogResponse(
        mode = mode,
        query = query,
        parts = distinct,
        backend = backend,
        facetDistribution = parseFacetDistribution(obj["facetDistribution"]),
    )
}

private fun parseFacetDistribution(element: JsonElement?): Map<String, Map<String, Int>> {
    val root = element as? JsonObject ?: return emptyMap()
    return root.mapNotNull { (facet, valuesEl) ->
        val values = valuesEl as? JsonObject ?: return@mapNotNull null
        val counts = values.mapNotNull { (label, countEl) ->
            val n = countEl.jsonPrimitive.content.toIntOrNull() ?: return@mapNotNull null
            label to n
        }.toMap()
        if (counts.isEmpty()) null else facet to counts
    }.toMap()
}

private fun collectPartHits(row: JsonElement, out: MutableList<CatalogPartHit>) {
    val o = row as? JsonObject ?: return
    val type = o["type"]?.jsonPrimitive?.contentOrNull ?: "part"
    when (type) {
        "part" -> o.toPartHit()?.let { out.add(it) }
        "vehicle", "pnc" -> {
            val fitments = o["fitments"]?.jsonArray ?: return
            for (f in fitments) {
                (f as? JsonObject)?.toPartHit()?.let { out.add(it) }
            }
        }
    }
}

private fun JsonObject.toPartHit(): CatalogPartHit? {
    val oem = this["oem_part_number"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
    if (oem.isEmpty()) return null
    return CatalogPartHit(
        oemPartNumber = oem,
        pncCode = this["pnc_code"]?.jsonPrimitive?.contentOrNull,
        categoryName = this["category_name"]?.jsonPrimitive?.contentOrNull,
        subcategoryName = this["subcategory_name"]?.jsonPrimitive?.contentOrNull,
        chassisCode = this["chassis_code"]?.jsonPrimitive?.contentOrNull,
        engineCode = this["engine_code"]?.jsonPrimitive?.contentOrNull,
    )
}
