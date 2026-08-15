package co.zw.nissangtr.pos.pay.bridge

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    fun connectPrinter_persistsMacAndStatus_fakePath() = runTest {
        val printer = FakePosPrintBridge()
        printer.configuredMac = null
        printer.connected = false
        val status = printer.connectPrinter("11:22:33:44:55:66")
        assertTrue(status.connected)
        assertEquals("11:22:33:44:55:66", status.mac)
        assertEquals("11:22:33:44:55:66", printer.getConfiguredPrinterAddress())
        assertEquals(1, printer.listBondedPrinters().size)
        assertNull(status.lastError)
    }

    @Test
    fun connectPrinter_requiresMac_fakePath() = runTest {
        val printer = FakePosPrintBridge()
        printer.configuredMac = null
        printer.bonded = emptyList()
        val status = printer.connectPrinter(null)
        assertTrue(!status.connected)
        assertEquals("Printer MAC required", status.lastError)
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
