package co.zw.nissangtr.customer.rpc

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Loyalty, returns CN, and kits — mirrors web customer-storefront.ts helpers.
 */
internal object CommerceRpcLive {
    suspend fun getLoyaltyBalance(client: SupabaseClient, customerId: String): LoyaltyBalance {
        require(customerId.isNotBlank()) { "customerId required" }
        val rows = client.postgrest.rpc(
            RpcNames.GET_LOYALTY_BALANCE,
            buildJsonObject { put("p_customer_id", customerId) },
        ).decodeList<LoyaltyBalanceRow>()
        val row = rows.firstOrNull() ?: error("get_loyalty_balance returned no row")
        return row.toModel(customerId)
    }

    suspend fun postCustomerReturnCreditNote(
        client: SupabaseClient,
        invoiceId: String,
        lines: List<ReturnCreditNoteLine>,
    ): String {
        require(invoiceId.isNotBlank()) { "invoiceId required" }
        require(lines.isNotEmpty()) { "return lines required" }
        val payload = buildJsonArray {
            lines.forEach { line ->
                add(
                    buildJsonObject {
                        put("stock_item_id", line.stockItemId)
                        put("uom_id", line.uomId)
                        put("qty", line.qty)
                    },
                )
            }
        }
        return client.postgrest.rpc(
            RpcNames.POST_CUSTOMER_RETURN_CREDIT_NOTE,
            buildJsonObject {
                put("p_invoice_id", invoiceId)
                put("p_lines", payload)
            },
        ).decodeAs<String>()
    }

    /** PostgREST read — mirrors web `listActiveKits` (no RPC). */
    suspend fun listActiveKits(client: SupabaseClient, limit: Int = 50): List<KitListItem> {
        val cap = limit.coerceIn(1, 50)
        val kits = client.from("item_kits")
            .select(
                Columns.raw(
                    "id, sell_mode, stock_item_id, stock_items ( oem_part_number, description )",
                ),
            ) {
                filter { eq("is_active", true) }
                order("created_at", Order.DESCENDING)
                limit(cap.toLong())
            }
            .decodeList<ItemKitRow>()
        if (kits.isEmpty()) return emptyList()

        val kitIds = kits.map { it.id }
        val comps = client.from("item_kit_components")
            .select(
                Columns.raw(
                    "kit_id, qty, stock_items:component_item_id ( oem_part_number, description )",
                ),
            ) {
                filter { isIn("kit_id", kitIds) }
            }
            .decodeList<KitComponentRow>()

        val byKit = mutableMapOf<String, MutableList<KitComponent>>()
        for (c in comps) {
            val item = c.stockItems
            val list = byKit.getOrPut(c.kitId) { mutableListOf() }
            list.add(
                KitComponent(
                    oem = item?.oemPartNumber ?: "—",
                    name = item?.description?.trim().orEmpty().ifEmpty {
                        item?.oemPartNumber ?: "Component"
                    },
                    qty = c.qty,
                ),
            )
        }

        return kits.map { k ->
            val item = k.stockItems
            KitListItem(
                kitId = k.id,
                stockItemId = k.stockItemId,
                oem = item?.oemPartNumber ?: k.stockItemId,
                name = item?.description?.trim().orEmpty().ifEmpty {
                    item?.oemPartNumber ?: "Kit"
                },
                sellMode = k.sellMode,
                components = byKit[k.id] ?: emptyList(),
            )
        }
    }
}

@Serializable
private data class LoyaltyBalanceRow(
    @SerialName("customer_id") val customerId: String? = null,
    @SerialName("points_balance") val pointsBalance: Double = 0.0,
    val currency: String = "USD",
    @SerialName("liability_per_point") val liabilityPerPoint: Double = 0.0,
    @SerialName("estimated_liability") val estimatedLiability: Double = 0.0,
) {
    fun toModel(fallbackCustomerId: String) = LoyaltyBalance(
        customerId = customerId ?: fallbackCustomerId,
        pointsBalance = pointsBalance,
        currency = currency,
        liabilityPerPoint = liabilityPerPoint,
        estimatedLiability = estimatedLiability,
    )
}

@Serializable
private data class ItemKitRow(
    val id: String,
    @SerialName("sell_mode") val sellMode: String,
    @SerialName("stock_item_id") val stockItemId: String,
    @SerialName("stock_items") val stockItems: StockItemBriefEmbed? = null,
)

@Serializable
private data class KitComponentRow(
    @SerialName("kit_id") val kitId: String,
    val qty: Double = 1.0,
    @SerialName("stock_items") val stockItems: StockItemBriefEmbed? = null,
)

@Serializable
private data class StockItemBriefEmbed(
    @SerialName("oem_part_number") val oemPartNumber: String? = null,
    val description: String? = null,
)
