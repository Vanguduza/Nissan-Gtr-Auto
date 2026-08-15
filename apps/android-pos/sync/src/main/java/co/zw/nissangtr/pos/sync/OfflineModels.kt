package co.zw.nissangtr.pos.sync

import co.zw.nissangtr.pos.api.TillItem
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OfflineSnapshot(
    @SerialName("warehouse_id") val warehouseId: String,
    @SerialName("pulled_at") val pulledAt: String,
    val currency: String = "USD",
    val items: List<TillItem> = emptyList(),
)

enum class OutboxStatus {
    PENDING,
    CONFLICT,
    FAILED,
    SYNCED,
}

@Serializable
data class OfflineSaleLine(
    @SerialName("stock_item_id") val stockItemId: String?,
    @SerialName("oem_part_number") val oemPartNumber: String,
    @SerialName("uom_id") val uomId: String? = null,
    val qty: Int,
    @SerialName("expected_unit_price") val expectedUnitPrice: Double,
)

@Serializable
data class OfflineSaleOutboxRow(
    @SerialName("client_sale_id") val clientSaleId: String,
    @SerialName("warehouse_id") val warehouseId: String,
    val currency: String,
    @SerialName("device_id") val deviceId: String,
    val lines: List<OfflineSaleLine>,
    /** Applied cash amount as major string (change never queued). */
    @SerialName("cash_amount") val cashAmount: String,
    @SerialName("sold_at") val soldAt: String,
    val status: OutboxStatus = OutboxStatus.PENDING,
    @SerialName("conflict_code") val conflictCode: String? = null,
    @SerialName("invoice_id") val invoiceId: String? = null,
)

data class SyncBannerState(
    val online: Boolean,
    val snapshotAt: String? = null,
    val syncingCount: Int = 0,
    val conflictCount: Int = 0,
) {
    val label: String
        get() = buildString {
            append(if (online) "Online" else "Offline")
            snapshotAt?.let { append(" · snap $it") }
            if (syncingCount > 0) append(" · Syncing $syncingCount")
            if (conflictCount > 0) append(" · Conflict $conflictCount")
        }
}
