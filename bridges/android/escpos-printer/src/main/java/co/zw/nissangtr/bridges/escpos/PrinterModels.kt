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
}
