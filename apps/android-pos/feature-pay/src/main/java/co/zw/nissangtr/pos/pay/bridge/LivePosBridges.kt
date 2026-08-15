package co.zw.nissangtr.pos.pay.bridge

import android.app.Activity
import android.content.Context
import co.zw.nissangtr.bridges.escpos.BluetoothCashDrawerBridge
import co.zw.nissangtr.bridges.escpos.BluetoothEscPosPrinterBridge
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
 */
class LivePosPrintBridge(
    context: Context,
) : PosPrintBridge {
    private val printer: BluetoothEscPosPrinterBridge =
        BluetoothEscPosPrinterBridge(context)

    val escPos: EscPosPrinterBridge get() = printer

    fun attachActivity(activity: Activity) = printer.attachActivity(activity)

    fun detachActivity() = printer.detachActivity()

    fun configurePrinterAddress(mac: String) = printer.configurePrinterAddress(mac)

    override suspend fun printReceiptLines(lines: List<String>) {
        if (printer.getConfiguredPrinterAddress().isNullOrBlank()) {
            error("Configure Bluetooth printer MAC in settings first")
        }
        runCatching { printer.connect() }
        printer.printReceiptLines(
            lines.map { EscPosReceiptLine(text = it) },
        )
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
