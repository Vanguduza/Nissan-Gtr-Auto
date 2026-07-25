package co.zw.nissangtr.bridges.qr

/**
 * Mirrors bridges/contracts/qr-inventory.ts — native Kotlin surface for QrScannerBridge.
 * Bridge emits decoded payload strings only; no Supabase / network in this module.
 */

enum class CameraPermissionStatus {
    GRANTED,
    DENIED,
    RESTRICTED,
    NOT_DETERMINED,
}

enum class InventoryQrValuation {
    FIFO,
    AVG,
}

/** Fields decoded from a valid inventory QR URI. */
data class InventoryQrFields(
    val oemPartNumber: String,
    val batchCode: String,
    val valuation: InventoryQrValuation,
)

/** Result of a native camera decode (raw string + device timestamp). */
data class QrScanResult(
    /** Full decoded string (expect `gtr://part/…` for inventory stickers). */
    val rawValue: String,
    /** ISO-8601 timestamp from device when decoded. */
    val scannedAt: String,
)

/**
 * Native QR scanner. Returns the raw payload; callers parse with [parseInventoryQrPayload]
 * and pass `rawValue` into inventory/sales RPCs (e.g. add_cart_line_from_qr).
 */
interface QrScannerBridge {
    suspend fun getCameraPermissionStatus(): CameraPermissionStatus
    suspend fun requestCameraPermission(): CameraPermissionStatus
    /** Start native preview; resolve on first successful decode. */
    suspend fun scanOnce(): QrScanResult
    /** Abort an in-flight [scanOnce] without resolving a value. */
    suspend fun cancel()
}

private val INVENTORY_QR_REGEX =
    Regex("""^gtr://part/([^?]+)\?batch=([^&]+)&valuation=(FIFO|AVG)$""")

/**
 * Parse canonical inventory QR URI (mirrors packages/shared `parseInventoryQrPayload`).
 * Throws [IllegalArgumentException] when the payload is not a valid inventory sticker.
 */
fun parseInventoryQrPayload(payload: String): InventoryQrFields {
    val m = INVENTORY_QR_REGEX.matchEntire(payload.trim())
        ?: throw IllegalArgumentException("Invalid inventory QR payload: $payload")
    return InventoryQrFields(
        oemPartNumber = m.groupValues[1],
        batchCode = m.groupValues[2],
        valuation = InventoryQrValuation.valueOf(m.groupValues[3]),
    )
}

/** Build canonical inventory QR URI (mirrors packages/shared / build_qr_payload). */
fun buildInventoryQrPayload(
    oemPartNumber: String,
    batchCode: String,
    valuation: InventoryQrValuation,
): String {
    val oem = oemPartNumber.trim()
    val batch = batchCode.trim()
    require(oem.isNotEmpty() && batch.isNotEmpty()) {
        "oemPartNumber and batchCode are required"
    }
    return "gtr://part/$oem?batch=$batch&valuation=${valuation.name}"
}
