package co.zw.nissangtr.pos.sync

import co.zw.nissangtr.pos.api.TillItem

/**
 * Local offline catalog + outbox. Never stores manager tokens or password hashes.
 */
interface OfflineStore {
    fun replaceCatalog(snapshot: OfflineSnapshot)

    fun getSnapshotMeta(): OfflineSnapshot?

    fun listCatalog(): List<TillItem>

    fun findByOem(oemNormalized: String): TillItem?

    fun decrementQty(stockItemId: String?, oem: String, qty: Int): Boolean

    fun restoreQty(stockItemId: String?, oem: String, qty: Int)

    fun enqueueSale(row: OfflineSaleOutboxRow)

    fun listOutbox(statuses: Set<OutboxStatus> = setOf(OutboxStatus.PENDING, OutboxStatus.CONFLICT)): List<OfflineSaleOutboxRow>

    fun updateOutbox(row: OfflineSaleOutboxRow)

    fun pendingCount(): Int = listOutbox(setOf(OutboxStatus.PENDING)).size

    fun conflictCount(): Int = listOutbox(setOf(OutboxStatus.CONFLICT)).size
}
