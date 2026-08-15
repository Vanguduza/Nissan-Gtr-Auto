package co.zw.nissangtr.pos.sync

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PosSyncManagerTest {

    @Test
    fun syncOrder_replayBeforePull() = runTest {
        val store = InMemoryOfflineStore()
        store.replaceCatalog(
            OfflineSnapshot(
                warehouseId = "wh2",
                pulledAt = "2026-08-15T00:00:00Z",
                items = emptyList(),
            ),
        )
        val remote = RecordingRemote()
        val mgr = PosSyncManager(store, remote)

        store.enqueueSale(
            OfflineSaleOutboxRow(
                clientSaleId = "sale-1",
                warehouseId = "wh2",
                currency = "USD",
                deviceId = "TILL-01",
                lines = listOf(
                    OfflineSaleLine(
                        stockItemId = "s1",
                        oemPartNumber = "40206-JF00A",
                        qty = 1,
                        expectedUnitPrice = 190.0,
                    ),
                ),
                cashAmount = "190.00",
                soldAt = "2026-08-15T01:00:00Z",
            ),
        )

        mgr.syncNow("wh2")

        assertEquals(
            listOf("replay:sale-1", "pull"),
            mgr.lastCallOrder,
        )
        assertEquals(listOf("replay:sale-1", "pull"), remote.calls)
    }

    @Test
    fun idempotentReplay_duplicateClientSaleIdSafe() = runTest {
        val store = InMemoryOfflineStore()
        val remote = RecordingRemote(duplicateOnSecond = true)
        val mgr = PosSyncManager(store, remote)
        val row = OfflineSaleOutboxRow(
            clientSaleId = "sale-dup",
            warehouseId = "wh2",
            currency = "USD",
            deviceId = "TILL-01",
            lines = emptyList(),
            cashAmount = "10.00",
            soldAt = "2026-08-15T01:00:00Z",
        )
        store.enqueueSale(row)
        mgr.syncNow("wh2")
        // Re-queue same id as pending (simulate retry after partial UI state).
        store.enqueueSale(row.copy(status = OutboxStatus.PENDING, invoiceId = null))
        mgr.syncNow("wh2")
        assertEquals("inv-sale-dup", store.listOutbox(setOf(OutboxStatus.SYNCED)).last().invoiceId)
        assertTrue(remote.replayCount >= 2)
    }

    @Test
    fun conflict_staysInOutbox_restoresQty() = runTest {
        val store = InMemoryOfflineStore()
        val pad = co.zw.nissangtr.pos.api.TillItem(
            stockItemId = "s1",
            oemPartNumber = "40206-JF00A",
            description = "Pad",
            unitPrice = 190.0,
            currency = "USD",
            saleableQty = 4,
        )
        store.replaceCatalog(
            OfflineSnapshot(
                warehouseId = "wh2",
                pulledAt = "t0",
                items = listOf(pad),
            ),
        )
        val remote = RecordingRemote(
            conflictCode = "offline_price_conflict",
            pullItems = listOf(pad.copy(saleableQty = 4)),
        )
        val mgr = PosSyncManager(store, remote)
        mgr.recordOfflineCashSale(
            warehouseId = "wh2",
            deviceId = "TILL-01",
            currency = "USD",
            lines = listOf(
                OfflineSaleLine("s1", "40206-JF00A", qty = 1, expectedUnitPrice = 190.0),
            ),
            cashAmountMajor = "190.00",
            soldAtIso = "t1",
            clientSaleId = "sale-c",
        )
        assertEquals(3, store.findByOem("40206-JF00A")!!.saleableQty)
        mgr.syncNow("wh2")
        val conflicts = store.listOutbox(setOf(OutboxStatus.CONFLICT))
        assertEquals(1, conflicts.size)
        assertEquals("offline_price_conflict", conflicts.single().conflictCode)
        // After conflict restore + pull, snapshot (server) qty wins.
        assertEquals(4, store.findByOem("40206-JF00A")!!.saleableQty)
    }

    private class RecordingRemote(
        private val duplicateOnSecond: Boolean = false,
        private val conflictCode: String? = null,
        private val pullItems: List<co.zw.nissangtr.pos.api.TillItem> = emptyList(),
    ) : OfflineSyncRemote {
        val calls = mutableListOf<String>()
        var replayCount = 0

        override suspend fun pullOfflineSnapshot(warehouseId: String): OfflineSnapshot {
            calls += "pull"
            return OfflineSnapshot(warehouseId, "2026-08-15T12:00:00Z", items = pullItems)
        }

        override suspend fun replayOfflineSale(
            clientSaleId: String,
            row: OfflineSaleOutboxRow,
        ): ReplayResult {
            calls += "replay:$clientSaleId"
            replayCount++
            if (conflictCode != null) {
                throw OfflineConflictException(conflictCode, "conflict")
            }
            val dup = duplicateOnSecond && replayCount > 1
            return ReplayResult(invoiceId = "inv-$clientSaleId", duplicate = dup)
        }
    }
}
