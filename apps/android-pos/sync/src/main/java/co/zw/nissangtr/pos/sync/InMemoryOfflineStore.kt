package co.zw.nissangtr.pos.sync

import co.zw.nissangtr.pos.api.OemNormalize
import co.zw.nissangtr.pos.api.TillItem

/** In-memory store for Fake / JVM unit tests (no SQLCipher native). */
class InMemoryOfflineStore : OfflineStore {
    private var snapshot: OfflineSnapshot? = null
    private val items = linkedMapOf<String, TillItem>()
    private val outbox = linkedMapOf<String, OfflineSaleOutboxRow>()

    override fun replaceCatalog(snapshot: OfflineSnapshot) {
        this.snapshot = snapshot
        items.clear()
        snapshot.items.forEach { item ->
            items[OemNormalize.normalize(item.oemPartNumber)] = item
        }
    }

    override fun getSnapshotMeta(): OfflineSnapshot? = snapshot?.copy(items = emptyList())

    override fun listCatalog(): List<TillItem> = items.values.toList()

    override fun findByOem(oemNormalized: String): TillItem? =
        items[OemNormalize.normalize(oemNormalized)]

    override fun decrementQty(stockItemId: String?, oem: String, qty: Int): Boolean {
        val key = OemNormalize.normalize(oem)
        val cur = items[key] ?: return false
        if (cur.saleableQty < qty) return false
        items[key] = cur.copy(saleableQty = cur.saleableQty - qty)
        return true
    }

    override fun restoreQty(stockItemId: String?, oem: String, qty: Int) {
        val key = OemNormalize.normalize(oem)
        val cur = items[key] ?: return
        items[key] = cur.copy(saleableQty = cur.saleableQty + qty)
    }

    override fun enqueueSale(row: OfflineSaleOutboxRow) {
        outbox[row.clientSaleId] = row
    }

    override fun listOutbox(statuses: Set<OutboxStatus>): List<OfflineSaleOutboxRow> =
        outbox.values.filter { it.status in statuses }

    override fun updateOutbox(row: OfflineSaleOutboxRow) {
        outbox[row.clientSaleId] = row
    }
}
