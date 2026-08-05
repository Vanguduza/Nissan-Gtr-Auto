package co.zw.nissangtr.customer.rpc

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Live catalog reads for [SupabaseRpcClient] — mirrors web catalog-search + catalog-product.
 */
internal object CatalogRpcLive {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun searchCatalog(client: SupabaseClient, mode: SearchMode, query: String): SearchCatalogResponse {
        val trimmed = query.trim()
        require(trimmed.isNotEmpty()) { "search query required" }
        val raw = client.postgrest.rpc(
            RpcNames.SEARCH_CATALOG,
            buildJsonObject {
                put("p_mode", mode.rpcValue)
                put("p_query", trimmed)
            },
        ).decodeAs<JsonElement>()
        return parseSearchCatalogJson(raw).copy(backend = "fts")
    }

    /**
     * Edge Function Meili proxy — JWT forwarded by supabase-kt; no Meili key in app.
     * Falls back to [searchCatalog] on any failure.
     */
    suspend fun searchCatalogMeili(
        client: SupabaseClient,
        mode: SearchMode,
        query: String,
        limit: Int = 20,
        facets: List<String>? = null,
    ): SearchCatalogResponse {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            return SearchCatalogResponse(mode = mode, query = "", parts = emptyList())
        }
        return try {
            val response = client.functions.invoke(RpcNames.CATALOG_SEARCH_MEILI_FN) {
                setBody(
                    buildJsonObject {
                        put("mode", mode.rpcValue)
                        put("query", trimmed)
                        put("limit", limit.coerceIn(1, 50))
                        if (!facets.isNullOrEmpty()) {
                            put(
                                "facets",
                                JsonArray(facets.map { JsonPrimitive(it) }),
                            )
                        }
                    },
                )
            }
            val text = response.bodyAsText()
            val el = json.parseToJsonElement(text)
            val err = (el as? JsonObject)?.get("error")?.jsonPrimitive?.contentOrNull
            if (!err.isNullOrBlank()) {
                return searchCatalog(client, mode, trimmed)
            }
            parseSearchCatalogJson(el)
        } catch (_: Exception) {
            searchCatalog(client, mode, trimmed)
        }
    }

    suspend fun listCatalogBrowse(
        client: SupabaseClient,
        category: String?,
        limit: Int,
    ): CatalogBrowseResult {
        val cap = limit.coerceIn(1, 100)
        val cat = category?.trim()?.takeIf { it.isNotEmpty() }
        val oemFilter = if (cat != null) resolveOemFilterForCategory(client, cat) else null
        if (cat != null && oemFilter != null && oemFilter.isEmpty()) {
            return CatalogBrowseResult(emptyList(), emptyList())
        }

        val items = client.from("stock_items")
            .select(Columns.list("id", "oem_part_number", "description", "reorder_point")) {
                if (oemFilter != null) {
                    filter { isIn("oem_part_number", oemFilter) }
                }
                order("oem_part_number", Order.ASCENDING)
                limit(cap.toLong())
            }
            .decodeList<StockItemBrowseRow>()

        if (items.isEmpty()) return CatalogBrowseResult(emptyList(), emptyList())

        val ids = items.map { it.id }
        val oems = items.map { it.oemPartNumber }
        val priceByItem = loadDefaultPrices(client, ids)
        val qtyByItem = loadSaleableQty(client, ids)
        val catByOem = loadCategoryByOem(client, oems)

        val list = items.map { row ->
            val price = priceByItem[row.id]
            val usd = if (price?.currency == "USD") price.unitPrice else price?.unitPrice
            CatalogListItem(
                stockItemId = row.id,
                oem = row.oemPartNumber,
                name = row.description?.trim().orEmpty().ifEmpty { row.oemPartNumber },
                stock = stockStateFromQty(qtyByItem[row.id] ?: 0.0, row.reorderPoint),
                usd = usd,
                category = catByOem[row.oemPartNumber],
            )
        }
        return CatalogBrowseResult(items = list, categories = emptyList())
    }

    private suspend fun resolveOemFilterForCategory(
        client: SupabaseClient,
        categoryLabel: String,
    ): List<String>? {
        val cat = categoryLabel.trim()
        if (cat.isEmpty()) return null
        val pncRows = client.from("pnc_categories")
            .select(Columns.list("pnc_code")) {
                filter { ilike("category_name", cat) }
                limit(200)
            }
            .decodeList<PncCodeRow>()
        val codes = pncRows.map { it.pncCode }.filter { it.isNotBlank() }
        if (codes.isEmpty()) return emptyList()
        val fits = client.from("part_fitment")
            .select(Columns.list("oem_part_number")) {
                filter { isIn("pnc_code", codes) }
                limit(200)
            }
            .decodeList<OemOnlyRow>()
        return fits.map { it.oemPartNumber }.distinct()
    }

    private suspend fun loadCategoryByOem(
        client: SupabaseClient,
        oems: List<String>,
    ): Map<String, String> {
        if (oems.isEmpty()) return emptyMap()
        val rows = client.from("part_fitment")
            .select(Columns.raw("oem_part_number, pnc_categories ( category_name )")) {
                filter { isIn("oem_part_number", oems) }
                limit(200)
            }
            .decodeList<FitmentCategoryRow>()
        val out = mutableMapOf<String, String>()
        for (row in rows) {
            if (out.containsKey(row.oemPartNumber)) continue
            val name = row.pncCategories?.categoryName?.trim().orEmpty()
            if (name.isNotEmpty()) out[row.oemPartNumber] = name
        }
        return out
    }

    suspend fun loadCatalogProduct(client: SupabaseClient, oemParam: String): CatalogProduct {
        val oem = oemParam.trim()
        require(oem.isNotEmpty()) { "OEM required" }
        val item = client.from("stock_items")
            .select(
                Columns.list(
                    "id",
                    "oem_part_number",
                    "description",
                    "reorder_point",
                    "base_uom_id",
                ),
            ) {
                filter { eq("oem_part_number", oem) }
                limit(1)
            }
            .decodeList<StockItemPdpRow>()
            .firstOrNull()
            ?: error("Part not found: $oem")

        val uomId = item.baseUomId?.trim().orEmpty()
        require(uomId.isNotEmpty()) { "Part $oem has no base UOM" }

        val price = loadDefaultPrices(client, listOf(item.id))[item.id]
        val qty = loadSaleableQty(client, listOf(item.id))[item.id] ?: 0.0
        val fitmentMeta = loadFitmentMeta(client, item.oemPartNumber)
        val diagramUrl = loadDiagramPublicUrl(client, item.oemPartNumber)
        val replaces = loadReplaces(client, item.oemPartNumber)

        val imageUrls = buildList {
            diagramUrl?.let { add(it) }
        }

        return CatalogProduct(
            stockItemId = item.id,
            baseUomId = uomId,
            oem = item.oemPartNumber,
            name = item.description?.trim().orEmpty().ifEmpty { item.oemPartNumber },
            brand = "Nissan OE",
            category = fitmentMeta.category,
            usd = if (price?.currency == "USD") price.unitPrice else price?.unitPrice,
            stock = stockStateFromQty(qty, item.reorderPoint),
            coreCharge = price?.coreCharge ?: 0.0,
            fitmentLines = fitmentMeta.lines,
            imageUrls = imageUrls,
            diagramUrl = diagramUrl,
            specs = fitmentMeta.specs,
            replaces = replaces,
        )
    }

    private data class FitmentMeta(
        val lines: List<String>,
        val specs: List<String>,
        val category: String? = null,
    )

    private suspend fun loadFitmentMeta(client: SupabaseClient, oem: String): FitmentMeta {
        val rows = client.from("part_fitment")
            .select(
                Columns.raw(
                    "chassis_code, engine_code, pnc_code, pnc_categories ( category_name, subcategory_name )",
                ),
            ) {
                filter { eq("oem_part_number", oem) }
                limit(12)
            }
            .decodeList<FitmentMetaRow>()
        val lines = rows.mapNotNull { row ->
            val bits = listOfNotNull(
                row.chassisCode?.trim()?.takeIf { it.isNotEmpty() },
                row.engineCode?.trim()?.takeIf { it.isNotEmpty() },
                row.pncCode?.trim()?.takeIf { it.isNotEmpty() }?.let { "PNC $it" },
            )
            bits.takeIf { it.isNotEmpty() }?.joinToString(" · ")
        }
        val primary = rows.firstOrNull()
        val specs = buildList {
            primary?.pncCode?.trim()?.takeIf { it.isNotEmpty() }?.let { add("PNC $it") }
            primary?.chassisCode?.trim()?.takeIf { it.isNotEmpty() }?.let { add("Chassis $it") }
            primary?.engineCode?.trim()?.takeIf { it.isNotEmpty() }?.let { add("Engine $it") }
        }
        val category = primary?.pncCategories?.categoryName?.trim()?.takeIf { it.isNotEmpty() }
            ?: primary?.pncCategories?.subcategoryName?.trim()?.takeIf { it.isNotEmpty() }
        return FitmentMeta(lines = lines, specs = specs, category = category)
    }

    private suspend fun loadReplaces(client: SupabaseClient, oem: String): List<String> {
        val xrefs = client.from("oe_cross_refs")
            .select(Columns.list("oe_number")) {
                filter { eq("oem_part_number", oem) }
                limit(20)
            }
            .decodeList<OeNumberRow>()
        val superseded = client.from("part_fitment")
            .select(Columns.list("superseded_by")) {
                filter { eq("oem_part_number", oem) }
                limit(20)
            }
            .decodeList<SupersededRow>()
        return (xrefs.mapNotNull { it.oeNumber } + superseded.mapNotNull { it.supersededBy })
            .filter { it.trim().isNotEmpty() && !it.equals(oem, ignoreCase = true) }
            .distinct()
    }

    private suspend fun loadDiagramPublicUrl(client: SupabaseClient, oem: String): String? {
        val rows = client.from("part_fitment")
            .select(Columns.list("diagram_path")) {
                filter { eq("oem_part_number", oem) }
                limit(20)
            }
            .decodeList<DiagramPathRow>()
        val path = rows.firstOrNull { !it.diagramPath.isNullOrBlank() }?.diagramPath?.trim().orEmpty()
        if (path.isEmpty()) return null
        if (path.startsWith("http://", ignoreCase = true) || path.startsWith("https://", ignoreCase = true)) {
            return path
        }
        val base = client.supabaseUrl.trimEnd('/')
        val cleanPath = path.trimStart('/')
        return "$base/storage/v1/object/public/${RpcNames.CATALOG_DIAGRAMS_BUCKET}/$cleanPath"
    }

    suspend fun addCartLineByOem(
        client: SupabaseClient,
        rpc: RpcClient,
        oem: String,
        qty: Double,
    ): Pair<String, String> {
        require(qty > 0) { "qty must be > 0" }
        val product = loadCatalogProduct(client, oem)
        val cartId = rpc.getOpenCart()?.id ?: rpc.createCustomerCart(
            warehouseId = resolveMainWarehouseId(client),
            currency = CurrencyCode.USD,
            fulfillmentMode = FulfillmentMode.IMMEDIATE,
            exchangeRate = 1.0,
        )
        val lineId = rpc.addCustomerCartLine(
            cartId = cartId,
            stockItemId = product.stockItemId,
            uomId = product.baseUomId,
            qty = qty,
        )
        return cartId to lineId
    }

    private suspend fun resolveMainWarehouseId(client: SupabaseClient): String {
        val main = client.from("warehouses")
            .select(Columns.list("id")) {
                filter {
                    eq("is_active", true)
                    eq("is_quarantine", false)
                    eq("code", "MAIN")
                }
                limit(1)
            }
            .decodeList<IdRow>()
            .firstOrNull()
        if (main != null) return main.id
        return client.from("warehouses")
            .select(Columns.list("id")) {
                filter {
                    eq("is_active", true)
                    eq("is_quarantine", false)
                }
                order("code", Order.ASCENDING)
                limit(1)
            }
            .decodeList<IdRow>()
            .firstOrNull()
            ?.id
            ?: error("no active warehouse")
    }

    private suspend fun loadSaleableQty(client: SupabaseClient, stockItemIds: List<String>): Map<String, Double> {
        if (stockItemIds.isEmpty()) return emptyMap()
        val rows = client.from("stock_levels")
            .select(Columns.raw("stock_item_id,quantity,warehouses!inner(is_quarantine,is_active)")) {
                filter { isIn("stock_item_id", stockItemIds) }
            }
            .decodeList<StockLevelRow>()
        val out = mutableMapOf<String, Double>()
        for (row in rows) {
            val wh = row.warehouses
            if (wh.isQuarantine || !wh.isActive) continue
            out[row.stockItemId] = (out[row.stockItemId] ?: 0.0) + row.quantity
        }
        return out
    }

    private suspend fun loadDefaultPrices(
        client: SupabaseClient,
        stockItemIds: List<String>,
    ): Map<String, PriceRow> {
        if (stockItemIds.isEmpty()) return emptyMap()
        val list = client.from("price_lists")
            .select(Columns.list("id", "currency")) {
                filter {
                    eq("is_default", true)
                    eq("is_active", true)
                }
                limit(1)
            }
            .decodeList<PriceListRow>()
            .firstOrNull() ?: return emptyMap()

        val rows = client.from("price_list_items")
            .select(Columns.list("stock_item_id", "unit_price", "core_charge")) {
                filter {
                    eq("price_list_id", list.id)
                    isIn("stock_item_id", stockItemIds)
                }
            }
            .decodeList<PriceListItemRow>()

        return rows.associate { row ->
            row.stockItemId to PriceRow(
                unitPrice = row.unitPrice,
                coreCharge = row.coreCharge,
                currency = list.currency,
            )
        }
    }
}

@Serializable
private data class IdRow(val id: String)

@Serializable
private data class StockItemBrowseRow(
    val id: String,
    @SerialName("oem_part_number") val oemPartNumber: String,
    val description: String? = null,
    @SerialName("reorder_point") val reorderPoint: Double? = null,
)

@Serializable
private data class StockItemPdpRow(
    val id: String,
    @SerialName("oem_part_number") val oemPartNumber: String,
    val description: String? = null,
    @SerialName("reorder_point") val reorderPoint: Double? = null,
    @SerialName("base_uom_id") val baseUomId: String? = null,
)

@Serializable
private data class StockLevelRow(
    @SerialName("stock_item_id") val stockItemId: String,
    val quantity: Double = 0.0,
    val warehouses: WarehouseFlagsRow,
)

@Serializable
private data class WarehouseFlagsRow(
    @SerialName("is_quarantine") val isQuarantine: Boolean = false,
    @SerialName("is_active") val isActive: Boolean = true,
)

@Serializable
private data class PriceListRow(
    val id: String,
    val currency: String = "USD",
)

@Serializable
private data class PriceListItemRow(
    @SerialName("stock_item_id") val stockItemId: String,
    @SerialName("unit_price") val unitPrice: Double? = null,
    @SerialName("core_charge") val coreCharge: Double = 0.0,
)

@Serializable
private data class FitmentLabelRow(
    @SerialName("chassis_code") val chassisCode: String? = null,
    @SerialName("engine_code") val engineCode: String? = null,
    @SerialName("pnc_code") val pncCode: String? = null,
)

@Serializable
private data class FitmentMetaRow(
    @SerialName("chassis_code") val chassisCode: String? = null,
    @SerialName("engine_code") val engineCode: String? = null,
    @SerialName("pnc_code") val pncCode: String? = null,
    @SerialName("pnc_categories") val pncCategories: PncCategoryEmbed? = null,
)

@Serializable
private data class PncCategoryEmbed(
    @SerialName("category_name") val categoryName: String? = null,
    @SerialName("subcategory_name") val subcategoryName: String? = null,
)

@Serializable
private data class FitmentCategoryRow(
    @SerialName("oem_part_number") val oemPartNumber: String,
    @SerialName("pnc_categories") val pncCategories: PncCategoryEmbed? = null,
)

@Serializable
private data class PncCodeRow(
    @SerialName("pnc_code") val pncCode: String,
)

@Serializable
private data class OemOnlyRow(
    @SerialName("oem_part_number") val oemPartNumber: String,
)

@Serializable
private data class OeNumberRow(
    @SerialName("oe_number") val oeNumber: String? = null,
)

@Serializable
private data class SupersededRow(
    @SerialName("superseded_by") val supersededBy: String? = null,
)

@Serializable
private data class DiagramPathRow(
    @SerialName("diagram_path") val diagramPath: String? = null,
)

private data class PriceRow(
    val unitPrice: Double?,
    val coreCharge: Double,
    val currency: String,
)
