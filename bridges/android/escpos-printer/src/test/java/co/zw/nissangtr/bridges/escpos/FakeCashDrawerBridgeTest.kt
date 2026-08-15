package co.zw.nissangtr.bridges.escpos

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fake drawer for tests/debug — no Bluetooth. Live path is [BluetoothCashDrawerBridge].
 */
class FakeCashDrawerBridgeTest {

    @Test
    fun openDrawer_recordsCountAndPin() = runBlocking {
        val drawer = FakeCashDrawerBridge()
        assertEquals(0, drawer.openCount)
        assertNull(drawer.lastPin)

        drawer.openDrawer(CashDrawerPin.PIN_2)
        drawer.openDrawer(CashDrawerPin.PIN_5)

        assertEquals(2, drawer.openCount)
        assertEquals(CashDrawerPin.PIN_5, drawer.lastPin)
    }

    @Test
    fun bluetoothAdapter_delegatesToPrinterOpenCashDrawer() = runBlocking {
        val printer = RecordingEscPosPrinterBridge()
        val drawer = BluetoothCashDrawerBridge(printer)

        drawer.openDrawer(CashDrawerPin.PIN_2)

        assertEquals(1, printer.openCashDrawerCalls)
        assertEquals(CashDrawerPin.PIN_2, printer.lastPin)
        assertTrue(printer.lastRaw!!.contentEquals(EscPosCommands.cashDrawerPulse(CashDrawerPin.PIN_2)))
    }

    /** Minimal stub: only [openCashDrawer] / [printRaw] exercised. */
    private class RecordingEscPosPrinterBridge : EscPosPrinterBridge {
        var openCashDrawerCalls: Int = 0
        var lastPin: CashDrawerPin? = null
        var lastRaw: ByteArray? = null

        override fun configurePrinterAddress(macAddress: String) = Unit
        override fun getConfiguredPrinterAddress(): String? = null
        override suspend fun listBondedDevices(): List<BondedEscPosDevice> = emptyList()
        override suspend fun getBluetoothPermissionStatus() = BluetoothPermissionStatus.GRANTED
        override suspend fun requestBluetoothPermission() = BluetoothPermissionStatus.GRANTED
        override suspend fun connect() = Unit
        override suspend fun disconnect() = Unit
        override suspend fun isConnected(): Boolean = true
        override suspend fun printInventoryLabel(job: EscPosPrintJob) = Unit
        override suspend fun printReceiptLines(lines: List<EscPosReceiptLine>) = Unit

        override suspend fun printRaw(bytes: ByteArray) {
            lastRaw = bytes
        }

        override suspend fun openCashDrawer(pin: CashDrawerPin) {
            openCashDrawerCalls++
            lastPin = pin
            printRaw(EscPosCommands.cashDrawerPulse(pin))
        }
    }
}
