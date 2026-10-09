package co.zw.nissangtr.bridges.escpos

enum class BluetoothPermissionStatus {
    GRANTED,
    DENIED,
    RESTRICTED,
    NOT_DETERMINED,
}

enum class PrinterTransport { BLUETOOTH, WIFI }

enum class InventoryQrValuation { FIFO, AVG }

data class EscPosPrintJob(
    val qrPayload: String,
    val oemPartNumber: String,
    val batchCode: String,
    val valuation: InventoryQrValuation,
    val labelDate: String? = null,
)

data class EscPosReceiptLine(
    val text: String,
    val emphasis: Boolean = false,
)

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
 * Android ESC/POS printer transport. Bluetooth uses classic SPP; Wi-Fi uses raw TCP
 * (normally port 9100). The printer must accept ESC/POS bytes, directly or through
 * a vendor/network adapter exposing an ESC/POS socket.
 */
interface EscPosPrinterBridge {
    fun selectTransport(transport: PrinterTransport)
    fun getConfiguredTransport(): PrinterTransport

    /** Bluetooth SPP target. */
    fun configurePrinterAddress(macAddress: String)
    fun getConfiguredPrinterAddress(): String?

    /** Wi-Fi/LAN raw ESC/POS socket target. */
    fun configureNetworkPrinter(host: String, port: Int = 9100)
    fun getConfiguredNetworkHost(): String?
    fun getConfiguredNetworkPort(): Int

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
     * Requires an open printer session ([connect]) on the selected transport.
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
