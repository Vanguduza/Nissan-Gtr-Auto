package co.zw.nissangtr.bridges.escpos

/**
 * Live cash-drawer kick: sends ESC p through an already-connected [EscPosPrinterBridge].
 * Host must [EscPosPrinterBridge.connect] first (same Bluetooth session as receipts).
 */
class BluetoothCashDrawerBridge(
    private val printer: EscPosPrinterBridge,
) : CashDrawerBridge {
    override suspend fun openDrawer(pin: CashDrawerPin) {
        printer.openCashDrawer(pin)
    }
}

/**
 * In-memory drawer for unit tests and debug builds. Does not touch Bluetooth.
 */
class FakeCashDrawerBridge : CashDrawerBridge {
    var openCount: Int = 0
        private set
    var lastPin: CashDrawerPin? = null
        private set

    override suspend fun openDrawer(pin: CashDrawerPin) {
        openCount++
        lastPin = pin
    }

    fun reset() {
        openCount = 0
        lastPin = null
    }
}
