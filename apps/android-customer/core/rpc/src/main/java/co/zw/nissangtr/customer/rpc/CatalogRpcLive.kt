package co.zw.nissangtr.customer.rpc

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Live catalog reads for [SupabaseRpcClient] — mirrors web catalog-search + catalog-product.
 */
internal object CatalogRpcLive {
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
        return parseSearchCatalogJson(raw)
    }

    suspend fun listCatalogBrowse(
        client: SupabaseClient,
        category: String?,
        limit: Int,
    ): CatalogBrowseResult {
        val cap = limit.coerceIn(1, 100)
        val items = client.from("stock_items")
            .select(Columns.list("id", "oem_part_number", "description", "reorder_point")) {
                order("oem_part_number", Order.ASCENDING)
                limit(cap.toLong())
            }
            .decodeList<StockItemBrowseRow>()

        if (items.isEmpty()) return CatalogBrowseResult(emptyList(), emptyList())

        val ids = items.map { it.id }
        val priceByItem = loadDefaultPrices(client, ids)
        val qtyByItem = loadSaleableQty(client, ids)

        val list = items.map { row ->
            val price = priceByItem[row.id]
            val usd = if (price?.currency == "USD") price.unitPrice else price?.unitPrice
            CatalogListItem(
                stockItemId = row.id,
                oem = row.oemPartNumber,
                name = row.description?.trim().orEmpty().ifEmpty { row.oemPartNumber },
                stock = stockStateFromQty(qtyByItem[row.id] ?: 0.0, row.reorderPoint),
                usd = usd,
                category = category,
            )
        }
        return CatalogBrowseResult(items = list, categories = emptyList())
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

        return CatalogProduct(
            stockItemId = item.id,
            baseUomId = uomId,
            oem = item.oemPartNumber,
            name = item.description?.trim().orEmpty().ifEmpty { item.oemPartNumber },
            usd = if (price?.currency == "USD") price.unitPrice else price?.unitPrice,
            stock = stockStateFromQty(qty, item.reorderPoint),
            coreCharge = price?.coreCharge ?: 0.0,
            fitmentLines = emptyList(),
        )
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

private data class PriceRow(
    val unitPrice: Double?,
    val coreCharge: Double,
    val currency: String,
)
