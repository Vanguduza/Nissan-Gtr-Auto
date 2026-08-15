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

/** System-bonded Bluetooth printer selectable from till utilities. */
data class PosBondedPrinter(
    val name: String,
    val address: String,
)

/** Live/Fake printer connection status for utilities UI. */
data class PosPrinterStatus(
    val connected: Boolean,
    val mac: String?,
    val lastError: String? = null,
)

/**
 * POS-side ESC/POS print. Hardware path: `bridges/android/escpos-printer`
 * ([co.zw.nissangtr.bridges.escpos.EscPosPrinterBridge]) — Classic RFCOMM only,
 * never Web Bluetooth.
 */
interface PosPrintBridge {
    suspend fun printReceiptLines(lines: List<String>)

    fun getConfiguredPrinterAddress(): String? = null

    suspend fun listBondedPrinters(): List<PosBondedPrinter> = emptyList()

    /**
     * Persist [mac] when non-null, then RFCOMM connect.
     * Returns status including last error message on failure.
     */
    suspend fun connectPrinter(mac: String? = null): PosPrinterStatus =
        PosPrinterStatus(connected = false, mac = getConfiguredPrinterAddress(), lastError = "Not supported")

    suspend fun printerStatus(): PosPrinterStatus =
        PosPrinterStatus(connected = false, mac = getConfiguredPrinterAddress())

    /** Request BLUETOOTH_CONNECT (and related) if needed. Returns true when granted. */
    suspend fun ensureBluetoothPermission(): Boolean = true
}

class FakePosPrintBridge : PosPrintBridge {
    val printedReceipts: MutableList<List<String>> = mutableListOf()
    var configuredMac: String? = "AA:BB:CC:DD:EE:FF"
    var connected: Boolean = false
    var lastError: String? = null
    var bonded: List<PosBondedPrinter> = listOf(
        PosBondedPrinter(name = "Fake ESC/POS", address = "AA:BB:CC:DD:EE:FF"),
    )

    override fun getConfiguredPrinterAddress(): String? = configuredMac

    override suspend fun listBondedPrinters(): List<PosBondedPrinter> = bonded

    override suspend fun connectPrinter(mac: String?): PosPrinterStatus {
        val target = mac?.trim()?.takeIf { it.isNotBlank() } ?: configuredMac
        if (target.isNullOrBlank()) {
            lastError = "Printer MAC required"
            connected = false
            return printerStatus()
        }
        configuredMac = target
        connected = true
        lastError = null
        return printerStatus()
    }

    override suspend fun printerStatus(): PosPrinterStatus =
        PosPrinterStatus(connected = connected, mac = configuredMac, lastError = lastError)

    override suspend fun printReceiptLines(lines: List<String>) {
        if (configuredMac.isNullOrBlank()) {
            lastError = "Configure Bluetooth printer MAC in utilities first"
            error(lastError!!)
        }
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
