package co.zw.nissangtr.management.gtradapter.live

import co.zw.nissangtr.management.gtradapter.GtrPendingTransfer
import co.zw.nissangtr.management.gtradapter.GtrReceiptLine
import co.zw.nissangtr.management.gtradapter.GtrStockItemOption
import co.zw.nissangtr.management.gtradapter.GtrTransferLine
import co.zw.nissangtr.management.gtradapter.GtrWarehouseAdapter
import co.zw.nissangtr.management.gtradapter.GtrWarehouseOption
import co.zw.nissangtr.management.gtradapter.MasterStockRow
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Live warehouse — web `staff-warehouse.ts`.
 * Receive/transfer QR stays Bridge-First (typed OEM this slice).
 */
class LiveGtrWarehouseAdapter(
    private val client: SupabaseClient,
) : GtrWarehouseAdapter {
    override suspend fun listMasterStock(
        limit: Int,
        query: String?,
        chassisCode: String?,
    ): Result<List<MasterStockRow>> = runCatching {
        val rows = client.postgrest.rpc(
            "list_master_stock",
            buildJsonObject {
                put("p_limit", limit.coerceIn(1, 500))
                val q = query?.trim().orEmpty()
                if (q.isEmpty()) put("p_query", JsonNull) else put("p_query", q)
                val chassis = chassisCode?.trim().orEmpty()
                if (chassis.isEmpty()) put("p_chassis_code", JsonNull)
                else put("p_chassis_code", chassis)
            },
        ).decodeList<MasterStockRpcRow>()
        rows.map { it.toDomain() }
    }

    override suspend fun listWarehouses(
        includeQuarantine: Boolean,
    ): Result<List<GtrWarehouseOption>> = runCatching {
        client.from("warehouses")
            .select(
                Columns.list("id", "code", "name", "role_code", "is_quarantine", "is_active"),
            ) {
                filter {
                    eq("is_active", true)
                    if (!includeQuarantine) eq("is_quarantine", false)
                }
                order("code", Order.ASCENDING)
                limit(50)
            }
            .decodeList<WarehouseRow>()
            .map { it.toDomain() }
    }

    override suspend fun searchStockItems(
        query: String,
        limit: Int,
    ): Result<List<GtrStockItemOption>> = runCatching {
        val q = query.trim()
        if (q.length < 2) return@runCatching emptyList()
        val capped = limit.coerceIn(1, 50)
        val uuid = UUID_REGEX.matches(q)
        if (uuid) {
            return@runCatching client.from("stock_items")
                .select(STOCK_ITEM_COLS) {
                    filter { eq("id", q) }
                    limit(1)
                }
                .decodeList<StockItemSearchRow>()
                .map { it.toDomain() }
        }
        val byOem = client.from("stock_items")
            .select(STOCK_ITEM_COLS) {
                filter { ilike("oem_part_number", "%$q%") }
                limit(capped.toLong())
            }
            .decodeList<StockItemSearchRow>()
        if (byOem.size >= capped) return@runCatching byOem.map { it.toDomain() }
        val seen = byOem.map { it.id }.toMutableSet()
        val byDesc = client.from("stock_items")
            .select(STOCK_ITEM_COLS) {
                filter { ilike("description", "%$q%") }
                limit(capped.toLong())
            }
            .decodeList<StockItemSearchRow>()
            .filter { it.id !in seen }
        (byOem + byDesc).take(capped).map { it.toDomain() }
    }

    override suspend fun postStockReceipt(
        toWarehouseId: String,
        notes: String,
        lines: List<GtrReceiptLine>,
    ): Result<String> = runCatching {
        require(toWarehouseId.isNotBlank())
        require(lines.isNotEmpty()) { "Receipt needs at least one line" }
        client.postgrest.rpc(
            "post_stock_receipt",
            buildJsonObject {
                put("p_to_warehouse_id", toWarehouseId)
                val n = notes.trim()
                if (n.isEmpty()) put("p_notes", JsonNull) else put("p_notes", n)
                put("p_lines", lines.toReceiptJson())
            },
        ).decodeAs<String>()
    }

    override suspend fun listPendingTransfers(): Result<List<GtrPendingTransfer>> = runCatching {
        client.from("stock_entries")
            .select(
                Columns.list(
                    "id",
                    "document_number",
                    "status",
                    "from_warehouse_id",
                    "to_warehouse_id",
                    "notes",
                    "created_at",
                ),
            ) {
                filter {
                    eq("entry_type", "transfer")
                    eq("status", "pending_approval")
                }
                order("created_at", Order.DESCENDING)
                limit(40)
            }
            .decodeList<StockEntryRow>()
            .map { it.toDomain() }
    }

    override suspend fun createStockTransfer(
        fromWarehouseId: String,
        toWarehouseId: String,
        notes: String,
        lines: List<GtrTransferLine>,
    ): Result<String> = runCatching {
        require(fromWarehouseId.isNotBlank() && toWarehouseId.isNotBlank())
        require(fromWarehouseId != toWarehouseId) { "From and to warehouses must differ" }
        require(lines.isNotEmpty()) { "Transfer needs at least one line" }
        client.postgrest.rpc(
            "create_stock_transfer",
            buildJsonObject {
                put("p_from_warehouse_id", fromWarehouseId)
                put("p_to_warehouse_id", toWarehouseId)
                val n = notes.trim()
                if (n.isEmpty()) put("p_notes", JsonNull) else put("p_notes", n)
                put("p_lines", lines.toTransferJson())
            },
        ).decodeAs<String>()
    }

    override suspend fun approveStockTransfer(entryId: String): Result<String> = runCatching {
        require(entryId.isNotBlank())
        client.postgrest.rpc(
            "approve_stock_transfer",
            buildJsonObject { put("p_entry_id", entryId) },
        ).decodeAs<String>()
    }

    override suspend fun rejectStockTransfer(entryId: String): Result<String> = runCatching {
        require(entryId.isNotBlank())
        client.postgrest.rpc(
            "reject_stock_transfer",
            buildJsonObject { put("p_entry_id", entryId) },
        ).decodeAs<String>()
    }

    private companion object {
        val STOCK_ITEM_COLS = Columns.list(
            "id",
            "oem_part_number",
            "description",
            "base_uom_id",
            "requires_serial",
        )
        val UUID_REGEX =
            Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
    }
}

private fun List<GtrReceiptLine>.toReceiptJson() = buildJsonArray {
    forEach { line ->
        val currency = if (line.currency == "ZIG") "ZIG" else "USD"
        add(
            buildJsonObject {
                put("stock_item_id", line.stockItemId)
                put("uom_id", line.uomId)
                put("qty", line.qty)
                put("unit_cost", line.unitCost)
                put("currency", currency)
                put("valuation_method", "FIFO")
            },
        )
    }
}

private fun List<GtrTransferLine>.toTransferJson() = buildJsonArray {
    forEach { line ->
        add(
            buildJsonObject {
                put("stock_item_id", line.stockItemId)
                put("uom_id", line.uomId)
                put("qty", line.qty)
                put("valuation_method", "FIFO")
            },
        )
    }
}

@Serializable
private data class MasterStockRpcRow(
    @SerialName("stock_item_id") val stockItemId: String,
    @SerialName("oem_part_number") val oemPartNumber: String = "",
    val description: String = "",
    @SerialName("qty_total") val qtyTotal: Double = 0.0,
    @SerialName("qty_wh1") val qtyWh1: Double = 0.0,
    @SerialName("qty_wh2") val qtyWh2: Double = 0.0,
) {
    fun toDomain() = MasterStockRow(
        stockItemId = stockItemId,
        oemPartNumber = oemPartNumber,
        description = description,
        qtyTotal = qtyTotal,
        qtyWh1 = qtyWh1,
        qtyWh2 = qtyWh2,
    )
}

@Serializable
private data class WarehouseRow(
    val id: String,
    val code: String,
    val name: String,
    @SerialName("role_code") val roleCode: String? = null,
    @SerialName("is_quarantine") val isQuarantine: Boolean = false,
    @SerialName("is_active") val isActive: Boolean = true,
) {
    fun toDomain() = GtrWarehouseOption(
        id = id,
        code = code,
        name = name,
        roleCode = roleCode,
        isQuarantine = isQuarantine,
    )
}

@Serializable
private data class StockItemSearchRow(
    val id: String,
    @SerialName("oem_part_number") val oemPartNumber: String = "",
    val description: String? = null,
    @SerialName("base_uom_id") val baseUomId: String? = null,
    @SerialName("requires_serial") val requiresSerial: Boolean = false,
) {
    fun toDomain() = GtrStockItemOption(
        id = id,
        oemPartNumber = oemPartNumber,
        description = description.orEmpty(),
        baseUomId = baseUomId,
        requiresSerial = requiresSerial,
    )
}

@Serializable
private data class StockEntryRow(
    val id: String,
    @SerialName("document_number") val documentNumber: String? = null,
    @SerialName("from_warehouse_id") val fromWarehouseId: String? = null,
    @SerialName("to_warehouse_id") val toWarehouseId: String? = null,
    val notes: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
) {
    fun toDomain() = GtrPendingTransfer(
        id = id,
        documentNumber = documentNumber,
        fromWarehouseId = fromWarehouseId,
        toWarehouseId = toWarehouseId,
        notes = notes,
        createdAt = createdAt,
    )
}
