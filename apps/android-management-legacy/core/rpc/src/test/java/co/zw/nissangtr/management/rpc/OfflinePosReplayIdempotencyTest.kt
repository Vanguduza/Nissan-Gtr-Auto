package co.zw.nissangtr.management.rpc

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflinePosReplayIdempotencyTest {

    @Test
    fun fake_replay_is_idempotent_on_client_sale_id() = runBlocking {
        val rpc = FakeRpcClient()
        val snap = rpc.pullPosOfflineSnapshot(FakeRpcClient.FAKE_WAREHOUSE_ID)
        val item = snap.items.first()
        val clientSaleId = "00000000-0000-4000-8000-00000000sale"
        val payload = OfflineSaleReplayPayload(
            warehouseId = FakeRpcClient.FAKE_WAREHOUSE_ID,
            currency = CurrencyCode.USD,
            lines = listOf(
                OfflineSaleLine(
                    stockItemId = item.stockItemId,
                    uomId = item.uomId,
                    qty = 1.0,
                    expectedUnitPrice = item.unitPrice,
                ),
            ),
            tenders = listOf(
                PosTenderLine(tender = "cash", amount = item.unitPrice, currency = "USD"),
            ),
        )
        val first = rpc.replayOfflinePosSale(clientSaleId, payload)
        val second = rpc.replayOfflinePosSale(clientSaleId, payload)
        assertEquals(first, second)
        assertTrue(first.isNotBlank())
    }
}
