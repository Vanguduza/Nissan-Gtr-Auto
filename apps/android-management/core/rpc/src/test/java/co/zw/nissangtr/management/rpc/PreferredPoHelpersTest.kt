package co.zw.nissangtr.management.rpc

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** H2 — preferred-PO helpers + Fake create/submit (mirrors web preferred-po.ts). */
class PreferredPoHelpersTest {

    @Test
    fun pickReceivingWarehouse_prefersWh1() {
        val wh2 = WarehouseRef("2", "WH2", "Floor", roleCode = "WH2")
        val wh1 = WarehouseRef("1", "WH1", "Receiving", roleCode = "WH1")
        assertEquals(wh1.id, pickReceivingWarehouse(listOf(wh2, wh1))!!.id)
    }

    @Test
    fun pickReceivingWarehouse_fallsBackToMainThenFirst() {
        val main = WarehouseRef("m", "MAIN", "Main")
        val other = WarehouseRef("o", "OTHER", "Other")
        assertEquals(main.id, pickReceivingWarehouse(listOf(other, main))!!.id)
        assertEquals(other.id, pickReceivingWarehouse(listOf(other))!!.id)
    }

    @Test
    fun resolveProgress_draftSubmittedApprovedFundsReceive() {
        assertEquals(
            ProcurementProgressStep.DRAFT,
            resolveProcurementProgress(status = "draft"),
        )
        assertEquals(
            ProcurementProgressStep.SUBMITTED,
            resolveProcurementProgress(status = "submitted"),
        )
        assertEquals(
            ProcurementProgressStep.APPROVED,
            resolveProcurementProgress(status = "approved"),
        )
        assertEquals(
            ProcurementProgressStep.FUNDS_RELEASED,
            resolveProcurementProgress(status = "approved", fundsReleasedAt = "2026-08-12T00:00:00Z"),
        )
        assertEquals(
            ProcurementProgressStep.PARTIALLY_RECEIVED,
            resolveProcurementProgress(status = "approved", qtyOrdered = 10.0, qtyReceived = 3.0),
        )
        assertEquals(
            ProcurementProgressStep.RECEIVED,
            resolveProcurementProgress(status = "approved", qtyOrdered = 10.0, qtyReceived = 10.0),
        )
        assertEquals(
            ProcurementProgressStep.CLOSED,
            resolveProcurementProgress(status = "approved", progressStep = "closed"),
        )
        assertEquals(
            ProcurementProgressStep.REJECTED,
            resolveProcurementProgress(status = "rejected"),
        )
    }

    @Test
    fun fake_listPreferred_create_and_submit() = runBlocking {
        val fake = FakeRpcClient()
        val preferred = fake.listPreferredSuppliers()
        assertTrue(preferred.isNotEmpty())
        assertEquals(FakeRpcClient.FAKE_SUPPLIER_ID, preferred.first().id)

        val recv = pickReceivingWarehouse(fake.listWarehouses())
        assertNotNull(recv)
        assertEquals("WH1", recv!!.code)

        val id = fake.createPurchaseOrder(
            supplierId = FakeRpcClient.FAKE_SUPPLIER_ID,
            warehouseId = recv.id,
            currency = CurrencyCode.USD,
            exchangeRate = 1.0,
            lines = listOf(
                BlanketLineInput(
                    stockItemId = FakeRpcClient.FAKE_STOCK_ITEM_ID,
                    uomId = "00000000-0000-4000-8000-0000000000u1",
                    qty = 2.0,
                    unitPrice = 12.5,
                    currency = CurrencyCode.USD,
                ),
            ),
            notes = "H2 fake",
        )
        assertTrue(id.isNotBlank())
        assertEquals(id, fake.submitPurchaseOrder(id))
    }
}
