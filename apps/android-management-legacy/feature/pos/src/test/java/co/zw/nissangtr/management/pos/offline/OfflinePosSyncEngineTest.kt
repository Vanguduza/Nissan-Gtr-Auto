package co.zw.nissangtr.management.pos.offline

import co.zw.nissangtr.management.rpc.CurrencyCode
import co.zw.nissangtr.management.rpc.FakeRpcClient
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class OfflinePosSyncEngineTest {

    @Test
    fun pull_queues_and_replays_idempotently() = runBlocking {
        val rpc = FakeRpcClient()
        val store = InMemoryOfflinePosStore()
        val engine = OfflinePosSyncEngine(rpc, store, deviceId = "test-tablet")
        val wh = FakeRpcClient.FAKE_WAREHOUSE_ID

        val snap = engine.pullSnapshot(wh)
        assertTrue(snap.items.isNotEmpty())

        val item = snap.items.first()
        val clientSaleId = engine.queueCashSale(
            warehouseId = wh,
            currency = CurrencyCode.USD,
            exchangeRate = 1.0,
            lines = listOf(
                LocalCartLine(
                    id = "line-1",
                    stockItemId = item.stockItemId,
                    oemPartNumber = item.oemPartNumber,
                    uomId = item.uomId,
                    qty = 2.0,
                    unitPrice = item.unitPrice,
                    lineTotal = item.unitPrice * 2.0,
                ),
            ),
        )
        assertEquals(1, engine.pendingCount())

        val first = engine.drainQueue()
        assertEquals(1, first.synced)
        assertEquals(0, first.conflicts)
        assertEquals(0, engine.pendingCount())
        val invoiceId = store.pendingSales(includeTerminal = true)
            .first { it.clientSaleId == clientSaleId }
            .serverInvoiceId

        // Force another pending replay of same client_sale_id → Fake returns same invoice.
        val sale = store.pendingSales(includeTerminal = true).first { it.clientSaleId == clientSaleId }
        store.enqueueSale(sale.copy(status = PendingSaleStatus.Pending))
        val second = engine.drainQueue()
        assertEquals(1, second.synced)
        val synced = store.pendingSales(includeTerminal = true)
            .first { it.clientSaleId == clientSaleId }
        assertEquals(PendingSaleStatus.Synced, synced.status)
        assertEquals(invoiceId, synced.serverInvoiceId)
    }

    @Test
    fun price_conflict_marks_conflict_not_synced() = runBlocking {
        val rpc = FakeRpcClient()
        val store = InMemoryOfflinePosStore()
        val engine = OfflinePosSyncEngine(rpc, store, deviceId = "test-tablet")
        val wh = FakeRpcClient.FAKE_WAREHOUSE_ID
        val snap = engine.pullSnapshot(wh)
        val item = snap.items.first()

        engine.queueCashSale(
            warehouseId = wh,
            currency = CurrencyCode.USD,
            exchangeRate = 1.0,
            lines = listOf(
                LocalCartLine(
                    id = "line-1",
                    stockItemId = item.stockItemId,
                    oemPartNumber = item.oemPartNumber,
                    uomId = item.uomId,
                    qty = 1.0,
                    unitPrice = item.unitPrice + 5.0, // drift → conflict on replay
                    lineTotal = item.unitPrice + 5.0,
                ),
            ),
        )
        val drain = engine.drainQueue()
        assertEquals(0, drain.synced)
        assertEquals(1, drain.conflicts)
        val pending = store.pendingSales(includeTerminal = false)
        assertEquals(PendingSaleStatus.Conflict, pending.first().status)
    }

    @Test
    fun insufficient_local_stock_rejects_queue() = runBlocking {
        val rpc = FakeRpcClient()
        val store = InMemoryOfflinePosStore()
        val engine = OfflinePosSyncEngine(rpc, store, deviceId = "test-tablet")
        val wh = FakeRpcClient.FAKE_WAREHOUSE_ID
        val snap = engine.pullSnapshot(wh)
        val item = snap.items.first()
        try {
            engine.queueCashSale(
                warehouseId = wh,
                currency = CurrencyCode.USD,
                exchangeRate = 1.0,
                lines = listOf(
                    LocalCartLine(
                        id = "line-1",
                        stockItemId = item.stockItemId,
                        oemPartNumber = item.oemPartNumber,
                        uomId = item.uomId,
                        qty = item.saleableQty + 10,
                        unitPrice = item.unitPrice,
                        lineTotal = item.unitPrice * (item.saleableQty + 10),
                    ),
                ),
            )
            fail("expected insufficient stock")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message.orEmpty().contains("Insufficient", ignoreCase = true))
        }
        assertEquals(0, engine.pendingCount())
    }
}
