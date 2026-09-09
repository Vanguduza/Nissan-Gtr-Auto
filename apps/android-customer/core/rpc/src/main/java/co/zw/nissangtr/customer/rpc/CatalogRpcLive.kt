package co.zw.nissangtr.customer.rpc

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Customer catalog transport.
 *
 * Rules:
 * - vehicle cascade comes from the approved catalog_v2 release, never the smoke vehicle_master table;
 * - vehicle-specific stock is resolved through EPC-derived customer fitment indexes;
 * - customer product imagery comes only from stock_item_images / product-images;
 * - EPC diagrams remain technical/staff data and are never merchandise photography.
 */
internal object CatalogRpcLive {
    @Serializable
    private data class StockItemImageBrowseRow(
        @SerialName("stock_item_id") val stockItemId: String,
        @SerialName("storage_path") val storagePath: String,
        @SerialName("is_primary") val isPrimary: Boolean = false,
        @SerialName("sort_order") val sortOrder: Int = 0,
    )

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
                            put("facets", JsonArray(facets.map { JsonPrimitive(it) }))
                        }
                    },
                )
            }
            val text = response.bodyAsText()
            val el = json.parseToJsonElement(text)
            val err = (el as? JsonObject)?.get("error")?.jsonPrimitive?.contentOrNull
            if (!err.isNullOrBlank()) searchCatalog(client, mode, trimmed)
            else parseSearchCatalogJson(el).let { parsed ->
                parsed.copy(backend = parsed.backend ?: "meili")
            }
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
        val categories = CatalogCategoryFilter.facetLabels(cat)
        val oemFilter = if (cat != null) resolveOemFilterForCategory(client, cat) else null
        if (cat != null && oemFilter != null && oemFilter.isEmpty()) {
            return CatalogBrowseResult(emptyList(), categories)
        }

        val items = client.from("stock_items")
            .select(Columns.list("id", "oem_part_number", "description", "reorder_point")) {
                if (oemFilter != null) filter { isIn("oem_part_number", oemFilter) }
                order("description", Order.ASCENDING)
                limit(cap.toLong())
            }
            .decodeList<StockItemBrowseRow>()

        return hydrateBrowseItems(client, items, categories)
    }

    /**
     * Full cascade master from the approved catalog_v2 release.
     * The live dataset currently contains thousands of fitment-scoped rows, so there is no 500-row cap.
     */
    suspend fun listVehicleMaster(client: SupabaseClient): List<VehicleMasterRow> {
        val rows = client.postgrest.rpc(
            "list_customer_vehicle_master",
            buildJsonObject {
                put("p_maker", "nissan")
                put("p_limit", 10000)
                put("p_offset", 0)
            },
        ).decodeList<CustomerVehicleMasterRpcRow>()

        return rows.map { row ->
            VehicleMasterRow(
                id = row.id,
                vinPrefix = row.vinPrefix,
                chassisCode = row.chassisCode,
                engineCode = row.engineCode,
                productionYear = row.productionYear,
                modelVariant = row.modelVariant,
            )
        }
    }

    /**
     * Vehicle-specific shop stock is referenced against the EPC-derived customer fitment index.
     * If the exact index has not been published yet, the server may use the tiny legacy fitment set;
     * it never invents a fit.
     */
    suspend fun listCatalogForVehicle(
        client: SupabaseClient,
        chassisCode: String,
        engineCode: String?,
        limit: Int,
    ): CatalogBrowseResult {
        val chassis = chassisCode.trim()
        require(chassis.isNotEmpty()) { "chassis required" }
        val cap = limit.coerceIn(1, 100)

        val resolved = client.postgrest.rpc(
            "customer_catalog_stock_for_vehicle",
            buildJsonObject {
                put("p_chassis", chassis)
                engineCode?.trim()?.takeIf { it.isNotEmpty() }?.let { put("p_engine", it) }
                    ?: put("p_engine", JsonPrimitive(null as String?))
                put("p_limit", cap)
            },
        ).decodeList<CustomerVehicleStockRpcRow>()

        if (resolved.isEmpty()) return CatalogBrowseResult(emptyList(), emptyList())
        val oems = resolved.map { it.oemPartNumber }.distinct()
        val items = client.from("stock_items")
            .select(Columns.list("id", "oem_part_number", "description", "reorder_point")) {
                filter { isIn("oem_part_number", oems) }
                order("description", Order.ASCENDING)
                limit(cap.toLong())
            }
            .decodeList<StockItemBrowseRow>()
        return hydrateBrowseItems(client, items, emptyList())
    }

    suspend fun loadCatalogProduct(
        client: SupabaseClient,
        supabaseUrl: String,
        oemParam: String,
    ): CatalogProduct {
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
        val replaces = loadReplaces(client, item.oemPartNumber)
        val imageUrls = loadProductImageUrls(client, supabaseUrl, item.id)

        return CatalogProduct(
            stockItemId = item.id,
            baseUomId = uomId,
            oem = item.oemPartNumber,
            name = item.description?.trim().orEmpty().ifEmpty { "Nissan part" },
            brand = "Nissan",
            category = fitmentMeta.category,
            usd = price?.unitPrice,
            stock = stockStateFromQty(qty, item.reorderPoint),
            coreCharge = price?.coreCharge ?: 0.0,
            fitmentLines = emptyList(),
            imageUrls = imageUrls,
            diagramUrl = null,
            specs = emptyList(),
            replaces = replaces,
        )
    }

    suspend fun listCatalogMakers(client: SupabaseClient): List<EpcMaker> {
        val raw = client.postgrest.rpc(RpcNames.LIST_CATALOG_MAKERS).decodeAs<JsonElement>()
        return parseEpcMakerList(raw)
    }

    suspend fun listCatalogModels(client: SupabaseClient, makerSlug: String): List<EpcModel> {
        val raw = client.postgrest.rpc(
            RpcNames.LIST_CATALOG_MODELS,
            buildJsonObject { put("p_maker_slug", makerSlug) },
        ).decodeAs<JsonElement>()
        return parseEpcModelList(raw)
    }

    suspend fun listCatalogVariants(
        client: SupabaseClient,
        makerSlug: String,
        modelSlug: String,
    ): List<EpcVariant> {
        val raw = client.postgrest.rpc(
            RpcNames.LIST_CATALOG_VARIANTS,
            buildJsonObject {
                put("p_maker_slug", makerSlug)
                put("p_model_slug", modelSlug)
            },
        ).decodeAs<JsonElement>()
        return parseEpcVariantList(raw)
    }

    suspend fun listCatalogSections(
        client: SupabaseClient,
        makerSlug: String,
        modelSlug: String,
        variantSlug: String,
    ): List<EpcSection> {
        val raw = client.postgrest.rpc(
            RpcNames.LIST_CATALOG_SECTIONS,
            buildJsonObject {
                put("p_maker_slug", makerSlug)
                put("p_model_slug", modelSlug)
                put("p_variant_slug", variantSlug)
            },
        ).decodeAs<JsonElement>()
        return parseEpcSectionList(raw)
    }

    /**
     * Retained for internal/staff compatibility only. Customer UI must not mount EPC browsing.
     * R2 diagram delivery is handled by catalog-v2-serve in the staff/management module.
     */
    suspend fun getCatalogDiagram(
        client: SupabaseClient,
        supabaseUrl: String,
        makerSlug: String,
        modelSlug: String,
        variantSlug: String,
        sectionSlug: String,
    ): EpcDiagramResponse {
        val raw = client.postgrest.rpc(
            RpcNames.GET_CATALOG_DIAGRAM,
            buildJsonObject {
                put("p_maker_slug", makerSlug)
                put("p_model_slug", modelSlug)
                put("p_variant_slug", variantSlug)
                put("p_section_slug", sectionSlug)
            },
        ).decodeAs<JsonElement>()
        return parseEpcDiagram(raw).copy(imageUrl = null)
    }

    suspend fun addCartLineByOem(
        client: SupabaseClient,
        supabaseUrl: String,
        rpc: RpcClient,
        oem: String,
        qty: Double,
    ): Pair<String, String> {
        require(qty > 0) { "qty must be > 0" }
        val product = loadCatalogProduct(client, supabaseUrl, oem)
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

    private suspend fun hydrateBrowseItems(
        client: SupabaseClient,
        items: List<StockItemBrowseRow>,
        categories: List<String>,
    ): CatalogBrowseResult {
        if (items.isEmpty()) return CatalogBrowseResult(emptyList(), categories)
        val ids = items.map { it.id }
        val oems = items.map { it.oemPartNumber }
        val priceByItem = loadDefaultPrices(client, ids)
        val qtyByItem = loadSaleableQty(client, ids)
        val catByOem = loadCategoryByOem(client, oems)
        val imageByItem = loadPrimaryImagePathByItem(client, ids)
        return CatalogBrowseResult(
            items = items.map { row ->
                val price = priceByItem[row.id]
                CatalogListItem(
                    stockItemId = row.id,
                    oem = row.oemPartNumber,
                    name = row.description?.trim().orEmpty().ifEmpty { "Nissan part" },
                    stock = stockStateFromQty(qtyByItem[row.id] ?: 0.0, row.reorderPoint),
                    usd = price?.unitPrice,
                    category = catByOem[row.oemPartNumber],
                    imagePath = imageByItem[row.id],
                )
            },
            categories = categories,
        )
    }

    private suspend fun resolveOemFilterForCategory(
        client: SupabaseClient,
        categoryLabel: String,
    ): List<String>? {
        val cat = categoryLabel.trim()
        if (cat.isEmpty()) return null
        val pncRows = client.from("pnc_categories")
            .select(Columns.list("pnc_code", "category_name", "subcategory_name")) { limit(2000) }
            .decodeList<PncCategoryMatchRow>()
        val codes = pncRows
            .filter { CatalogCategoryFilter.matches(cat, it.categoryName, it.subcategoryName) }
            .map { it.pncCode }
            .filter { it.isNotBlank() }
        if (codes.isEmpty()) return emptyList()
        val fits = client.from("part_fitment")
            .select(Columns.list("oem_part_number")) {
                filter { isIn("pnc_code", codes) }
                limit(500)
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
                limit(500)
            }
            .decodeList<FitmentCategoryRow>()
        return buildMap {
            for (row in rows) {
                val name = row.pncCategories?.categoryName?.trim().orEmpty()
                if (name.isNotEmpty() && !containsKey(row.oemPartNumber)) put(row.oemPartNumber, name)
            }
        }
    }

    private data class FitmentMeta(val category: String? = null)

    private suspend fun loadFitmentMeta(client: SupabaseClient, oem: String): FitmentMeta {
        val rows = client.from("part_fitment")
            .select(Columns.raw("oem_part_number, pnc_categories ( category_name, subcategory_name )")) {
                filter { eq("oem_part_number", oem) }
                limit(1)
            }
            .decodeList<FitmentCategoryRow>()
        val primary = rows.firstOrNull()?.pncCategories
        return FitmentMeta(
            category = primary?.categoryName?.trim()?.takeIf { it.isNotEmpty() }
                ?: primary?.subcategoryName?.trim()?.takeIf { it.isNotEmpty() },
        )
    }

    private suspend fun loadPrimaryImagePathByItem(
        client: SupabaseClient,
        stockItemIds: List<String>,
    ): Map<String, String> {
        if (stockItemIds.isEmpty()) return emptyMap()
        val rows = client.from("stock_item_images")
            .select(Columns.list("stock_item_id", "storage_path", "is_primary", "sort_order")) {
                filter { isIn("stock_item_id", stockItemIds) }
                order("is_primary", Order.DESCENDING)
                order("sort_order", Order.ASCENDING)
                limit((stockItemIds.size * 8).coerceAtMost(800).toLong())
            }
            .decodeList<StockItemImageBrowseRow>()
        return buildMap {
            for (row in rows) {
                val path = row.storagePath.trim().trimStart('/')
                if (path.isNotEmpty() && !containsKey(row.stockItemId)) put(row.stockItemId, path)
            }
        }
    }

    private suspend fun loadProductImageUrls(
        client: SupabaseClient,
        supabaseUrl: String,
        stockItemId: String,
    ): List<String> {
        val rows = client.from("stock_item_images")
            .select(Columns.list("storage_path", "is_primary", "sort_order")) {
                filter { eq("stock_item_id", stockItemId) }
                order("is_primary", Order.DESCENDING)
                order("sort_order", Order.ASCENDING)
                limit(20)
            }
            .decodeList<StockItemImageRow>()
        val base = supabaseUrl.trimEnd('/')
        return rows.mapNotNull { row ->
            val path = row.storagePath.trim()
            if (path.isEmpty()) null
            else if (path.startsWith("https://", true) || path.startsWith("http://", true)) path
            else "$base/storage/v1/object/public/product-images/${path.trimStart('/')}"
        }.distinct()
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
            .firstOrNull()?.id ?: error("no active warehouse")
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
            if (row.warehouses.isQuarantine || !row.warehouses.isActive) continue
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
            row.stockItemId to PriceRow(row.unitPrice, row.coreCharge, list.currency)
        }
    }
}

@Serializable
private data class IdRow(val id: String)

@Serializable
private data class CustomerVehicleMasterRpcRow(
    val id: String,
    val make: String,
    @SerialName("model_variant") val modelVariant: String,
    @SerialName("chassis_code") val chassisCode: String,
    @SerialName("engine_code") val engineCode: String? = null,
    @SerialName("production_year") val productionYear: Int? = null,
    @SerialName("vin_prefix") val vinPrefix: String? = null,
)

@Serializable
private data class CustomerVehicleStockRpcRow(
    @SerialName("stock_item_id") val stockItemId: String,
    @SerialName("oem_part_number") val oemPartNumber: String,
    val name: String,
    @SerialName("fitment_evidence") val fitmentEvidence: String,
    @SerialName("catalog_release_id") val catalogReleaseId: String? = null,
)

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
private data class StockItemImageRow(
    @SerialName("storage_path") val storagePath: String,
    @SerialName("is_primary") val isPrimary: Boolean = false,
    @SerialName("sort_order") val sortOrder: Int = 0,
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
private data class PriceListRow(val id: String, val currency: String = "USD")

@Serializable
private data class PriceListItemRow(
    @SerialName("stock_item_id") val stockItemId: String,
    @SerialName("unit_price") val unitPrice: Double? = null,
    @SerialName("core_charge") val coreCharge: Double = 0.0,
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
private data class PncCategoryMatchRow(
    @SerialName("pnc_code") val pncCode: String,
    @SerialName("category_name") val categoryName: String? = null,
    @SerialName("subcategory_name") val subcategoryName: String? = null,
)

@Serializable
private data class OemOnlyRow(@SerialName("oem_part_number") val oemPartNumber: String)

@Serializable
private data class OeNumberRow(@SerialName("oe_number") val oeNumber: String? = null)

@Serializable
private data class SupersededRow(@SerialName("superseded_by") val supersededBy: String? = null)

private data class PriceRow(
    val unitPrice: Double?,
    val coreCharge: Double,
    val currency: String,
)
