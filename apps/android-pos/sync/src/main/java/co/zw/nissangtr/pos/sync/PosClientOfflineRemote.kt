package co.zw.nissangtr.pos.sync

import co.zw.nissangtr.pos.api.OfflineReplayConflict
import co.zw.nissangtr.pos.api.OfflineSnapshotDto
import co.zw.nissangtr.pos.api.PosClient

/** Adapts [PosClient] to [OfflineSyncRemote] for [PosSyncManager]. */
class PosClientOfflineRemote(
    private val client: PosClient,
) : OfflineSyncRemote {
    override suspend fun pullOfflineSnapshot(warehouseId: String): OfflineSnapshot {
        val dto: OfflineSnapshotDto = client.pullOfflineSnapshot(warehouseId)
        return OfflineSnapshot(
            warehouseId = dto.warehouseId,
            pulledAt = dto.pulledAt,
            currency = dto.currency,
            items = dto.items,
        )
    }

    override suspend fun replayOfflineSale(
        clientSaleId: String,
        row: OfflineSaleOutboxRow,
    ): ReplayResult {
        return try {
            val result = client.replayOfflineSale(clientSaleId, row.clientSaleId)
            ReplayResult(invoiceId = result.invoiceId, duplicate = result.duplicate)
        } catch (e: OfflineReplayConflict) {
            throw OfflineConflictException(e.code, e.message ?: e.code)
        }
    }
}
