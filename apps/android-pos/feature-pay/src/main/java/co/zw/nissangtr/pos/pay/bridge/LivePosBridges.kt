package co.zw.nissangtr.pos.pay.bridge

import android.app.Activity
import android.content.Context
import co.zw.nissangtr.bridges.escpos.BluetoothCashDrawerBridge
import co.zw.nissangtr.bridges.escpos.BluetoothEscPosPrinterBridge
import co.zw.nissangtr.bridges.escpos.BluetoothPermissionStatus
import co.zw.nissangtr.bridges.escpos.CashDrawerPin
import co.zw.nissangtr.bridges.escpos.EscPosPrinterBridge
import co.zw.nissangtr.bridges.escpos.EscPosReceiptLine
import co.zw.nissangtr.bridges.qr.CameraxQrScannerBridge
import co.zw.nissangtr.bridges.qr.CameraPermissionStatus

/**
 * Live CameraX QR → [PosScanBridge]. Never HTML5 / browser camera.
 */
class LivePosScanBridge(
    context: Context,
) : PosScanBridge {
    private val camera = CameraxQrScannerBridge(context)

    fun attachActivity(activity: Activity) = camera.attachActivity(activity)

    fun detachActivity() = camera.detachActivity()

    fun onPermissionResult() = camera.onPermissionResult()

    fun onScanActivityResult(resultCode: Int, data: android.content.Intent?) =
        camera.onScanActivityResult(resultCode, data)

    override suspend fun scanOnce(): String {
        val status = camera.getCameraPermissionStatus()
        if (status != CameraPermissionStatus.GRANTED) {
            val requested = camera.requestCameraPermission()
            if (requested != CameraPermissionStatus.GRANTED) {
                error("Camera permission required for scan")
            }
        }
        return camera.scanOnce().rawValue
    }

    override suspend fun cancel() = camera.cancel()
}

/**
 * Live Bluetooth ESC/POS → [PosPrintBridge].
 * MAC persisted in bridge prefs (`gtr_escpos_printer` / `printer_mac`).
 */
class LivePosPrintBridge(
    context: Context,
) : PosPrintBridge {
    private val printer: BluetoothEscPosPrinterBridge =
        BluetoothEscPosPrinterBridge(context)

    @Volatile
    private var lastError: String? = null

    val escPos: EscPosPrinterBridge get() = printer

    fun attachActivity(activity: Activity) = printer.attachActivity(activity)

    fun detachActivity() = printer.detachActivity()

    fun onPermissionResult() = printer.onPermissionResult()

    fun configurePrinterAddress(mac: String) = printer.configurePrinterAddress(mac)

    override fun getConfiguredPrinterAddress(): String? = printer.getConfiguredPrinterAddress()

    override suspend fun ensureBluetoothPermission(): Boolean {
        val status = printer.getBluetoothPermissionStatus()
        if (status == BluetoothPermissionStatus.GRANTED) return true
        return printer.requestBluetoothPermission() == BluetoothPermissionStatus.GRANTED
    }

    override suspend fun listBondedPrinters(): List<PosBondedPrinter> {
        if (!ensureBluetoothPermission()) {
            lastError = "Bluetooth permission required"
            return emptyList()
        }
        return runCatching {
            printer.listBondedDevices().map {
                PosBondedPrinter(name = it.name, address = it.address)
            }
        }.getOrElse { e ->
            lastError = e.message ?: "list bonded failed"
            emptyList()
        }
    }

    override suspend fun connectPrinter(mac: String?): PosPrinterStatus {
        if (!ensureBluetoothPermission()) {
            lastError = "Bluetooth permission required"
            return printerStatus()
        }
        val target = mac?.trim()?.takeIf { it.isNotBlank() }
            ?: printer.getConfiguredPrinterAddress()
        if (target.isNullOrBlank()) {
            lastError = "Select a bonded printer or set MAC first"
            return printerStatus()
        }
        return try {
            printer.configurePrinterAddress(target)
            printer.connect()
            lastError = null
            printerStatus()
        } catch (e: Exception) {
            lastError = e.message ?: "printer connect failed"
            PosPrinterStatus(
                connected = false,
                mac = printer.getConfiguredPrinterAddress(),
                lastError = lastError,
            )
        }
    }

    override suspend fun printerStatus(): PosPrinterStatus {
        val connected = runCatching { printer.isConnected() }.getOrDefault(false)
        return PosPrinterStatus(
            connected = connected,
            mac = printer.getConfiguredPrinterAddress(),
            lastError = lastError,
        )
    }

    override suspend fun printReceiptLines(lines: List<String>) {
        if (printer.getConfiguredPrinterAddress().isNullOrBlank()) {
            lastError = "Configure Bluetooth printer in utilities first"
            error(lastError!!)
        }
        runCatching { printer.connect() }
            .onFailure { lastError = it.message ?: "connect failed" }
        printer.printReceiptLines(
            lines.map { EscPosReceiptLine(text = it) },
        )
        lastError = null
    }
}

/**
 * Live cash-drawer kick via ESC p on the same Bluetooth printer session.
 */
class LivePosCashDrawerBridge(
    private val printBridge: LivePosPrintBridge,
) : PosCashDrawerBridge {
    private val drawer = BluetoothCashDrawerBridge(printBridge.escPos)

    override suspend fun openDrawer() {
        drawer.openDrawer(CashDrawerPin.PIN_2)
    }
}

/**
 * Resolve Fake vs Live bridges from [forceFake].
 */
object PosBridgeFactory {
    data class Bundle(
        val scan: PosScanBridge,
        val print: PosPrintBridge,
        val drawer: PosCashDrawerBridge,
        val liveScan: LivePosScanBridge? = null,
        val livePrint: LivePosPrintBridge? = null,
    )

    fun create(context: Context, forceFake: Boolean): Bundle {
        if (forceFake) {
            return Bundle(
                scan = FakePosScanBridge(),
                print = FakePosPrintBridge(),
                drawer = FakePosCashDrawerBridge(),
            )
        }
        val print = LivePosPrintBridge(context)
        val scan = LivePosScanBridge(context)
        return Bundle(
            scan = scan,
            print = print,
            drawer = LivePosCashDrawerBridge(print),
            liveScan = scan,
            livePrint = print,
        )
    }
}
