package co.zw.nissangtr.management.dispatch

import co.zw.nissangtr.management.rpc.ConfirmPickLineInput
import co.zw.nissangtr.management.rpc.FakeRpcClient
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PickPackDeskFakeRpcParityTest {

    @Test
    fun dispatchInvoicesPickLinesConfirmAndCancelDn() = runBlocking {
        val rpc = FakeRpcClient()

        val invoices = rpc.listDispatchInvoices()
        assertTrue(invoices.any { it.id == FakeRpcClient.FAKE_DISPATCH_INVOICE_ID })
        assertEquals("dispatch", invoices.first().fulfillmentMode)

        val lines = rpc.listPickListLines(FakeRpcClient.FAKE_PICK_LIST_ID)
        assertTrue(lines.any { it.id == FakeRpcClient.FAKE_PICK_LIST_LINE_ID })
        val seed = lines.first { it.id == FakeRpcClient.FAKE_PICK_LIST_LINE_ID }
        assertEquals(FakeRpcClient.FAKE_INVOICE_LINE_ID, seed.salesInvoiceLineId)
        assertEquals(2.0, seed.qtyRequested, 0.001)
        assertEquals("21410-JF00A", seed.oemPartNumber)

        val confirmed = rpc.confirmPickLines(
            FakeRpcClient.FAKE_PICK_LIST_ID,
            listOf(
                ConfirmPickLineInput(
                    pickListLineId = FakeRpcClient.FAKE_PICK_LIST_LINE_ID,
                    qtyPicked = 2.0,
                ),
            ),
        )
        assertEquals(FakeRpcClient.FAKE_PICK_LIST_ID, confirmed)
        val afterConfirm = rpc.listPickListLines(FakeRpcClient.FAKE_PICK_LIST_ID)
            .first { it.id == FakeRpcClient.FAKE_PICK_LIST_LINE_ID }
        assertEquals(2.0, afterConfirm.qtyPicked!!, 0.001)
        assertEquals("done", rpc.listPickLists().first { it.id == FakeRpcClient.FAKE_PICK_LIST_ID }.status)

        val dnId = rpc.createDeliveryNote(
            salesInvoiceId = FakeRpcClient.FAKE_DISPATCH_INVOICE_ID,
            lines = listOf(
                co.zw.nissangtr.management.rpc.DnLineInput(
                    FakeRpcClient.FAKE_INVOICE_LINE_ID,
                    2.0,
                ),
            ),
            pickListId = FakeRpcClient.FAKE_PICK_LIST_ID,
        )
        assertTrue(dnId.isNotBlank())
        rpc.submitDeliveryNote(dnId)
        assertEquals("submitted", rpc.listDeliveryNotes().first { it.id == dnId }.status)

        rpc.cancelDeliveryNote(dnId)
        assertEquals("cancelled", rpc.listDeliveryNotes().first { it.id == dnId }.status)
    }

    @Test
    fun createPickListSeedsLineForDesk() = runBlocking {
        val rpc = FakeRpcClient()
        val id = rpc.createPickList(FakeRpcClient.FAKE_DISPATCH_INVOICE_ID)
        val lines = rpc.listPickListLines(id)
        assertEquals(1, lines.size)
        assertEquals(1.0, lines.first().qtyRequested, 0.001)
    }

    @Test
    fun draftsFromLinesSeedsMissingKeys() {
        val line = co.zw.nissangtr.management.rpc.PickListLineSummary(
            id = "line-1",
            pickListId = "pl-1",
            salesInvoiceLineId = "sil-1",
            stockItemId = "si-1",
            qtyRequested = 3.0,
            qtyPicked = null,
        )
        val drafts = DispatchViewModel.draftsFromLines(listOf(line), emptyMap())
        assertEquals("3.0", drafts["line-1"])
        val kept = DispatchViewModel.draftsFromLines(listOf(line), mapOf("line-1" to "1.5"))
        assertEquals("1.5", kept["line-1"])
    }
}
