package co.zw.nissangtr.management.pos.offline

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** In-memory store for unit tests and Fake demos (no SQLCipher). */
class InMemoryOfflinePosStore : OfflinePosStore {
    private val catalog = ConcurrentHashMap<String, MutableList<LocalCatalogItem>>()
    private val pulledAt = ConcurrentHashMap<String, Long>()
    private val sales = ConcurrentHashMap<String, PendingOfflineSale>()

    override fun replaceCatalog(
        warehouseId: String,
        pulledAtEpochMs: Long,
        items: List<LocalCatalogItem>,
    ) {
        catalog[warehouseId] = items.toMutableList()
        pulledAt[warehouseId] = pulledAtEpochMs
    }

    override fun catalogFor(warehouseId: String): List<LocalCatalogItem> =
        catalog[warehouseId]?.toList().orEmpty()

    override fun searchCatalog(warehouseId: String, query: String): List<LocalCatalogItem> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        return catalogFor(warehouseId).filter {
            it.oemPartNumber.lowercase().contains(q) ||
                (it.description?.lowercase()?.contains(q) == true)
        }
    }

    override fun adjustSaleableQty(
        warehouseId: String,
        stockItemId: String,
        delta: Double,
    ): Boolean {
        val list = catalog[warehouseId] ?: return false
        val idx = list.indexOfFirst { it.stockItemId == stockItemId }
        if (idx < 0) return false
        val item = list[idx]
        val next = item.saleableQty + delta
        if (next < -0.0001) return false
        list[idx] = item.copy(saleableQty = next.coerceAtLeast(0.0))
        return true
    }

    override fun enqueueSale(sale: PendingOfflineSale) {
        sales[sale.clientSaleId] = sale
    }

    override fun pendingSales(includeTerminal: Boolean): List<PendingOfflineSale> =
        sales.values
            .filter {
                includeTerminal ||
                    it.status == PendingSaleStatus.Pending ||
                    it.status == PendingSaleStatus.Syncing ||
                    it.status == PendingSaleStatus.Conflict ||
                    it.status == PendingSaleStatus.Failed
            }
            .sortedBy { it.soldAtEpochMs }

    override fun markSaleStatus(
        clientSaleId: String,
        status: PendingSaleStatus,
        error: String?,
        serverInvoiceId: String?,
    ) {
        val cur = sales[clientSaleId] ?: return
        sales[clientSaleId] = cur.copy(
            status = status,
            lastError = error,
            serverInvoiceId = serverInvoiceId ?: cur.serverInvoiceId,
        )
    }

    override fun lastPulledAtEpochMs(warehouseId: String): Long? = pulledAt[warehouseId]

    override fun close() {
        catalog.clear()
        sales.clear()
        pulledAt.clear()
    }

    companion object {
        fun newClientSaleId(): String = UUID.randomUUID().toString()
    }
}
