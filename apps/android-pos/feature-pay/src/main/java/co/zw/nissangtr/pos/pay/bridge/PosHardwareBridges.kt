package co.zw.nissangtr.pos.pay.bridge

/**
 * POS-side scan bridge. Hardware path: `bridges/android/qr-scanner` CameraX
 * ([co.zw.nissangtr.bridges.qr.QrScannerBridge]) — never HTML5 / browser camera.
 */
interface PosScanBridge {
    suspend fun scanOnce(): String
    suspend fun cancel() {}
}

class FakePosScanBridge(
    var nextPayload: String = "40206-JF00A",
) : PosScanBridge {
    var scanCount: Int = 0
        private set

    override suspend fun scanOnce(): String {
        scanCount++
        return nextPayload
    }
}

/**
 * POS-side ESC/POS print. Hardware path: `bridges/android/escpos-printer`
 * ([co.zw.nissangtr.bridges.escpos.EscPosPrinterBridge]) — Classic RFCOMM only,
 * never Web Bluetooth.
 */
interface PosPrintBridge {
    suspend fun printReceiptLines(lines: List<String>)
}

class FakePosPrintBridge : PosPrintBridge {
    val printedReceipts: MutableList<List<String>> = mutableListOf()

    override suspend fun printReceiptLines(lines: List<String>) {
        printedReceipts += lines.toList()
    }
}

/**
 * POS-side cash-drawer kick. Live path:
 * `BluetoothCashDrawerBridge(BluetoothEscPosPrinterBridge)` → ESC p pulse.
 * Fake for tests/debug only.
 */
interface PosCashDrawerBridge {
    suspend fun openDrawer()
}

class FakePosCashDrawerBridge : PosCashDrawerBridge {
    var openCount: Int = 0
        private set

    override suspend fun openDrawer() {
        openCount++
    }
}
