package co.zw.nissangtr.bridges.escpos

/**
 * Mirrors bridges/contracts/qr-inventory.ts — EscPosPrinterBridge Kotlin surface.
 * BluetoothAdapter / RFCOMM only — no Web Bluetooth, no Supabase.
 */

enum class BluetoothPermissionStatus {
    GRANTED,
    DENIED,
    RESTRICTED,
    NOT_DETERMINED,
}

enum class InventoryQrValuation {
    FIFO,
    AVG,
}

/**
 * One thermal label: QR glyph + human-readable OEM / batch / date.
 * [qrPayload] must be the full canonical `gtr://part/…` string.
 */
data class EscPosPrintJob(
    val qrPayload: String,
    val oemPartNumber: String,
    val batchCode: String,
    val valuation: InventoryQrValuation,
    /** Optional ISO date under the QR (defaults to device local date). */
    val labelDate: String? = null,
)

/** One text line for a thermal receipt. */
data class EscPosReceiptLine(
    val text: String,
    val emphasis: Boolean = false,
)

/** System-bonded Bluetooth device selectable as an ESC/POS printer. */
data class BondedEscPosDevice(
    val name: String,
    val address: String,
)

/**
 * Drawer kick connector pin on Epson-compatible ESC/POS printers (ESC p / DLE DC4).
 * Most RJ11 cash drawers use [PIN_2]; some use [PIN_5].
 */
enum class CashDrawerPin(val escPosM: Int) {
    PIN_2(0),
    PIN_5(1),
}

/**
 * Bluetooth ESC/POS inventory label + receipt printer.
 * Configure printer MAC via [configurePrinterAddress] before [connect].
 * Classic RFCOMM only — no Web Bluetooth.
 */
interface EscPosPrinterBridge {
    /** Persist bonded printer MAC (e.g. `00:11:22:33:44:55`). */
    fun configurePrinterAddress(macAddress: String)
    /** Last configured MAC from prefs, if any. */
    fun getConfiguredPrinterAddress(): String?
    /** Bonded devices from system Bluetooth settings (already paired). */
    suspend fun listBondedDevices(): List<BondedEscPosDevice>
    suspend fun getBluetoothPermissionStatus(): BluetoothPermissionStatus
    suspend fun requestBluetoothPermission(): BluetoothPermissionStatus
    suspend fun connect()
    suspend fun disconnect()
    suspend fun isConnected(): Boolean
    suspend fun printInventoryLabel(job: EscPosPrintJob)
    suspend fun printReceiptLines(lines: List<EscPosReceiptLine>)
    suspend fun printRaw(bytes: ByteArray)
    /**
     * Pulse the cash-drawer kick via ESC p on the connected printer.
     * Requires an open RFCOMM session ([connect]).
     */
    suspend fun openCashDrawer(pin: CashDrawerPin = CashDrawerPin.PIN_2)
}

/**
 * Narrow cash-drawer surface for POS / till rails.
 * Live path: [BluetoothCashDrawerBridge] over [EscPosPrinterBridge].
 * Tests/debug: [FakeCashDrawerBridge] only — never invent HTML5 / Web Bluetooth.
 */
interface CashDrawerBridge {
    suspend fun openDrawer(pin: CashDrawerPin = CashDrawerPin.PIN_2)
}
