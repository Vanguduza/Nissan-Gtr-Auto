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

/**
 * Bluetooth ESC/POS inventory label + receipt printer.
 * Configure printer MAC via [configurePrinterAddress] before [connect].
 */
interface EscPosPrinterBridge {
    /** Persist bonded printer MAC (e.g. `00:11:22:33:44:55`). */
    fun configurePrinterAddress(macAddress: String)
    suspend fun getBluetoothPermissionStatus(): BluetoothPermissionStatus
    suspend fun requestBluetoothPermission(): BluetoothPermissionStatus
    suspend fun connect()
    suspend fun disconnect()
    suspend fun isConnected(): Boolean
    suspend fun printInventoryLabel(job: EscPosPrintJob)
    suspend fun printReceiptLines(lines: List<EscPosReceiptLine>)
    suspend fun printRaw(bytes: ByteArray)
}
