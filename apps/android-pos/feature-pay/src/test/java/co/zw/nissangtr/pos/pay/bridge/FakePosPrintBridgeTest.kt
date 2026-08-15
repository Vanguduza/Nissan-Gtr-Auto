package co.zw.nissangtr.pos.pay.bridge

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FakePosPrintBridgeTest {

    @Test
    fun printReceipt_recordsLines_noWebBluetooth() = runTest {
        val printer = FakePosPrintBridge()
        printer.printReceiptLines(
            listOf(
                "GTR POS RECEIPT",
                "USD 212.00",
                "40206-JF00A x1",
            ),
        )
        assertEquals(1, printer.printedReceipts.size)
        assertTrue(printer.printedReceipts.single().none { it.contains("tax", ignoreCase = true) })
        assertEquals("USD 212.00", printer.printedReceipts.single()[1])
    }

    @Test
    fun scanBridge_returnsPayload() = runTest {
        val scan = FakePosScanBridge(nextPayload = "40206-JF00A")
        assertEquals("40206-JF00A", scan.scanOnce())
        assertEquals(1, scan.scanCount)
    }

    @Test
    fun cashDrawer_fakeRecordsOpen_noWebBluetooth() = runTest {
        val drawer = FakePosCashDrawerBridge()
        drawer.openDrawer()
        drawer.openDrawer()
        assertEquals(2, drawer.openCount)
    }
}
