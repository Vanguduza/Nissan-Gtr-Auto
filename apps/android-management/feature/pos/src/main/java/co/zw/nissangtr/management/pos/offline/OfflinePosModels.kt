package co.zw.nissangtr.management.pos.offline

/**
 * Local encrypted outbox / catalog cache for tablet offline POS.
 * Privilege: never stores manager approval tokens (see ADR).
 */
enum class PendingSaleStatus {
    Pending,
    Syncing,
    Synced,
    Conflict,
    Failed,
}

data class LocalCatalogItem(
    val stockItemId: String,
    val oemPartNumber: String,
    val description: String?,
    val uomId: String,
    val unitPrice: Double,
    val coreCharge: Double,
    val saleableQty: Double,
    val currency: String,
)

data class LocalCartLine(
    val id: String,
    val stockItemId: String,
    val oemPartNumber: String,
    val uomId: String,
    val qty: Double,
    val unitPrice: Double,
    val lineTotal: Double,
    val isCoreCharge: Boolean = false,
)

data class PendingOfflineSale(
    val clientSaleId: String,
    val warehouseId: String,
    val currency: String,
    val exchangeRate: Double,
    val deviceId: String?,
    val linesJson: String,
    val tendersJson: String,
    val receiptEmail: String?,
    val receiptWhatsapp: String?,
    val receiptPhone: String?,
    val soldAtEpochMs: Long,
    val status: PendingSaleStatus,
    val lastError: String? = null,
    val serverInvoiceId: String? = null,
)

interface OfflinePosStore {
    fun replaceCatalog(warehouseId: String, pulledAtEpochMs: Long, items: List<LocalCatalogItem>)
    fun catalogFor(warehouseId: String): List<LocalCatalogItem>
    fun searchCatalog(warehouseId: String, query: String): List<LocalCatalogItem>
    fun adjustSaleableQty(warehouseId: String, stockItemId: String, delta: Double): Boolean
    fun enqueueSale(sale: PendingOfflineSale)
    fun pendingSales(includeTerminal: Boolean = false): List<PendingOfflineSale>
    fun markSaleStatus(
        clientSaleId: String,
        status: PendingSaleStatus,
        error: String? = null,
        serverInvoiceId: String? = null,
    )
    fun lastPulledAtEpochMs(warehouseId: String): Long?
    fun close()
}
