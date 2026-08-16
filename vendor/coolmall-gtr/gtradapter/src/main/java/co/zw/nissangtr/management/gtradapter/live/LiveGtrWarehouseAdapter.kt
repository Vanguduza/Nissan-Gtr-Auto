package co.zw.nissangtr.management.gtradapter.live

import co.zw.nissangtr.management.gtradapter.GtrWarehouseAdapter
import co.zw.nissangtr.management.gtradapter.MasterStockRow
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Live master-stock — web RPC `list_master_stock` (OEM query + optional chassis).
 * Category needle filters stay Phase D deepen / later B3 polish.
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
