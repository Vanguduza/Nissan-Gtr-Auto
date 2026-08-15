package co.zw.nissangtr.pos.sync

import java.util.UUID

/**
 * Remote face for offline sync — Fake/Live PosClient implements this.
 */
interface OfflineSyncRemote {
    suspend fun pullOfflineSnapshot(warehouseId: String): OfflineSnapshot

    /**
     * Idempotent on [clientSaleId]. Returns invoice id.
     * Throws [OfflineConflictException] for price/stock conflicts (row stays conflict).
     */
    suspend fun replayOfflineSale(
        clientSaleId: String,
        row: OfflineSaleOutboxRow,
    ): ReplayResult
}

data class ReplayResult(
    val invoiceId: String,
    val duplicate: Boolean = false,
)

class OfflineConflictException(
    val code: String,
    message: String,
) : Exception(message)

/**
 * Sync order is locked: **replay outbox first → then pull snapshot**.
 * Never invents journal entries; conflicts stay in outbox.
 */
class PosSyncManager(
    private val store: OfflineStore,
    private val remote: OfflineSyncRemote,
) {
    var lastCallOrder: List<String> = emptyList()
        private set

    fun banner(online: Boolean): SyncBannerState {
        val meta = store.getSnapshotMeta()
        return SyncBannerState(
            online = online,
            snapshotAt = meta?.pulledAt,
            syncingCount = store.pendingCount(),
            conflictCount = store.conflictCount(),
        )
    }

    /**
     * Queue a cash-only offline sale (sellable lines). Decrements local qty.
     */
    fun recordOfflineCashSale(
        warehouseId: String,
        deviceId: String,
        currency: String,
        lines: List<OfflineSaleLine>,
        cashAmountMajor: String,
        soldAtIso: String,
        clientSaleId: String = UUID.randomUUID().toString(),
    ): OfflineSaleOutboxRow {
        require(lines.isNotEmpty()) { "lines required" }
        for (line in lines) {
            val ok = store.decrementQty(line.stockItemId, line.oemPartNumber, line.qty)
            require(ok) { "insufficient local qty for ${line.oemPartNumber}" }
        }
        val row = OfflineSaleOutboxRow(
            clientSaleId = clientSaleId,
            warehouseId = warehouseId,
            currency = currency,
            deviceId = deviceId,
            lines = lines,
            cashAmount = cashAmountMajor,
            soldAt = soldAtIso,
            status = OutboxStatus.PENDING,
        )
        store.enqueueSale(row)
        return row
    }

    /**
     * Drain pending outbox, then pull snapshot. Call order is recorded for tests.
     */
    suspend fun syncNow(warehouseId: String): SyncBannerState {
        val order = mutableListOf<String>()
        replayOutbox(order)
        order += "pull"
        val snapshot = remote.pullOfflineSnapshot(warehouseId)
        store.replaceCatalog(snapshot)
        lastCallOrder = order.toList()
        return banner(online = true)
    }

    private suspend fun replayOutbox(order: MutableList<String>) {
        val pending = store.listOutbox(setOf(OutboxStatus.PENDING))
        for (row in pending) {
            order += "replay:${row.clientSaleId}"
            try {
                val result = remote.replayOfflineSale(row.clientSaleId, row)
                store.updateOutbox(
                    row.copy(
                        status = OutboxStatus.SYNCED,
                        invoiceId = result.invoiceId,
                        conflictCode = null,
                    ),
                )
            } catch (e: OfflineConflictException) {
                for (line in row.lines) {
                    store.restoreQty(line.stockItemId, line.oemPartNumber, line.qty)
                }
                store.updateOutbox(
                    row.copy(
                        status = OutboxStatus.CONFLICT,
                        conflictCode = e.code,
                    ),
                )
            }
        }
    }
}
