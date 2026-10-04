package co.zw.nissangtr.management.pos

import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import co.zw.nissangtr.bridges.escpos.DocumentPrinterBridge
import co.zw.nissangtr.bridges.escpos.EscPosPrinterBridge
import co.zw.nissangtr.bridges.escpos.EscPosReceiptLine
import co.zw.nissangtr.bridges.qr.CameraPermissionStatus
import co.zw.nissangtr.bridges.qr.QrScannerBridge
import co.zw.nissangtr.management.pos.offline.OfflinePosConnectivity
import co.zw.nissangtr.management.pos.offline.bundle.OfflineCatalogBundle
import co.zw.nissangtr.management.pos.offline.bundle.OfflineCatalogRpcClient
import co.zw.nissangtr.pos.ui.home.OfflineCatalogControl
import co.zw.nissangtr.pos.ui.home.OfflineCatalogPhase
import co.zw.nissangtr.management.rpc.RpcClient
import co.zw.nissangtr.pos.data.RpcPosGateways
import co.zw.nissangtr.pos.data.RpcSaleGateways
import co.zw.nissangtr.pos.data.RpcTillGateway
import co.zw.nissangtr.pos.domain.error.PosError
import co.zw.nissangtr.pos.domain.result.PosResult
import co.zw.nissangtr.pos.domain.gateway.BadgeScanner
import co.zw.nissangtr.bridges.qr.CameraLens
import co.zw.nissangtr.pos.data.RpcGovernanceGateway
import co.zw.nissangtr.pos.design.theme.PosTheme
import co.zw.nissangtr.pos.design.theme.PosWindowClass
import co.zw.nissangtr.pos.domain.model.ReceiptPaper
import co.zw.nissangtr.pos.domain.model.THERMAL_COLUMNS
import co.zw.nissangtr.pos.domain.model.thermalLines
import co.zw.nissangtr.pos.domain.model.thermalText
import co.zw.nissangtr.pos.ui.sale.A4_COLUMNS
import co.zw.nissangtr.pos.domain.state.CompanionIntent
import co.zw.nissangtr.pos.domain.state.PosIntent
import co.zw.nissangtr.pos.ui.home.PosHomeScreen
import co.zw.nissangtr.pos.ui.home.PosHostActions
import co.zw.nissangtr.pos.ui.sale.ReceiptLine
import co.zw.nissangtr.pos.ui.store.PosGateways
import co.zw.nissangtr.pos.ui.store.PosStoreViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDateTime

/**
 * The benchmark POS on the counter tablet (owner decisions D4–D6): the :feature:pos-ui shell bound
 * to live data through :feature:pos-data, with the printer and scanner bridges (Bridge-First).
 */
@Composable
fun PosTabletEntry(
    rpc: RpcClient,
    qr: QrScannerBridge,
    printer: EscPosPrinterBridge,
    documentPrinter: DocumentPrinterBridge?,
    onStaffPortal: (() -> Unit)?,
    onKioskSettings: (() -> Unit)?,
    onExitToHub: (() -> Unit)?,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val offlineCatalog = remember { OfflineCatalogHolder.get(context) }
    val vm: PosStoreViewModel = viewModel(
        factory = PosStoreViewModel.owning {
            val outbox = PosOfflineOutbox.open(context, rpc, offlineCatalog)
            // Catalogue calls fall back to the downloaded full catalogue with no connection.
            val catalogRpc = OfflineCatalogRpcClient(rpc, offlineCatalog, OfflineCatalogHolder.online::get, outbox::stockLines)
            val core = RpcPosGateways(catalogRpc)
            val deviceId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "tablet"
            val sale = RpcSaleGateways(catalogRpc, deviceId)
            PosGateways(
                session = core.session,
                catalog = core.catalog,
                fitment = core.fitment,
                cart = core.cart,
                pins = core.pins,
                checkout = sale.checkout,
                customers = sale.customers,
                sales = sale.sales,
                epc = sale.epc,
                offline = outbox,
                companion = sale.companion,
                // Till sessions are server-only: they talk to the live client, not the catalogue wrapper.
                till = RpcTillGateway(rpc, deviceId),
                governance = RpcGovernanceGateway(rpc),
                // Reserve-first checkout (§10.6): online sales reserve stock, then take money against the order.
                reserve = co.zw.nissangtr.pos.data.RpcReserveCheckoutGateway(rpc),
                // Part payments (staged split) on the reserved order.
                split = co.zw.nissangtr.pos.data.RpcSplitPaymentGateway(rpc),
                returns = co.zw.nissangtr.pos.data.RpcReturnsGateway(rpc),
                fulfillment = co.zw.nissangtr.pos.data.RpcFulfillmentGateway(rpc),
                // Card machine (ECR): the acquirer's app via the card-terminal bridge; a simulated machine
                // only when this build runs on the in-memory demo backend.
                terminal = RpcCardTerminalGateway(
                    rpc = rpc,
                    bridge = if (rpc is co.zw.nissangtr.management.rpc.FakeRpcClient) co.zw.nissangtr.bridges.terminal.SimulatedCardTerminalBridge()
                    else co.zw.nissangtr.bridges.terminal.IntentCardTerminalBridge(context).also { b -> (context as? android.app.Activity)?.let(b::attachActivity) },
                    key = { co.zw.nissangtr.bridges.terminal.TerminalDeviceKey() },
                    deviceId = deviceId,
                    prefs = context.getSharedPreferences("pos_card_terminal", android.content.Context.MODE_PRIVATE),
                ),
                // Manager ID badges are read with the front camera, facing whoever stands at the counter.
                badgeScanner = BadgeScanner {
                    try {
                        if (qr.getCameraPermissionStatus() != CameraPermissionStatus.GRANTED &&
                            qr.requestCameraPermission() != CameraPermissionStatus.GRANTED
                        ) {
                            return@BadgeScanner PosResult.Err(PosError.HardwareUnavailable("camera"))
                        }
                        PosResult.Ok(qr.scanOnce(CameraLens.FRONT, context.getString(co.zw.nissangtr.pos.ui.R.string.pos_badge_scan_hint)).rawValue)
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        PosResult.Ok(null)
                    } catch (e: SecurityException) {
                        PosResult.Err(PosError.HardwareUnavailable("camera"))
                    } catch (e: Exception) {
                        PosResult.Err(PosError.HardwareUnavailable("camera"))
                    }
                },
            ) to outbox::close
        },
    )
    val state by vm.store.state.collectAsState()
    // Offline restricted mode follows the device's validated connectivity (§10.12).
    LaunchedEffect(Unit) {
        OfflinePosConnectivity.onlineFlow(context).collect {
            OfflineCatalogHolder.online.set(it)
            vm.store.dispatch(PosIntent.ConnectivityChanged(it))
        }
    }
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(15_000)
            now = LocalDateTime.now()
        }
    }

    val catalogStatus by offlineCatalog.status.collectAsState()

    fun toast(message: String) = Toast.makeText(context, message, Toast.LENGTH_LONG).show()

    val host = PosHostActions(
        onScan = {
            scope.launch {
                val permission = qr.getCameraPermissionStatus().let {
                    if (it == CameraPermissionStatus.GRANTED) it else qr.requestCameraPermission()
                }
                if (permission != CameraPermissionStatus.GRANTED) {
                    toast("Camera permission is needed to scan.")
                    return@launch
                }
                runCatching { qr.scanOnce() }
                    .onSuccess {
                        // Linked to another till as its scanner: the part goes into that sale.
                        val raw = it.rawValue.trim()
                        vm.store.dispatch(if (vm.store.state.value.scanner != null) CompanionIntent.Scanned(raw) else PosIntent.SearchFor(raw))
                    }
                    .onFailure { toast("Scan cancelled or failed.") }
            }
        },
        onPrint = { lines, paper -> scope.launch { print(lines, paper, printer, documentPrinter, ::toast) } },
        onStaffPortal = onStaffPortal,
        onKioskSettings = onKioskSettings,
        onExitToHub = onExitToHub,
        offlineCatalog = catalogStatus.toControl(
            onDownload = { OfflineCatalogHolder.download(context, rpc) },
            onPause = OfflineCatalogHolder::pause,
            onRemove = { OfflineCatalogHolder.remove(context) },
        ),
    )

    BoxWithConstraints(Modifier.fillMaxSize()) {
        PosTheme(windowClass = PosWindowClass.derive(maxWidth, maxHeight)) {
            PosHomeScreen(state = state, now = now, dispatch = vm.store::dispatch, host = host)
        }
    }
}

/** A failed print never blocks a completed sale (§6.6): it is reported and can be retried. */
private suspend fun print(
    lines: List<ReceiptLine>,
    paper: ReceiptPaper,
    printer: EscPosPrinterBridge,
    documentPrinter: DocumentPrinterBridge?,
    toast: (String) -> Unit,
) {
    runCatching {
        when (paper) {
            ReceiptPaper.Thermal80 -> {
                if (!printer.isConnected()) printer.connect()
                printer.printReceiptLines(
                    lines.flatMap { row -> thermalLines(row.left, row.right, THERMAL_COLUMNS).map { EscPosReceiptLine(it, emphasis = row.strong) } },
                )
            }
            ReceiptPaper.A4 -> {
                val doc = documentPrinter ?: error("No document printer on this device.")
                doc.printTextDocument("Receipt", thermalText(lines, A4_COLUMNS))
            }
        }
    }.onFailure { toast("Receipt not printed: ${it.message ?: "printer unavailable"}. The sale is complete; print again from the receipt.") }
}

private fun OfflineCatalogBundle.Status.toControl(onDownload: () -> Unit, onPause: () -> Unit, onRemove: () -> Unit) = OfflineCatalogControl(
    phase = when (phase) {
        OfflineCatalogBundle.Phase.None -> OfflineCatalogPhase.None
        OfflineCatalogBundle.Phase.Downloading -> OfflineCatalogPhase.Downloading
        OfflineCatalogBundle.Phase.Paused -> OfflineCatalogPhase.Paused
        OfflineCatalogBundle.Phase.Ready -> OfflineCatalogPhase.Ready
    },
    release = release,
    doneBytes = doneBytes,
    totalBytes = totalBytes,
    downloadedAt = downloadedAtMs?.let {
        java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT).format(java.util.Date(it))
    },
    message = message,
    onDownload = onDownload,
    onPause = onPause,
    onRemove = onRemove,
)
