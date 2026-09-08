package co.zw.nissangtr.management.pos

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import co.zw.nissangtr.bridges.escpos.DocumentPrinterBridge
import co.zw.nissangtr.bridges.escpos.EscPosPrinterBridge
import co.zw.nissangtr.bridges.escpos.PrinterTransport
import co.zw.nissangtr.bridges.qr.QrScannerBridge
import co.zw.nissangtr.management.pos.offline.InMemoryOfflinePosStore
import co.zw.nissangtr.management.pos.offline.OfflinePosConnectivity
import co.zw.nissangtr.management.pos.offline.OfflinePosRpcHolder
import co.zw.nissangtr.management.pos.offline.OfflinePosSyncEngine
import co.zw.nissangtr.management.pos.offline.OfflinePosSyncWorker
import co.zw.nissangtr.management.pos.offline.SqlCipherOfflinePosStore
import co.zw.nissangtr.management.rpc.CurrencyCode
import co.zw.nissangtr.management.rpc.FulfillmentMode
import co.zw.nissangtr.management.rpc.RpcClient
import co.zw.nissangtr.ui.shop.ShopHonestEmpty
import co.zw.nissangtr.ui.shop.ShopListCard
import co.zw.nissangtr.ui.shop.ShopOrderBox
import co.zw.nissangtr.ui.shop.ShopPresenceBanner
import co.zw.nissangtr.ui.shop.ShopRemoteImage
import co.zw.nissangtr.ui.shop.ShopPrimaryButton
import co.zw.nissangtr.ui.shop.ShopSecondaryButton
import co.zw.nissangtr.ui.shop.ShopStaffPanel
import co.zw.nissangtr.ui.shop.ShopStatusChip
import co.zw.nissangtr.ui.theme.GtrColors

/**
 * Canonical Nissan GTR Auto operator POS entry point. The 2026-09-07 design lock is rendered
 * by [PosOperatorWorkspace]: dark role-aware navigation, search/discovery canvas and persistent
 * Current Sale pane on landscape tablets, with a compact stacked fallback. Transaction authority
 * remains in [PosViewModel]/[RpcClient]; QR and receipt printing remain Android bridge-first.
 */
@Composable
fun PosScreen(
    rpc: RpcClient,
    qr: QrScannerBridge,
    printer: EscPosPrinterBridge,
    documentPrinter: DocumentPrinterBridge? = null,
    onBack: () -> Unit,
    isSalesHome: Boolean = false,
    onOpenHub: (() -> Unit)? = null,
    onOpenStaffPortal: (() -> Unit)? = null,
    onOpenKioskSettings: (() -> Unit)? = null,
    operatorLabel: String? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val offlineStore = remember {
        runCatching { SqlCipherOfflinePosStore.open(context) }
            .getOrElse { InMemoryOfflinePosStore() }
    }
    DisposableEffect(rpc) {
        OfflinePosRpcHolder.set(rpc)
        onDispose { OfflinePosRpcHolder.set(null) }
    }
    DisposableEffect(offlineStore) {
        onDispose { offlineStore.close() }
    }
    val offlineEngine = remember(offlineStore) {
        OfflinePosSyncEngine(
            rpc = rpc,
            store = offlineStore,
            deviceId = android.provider.Settings.Secure.getString(
                context.contentResolver,
                android.provider.Settings.Secure.ANDROID_ID,
            ) ?: "tablet",
        )
    }
    val onlineFlow = remember { OfflinePosConnectivity.onlineFlow(context) }
    val viewModel: PosViewModel = viewModel(
        factory = PosViewModel.factory(rpc, qr, printer, documentPrinter, offlineEngine, onlineFlow),
    )
    val state by viewModel.state.collectAsState()

    LaunchedEffect(isSalesHome) {
        viewModel.setSalesHome(isSalesHome)
    }
    LaunchedEffect(state.pendingOfflineSales, state.isOffline) {
        if (!state.isOffline && state.pendingOfflineSales > 0) {
            OfflinePosSyncWorker.enqueue(context)
        }
    }

    PosOperatorWorkspace(
        state = state,
        viewModel = viewModel,
        operatorLabel = operatorLabel,
        onOpenHub = onOpenHub,
        onOpenStaffPortal = onOpenStaffPortal,
        onOpenKioskSettings = onOpenKioskSettings,
        modifier = modifier,
    )

    if (state.managerPrompt != null) {
        ManagerAuthDialog(state = state, viewModel = viewModel)
    }
}

@Composable
internal fun OfflineStatusBanner(
    state: PosUiState,
    viewModel: PosViewModel,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ShopPresenceBanner(
            label = if (state.isOffline) {
                "OFFLINE — cash walk-in sales only · list price from snapshot"
            } else {
                "Online · ${state.pendingOfflineSales} sale(s) queued for sync"
            },
            detail = "Blocked offline: discount / refund / EcoCash·Paynow / companion / quotes / credit customers",
            accent = if (state.isOffline) GtrColors.Danger else GtrColors.Accent,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = viewModel::pullOfflineSnapshot,
                enabled = !state.busy && !state.isOffline,
            ) { Text("Pull snapshot") }
            OutlinedButton(
                onClick = viewModel::syncOfflineQueue,
                enabled = !state.busy && !state.isOffline,
            ) { Text("Sync queue") }
        }
    }
}

/** Warehouse / currency / fulfillment / customer selection + cart-open trigger. */
@Composable
internal fun CartSetupSection(
    state: PosUiState,
    viewModel: PosViewModel,
) {
    ShopStaffPanel(title = "Till setup") {
            if (state.warehouses.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    state.warehouses.forEach { wh ->
                        FilterChip(
                            selected = state.warehouseId == wh.id,
                            onClick = { viewModel.selectWarehouse(wh) },
                            label = { Text(wh.code) },
                            enabled = !state.busy,
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                CurrencyCode.entries.forEach { code ->
                    FilterChip(
                        selected = state.currency == code,
                        onClick = { viewModel.onCurrencyChange(code) },
                        label = { Text(code.rpcValue) },
                        enabled = !state.busy,
                    )
                }
                FulfillmentMode.entries.forEach { mode ->
                    FilterChip(
                        selected = state.fulfillmentMode == mode,
                        onClick = { viewModel.onFulfillmentModeChange(mode) },
                        label = { Text(mode.label) },
                        enabled = !state.busy,
                    )
                }
            }
            if (state.customerName.isNotBlank()) {
                Text(
                    "Customer · ${state.customerName}",
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                Text(
                    "Walk-in sale · use Customer from the POS navigation to attach or create an account",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (state.cartId.isBlank()) {
                ShopPrimaryButton(
                    label = "Open cart",
                    onClick = viewModel::createCart,
                    enabled = !state.busy,
                )
            } else {
                Text("Cart ${state.cartId.take(8)}…", style = MaterialTheme.typography.bodySmall)
            }
    }
}

/** Park / quote / discount / void / refund — cart-level function triggers (manager reauth
 * for discount/void/refund gates through [ManagerAuthDialog]; per-line price override stays
 * attached to its line in [RightCartPane]). */
@Composable
internal fun CartActionTriggers(
    state: PosUiState,
    viewModel: PosViewModel,
) {
    ShopStaffPanel(title = "Cart actions") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = viewModel::parkCart,
                    enabled = !state.busy,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                ) { Text("Park") }
                OutlinedButton(
                    onClick = viewModel::createQuotation,
                    enabled = !state.busy,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                ) { Text("Quote") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = viewModel::requestDiscount,
                    enabled = !state.busy,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                ) { Text("Discount") }
                OutlinedButton(
                    onClick = viewModel::requestVoidCart,
                    enabled = !state.busy,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                ) { Text("Void") }
            }
            if (!state.lastInvoiceId.isNullOrBlank()) {
                OutlinedButton(
                    onClick = viewModel::requestRefund,
                    enabled = !state.busy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                ) { Text("Refund via finance pipeline") }
            }
    }
}

@Composable
internal fun PrinterSection(
    state: PosUiState,
    viewModel: PosViewModel,
) {
    ShopStaffPanel(title = "Receipt printer") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = state.printerTransport == PrinterTransport.BLUETOOTH,
                onClick = { viewModel.onPrinterTransportChange(PrinterTransport.BLUETOOTH) },
                label = { Text("Bluetooth") },
                enabled = !state.busy,
            )
            FilterChip(
                selected = state.printerTransport == PrinterTransport.WIFI,
                onClick = { viewModel.onPrinterTransportChange(PrinterTransport.WIFI) },
                label = { Text("Wi-Fi / LAN") },
                enabled = !state.busy,
            )
        }

        if (state.printerTransport == PrinterTransport.BLUETOOTH) {
            OutlinedButton(
                onClick = viewModel::refreshBondedPrinters,
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Find bonded Bluetooth printers") }
            state.bondedPrinters.forEach { device ->
                Text(
                    "${device.name} · ${device.address}",
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !state.busy) { viewModel.selectBondedPrinter(device) }
                        .padding(vertical = 8.dp),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            OutlinedTextField(
                value = state.printerMac,
                onValueChange = viewModel::onPrinterMacChange,
                label = { Text("Bluetooth MAC") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !state.busy,
            )
        } else {
            OutlinedTextField(
                value = state.printerHost,
                onValueChange = viewModel::onPrinterHostChange,
                label = { Text("Printer IP / hostname") },
                placeholder = { Text("192.168.1.50") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !state.busy,
            )
            OutlinedTextField(
                value = state.printerPort,
                onValueChange = viewModel::onPrinterPortChange,
                label = { Text("TCP port") },
                supportingText = { Text("ESC/POS network printers normally use 9100") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !state.busy,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        }

        OutlinedButton(
            onClick = viewModel::connectPrinter,
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) {
            Text(if (state.printerConnected) "Printer connected" else "Connect printer")
        }
        Text(
            "Supports ESC/POS over Bluetooth SPP or Wi-Fi/LAN raw TCP. Vendor-only receipt protocols can use their Android driver where provided.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    ShopStaffPanel(title = "A4 / desktop printers") {
        Text(
            "Uses Android Print Framework. Mopria and installed HP, Canon, Epson, Brother, Samsung/Xerox or other PrintService drivers can discover and drive compatible A4 printers.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "Printer service/driver installation is admin-only under Settings → Kiosk & device.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = viewModel::printLastInvoiceA4,
            enabled = state.lastA4DocumentLines.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) { Text("Print last invoice on A4") }
    }
}

/** Parked-cart resume + owner-side companion pairing code (create/revoke). */
@Composable
internal fun ParkedAndPairingSection(
    state: PosUiState,
    viewModel: PosViewModel,
) {
    ShopStaffPanel(title = "Park / companion") {
            OutlinedTextField(
                value = state.parkedCartId,
                onValueChange = viewModel::onParkedCartIdChange,
                label = { Text("Resume parked cart UUID") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !state.busy,
            )
            OutlinedButton(
                onClick = viewModel::resumeParkedCart,
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Resume parked") }

            if (state.pairingCodeDisplay.isNotBlank()) {
                Text("Pairing: ${state.pairingCodeDisplay}", style = MaterialTheme.typography.headlineMedium)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = viewModel::createPairingSession, enabled = !state.busy) {
                    Text("Companion code")
                }
                TextButton(onClick = viewModel::revokePairingSession, enabled = !state.busy) {
                    Text("Revoke")
                }
            }
    }
}

/** RIGHT pane: the active cart only — line items, totals, tender/receipt, checkout. */
@Composable
internal fun RightCartPane(
    state: PosUiState,
    viewModel: PosViewModel,
    modifier: Modifier = Modifier,
    onCustomerClick: (() -> Unit)? = null,
) {
    val cartTotal = PosCartLineOps.cartTotal(state.cartLines)
    val paymentOpen = remember { mutableStateOf(false) }

    ShopStaffPanel(modifier = modifier, title = "Current Sale") {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (state.cartLines.isEmpty()) "No items yet" else "${state.cartLines.size} line${if (state.cartLines.size == 1) "" else "s"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(
                    onClick = viewModel::requestVoidCart,
                    enabled = !state.busy && state.cartId.isNotBlank() && state.cartLines.isNotEmpty(),
                ) { Text("Clear", color = MaterialTheme.colorScheme.primary) }
            }

            state.saleVehicle?.let { vehicle ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.small,
                    color = GtrColors.Mist,
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("Vehicle", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(vehicle.displayLabel, style = MaterialTheme.typography.titleSmall)
                        Text("Chassis ${vehicle.chassisCode}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            if (state.cartLines.isEmpty()) {
                ShopHonestEmpty(
                    title = "Empty cart",
                    body = "Search, scan, choose Popular Items, or browse EPC to add lines.",
                )
            } else {
                state.cartLines.forEach { line ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 1.dp,
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Surface(
                                modifier = Modifier.size(58.dp),
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.surfaceVariant,
                            ) {
                                if (!line.imageUrl.isNullOrBlank()) {
                                    ShopRemoteImage(
                                        url = line.imageUrl,
                                        contentDescription = line.description ?: line.oemPartNumber,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                                        placeholderLabel = "Part",
                                    )
                                } else {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Filled.Inventory2,
                                            contentDescription = null,
                                            tint = GtrColors.SilverDim,
                                            modifier = Modifier.size(28.dp),
                                        )
                                    }
                                }
                            }
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    line.description ?: line.oemPartNumber ?: "Part ${line.stockItemId.take(8)}",
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                )
                                val lineOem = line.oemPartNumber
                                if (!line.description.isNullOrBlank() && !lineOem.isNullOrBlank()) {
                                    Text(
                                        lineOem,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Text(
                                    "${state.currency.rpcValue} ${"%.2f".format(line.unitPrice)} each",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (line.isCoreCharge) {
                                    ShopStatusChip(label = "core charge", background = GtrColors.Mist)
                                }
                            }
                            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    "${state.currency.rpcValue} ${"%.2f".format(line.lineTotal)}",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                                )
                                if (!line.isCoreCharge) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        OutlinedButton(
                                            onClick = { viewModel.bumpLineQty(line, -1.0) },
                                            enabled = !state.busy,
                                            modifier = Modifier.size(38.dp),
                                            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                                        ) { Text("−") }
                                        Text(
                                            line.qty.toInt().toString(),
                                            modifier = Modifier.width(34.dp),
                                            style = MaterialTheme.typography.titleSmall,
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                        )
                                        OutlinedButton(
                                            onClick = { viewModel.bumpLineQty(line, 1.0) },
                                            enabled = !state.busy,
                                            modifier = Modifier.size(38.dp),
                                            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                                        ) { Text("+") }
                                    }
                                    TextButton(
                                        onClick = { viewModel.requestPriceOverride(line) },
                                        enabled = !state.busy,
                                    ) { Text("Override price") }
                                }
                            }
                        }
                    }
                }
            }

            if (onCustomerClick != null) {
                ShopSecondaryButton(
                    label = if (state.customerName.isBlank()) "Add Customer (Optional)" else "Customer · ${state.customerName}",
                    onClick = onCustomerClick,
                    enabled = !state.busy,
                )
            }

            HorizontalDivider()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Subtotal", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${state.currency.rpcValue} ${"%.2f".format(cartTotal)}", fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
            }
            Text(
                "Manager-approved adjustments are already reflected in the authoritative line prices.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HorizontalDivider()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Total", style = MaterialTheme.typography.titleLarge)
                Text(
                    "${state.currency.rpcValue} ${"%.2f".format(cartTotal)}",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                )
            }
            ShopPrimaryButton(
                label = "Proceed to Payment  →",
                onClick = { paymentOpen.value = true },
                enabled = !state.busy && state.cartLines.isNotEmpty(),
            )
        }
    }

    if (paymentOpen.value) {
        PaymentDialog(
            state = state,
            viewModel = viewModel,
            total = cartTotal,
            onDismiss = { paymentOpen.value = false },
            onCheckout = {
                viewModel.checkout()
                paymentOpen.value = false
            },
        )
    }
}

@Composable
private fun PaymentDialog(
    state: PosUiState,
    viewModel: PosViewModel,
    total: Double,
    onDismiss: () -> Unit,
    onCheckout: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!state.busy) onDismiss() },
        title = { Text("Payment") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    "Total ${state.currency.rpcValue} ${"%.2f".format(total)}",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                )
                OutlinedTextField(
                    value = state.receiptEmail,
                    onValueChange = viewModel::onReceiptEmailChange,
                    label = { Text("Receipt email") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !state.busy,
                )
                OutlinedTextField(
                    value = state.receiptWhatsapp,
                    onValueChange = viewModel::onReceiptWhatsappChange,
                    label = { Text("Receipt WhatsApp") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !state.busy,
                )
                state.tenderLines.forEachIndexed { index, row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("cash", "ecocash", "paynow").forEach { tender ->
                            FilterChip(
                                selected = row.tender == tender,
                                onClick = { viewModel.onTenderChange(index, tender) },
                                label = { Text(tender) },
                                enabled = !state.busy,
                            )
                        }
                    }
                    OutlinedTextField(
                        value = row.amount,
                        onValueChange = { viewModel.onTenderAmountChange(index, it) },
                        label = { Text("Amount${if (state.tenderLines.size > 1) " · split ${index + 1}" else ""}") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !state.busy,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                    if (state.tenderLines.size > 1) {
                        TextButton(onClick = { viewModel.removeTenderLine(index) }, enabled = !state.busy) {
                            Text("Remove split")
                        }
                    }
                }
                TextButton(onClick = viewModel::addTenderLine, enabled = !state.busy) {
                    Text("Add split tender")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onCheckout,
                enabled = !state.busy && state.cartLines.isNotEmpty(),
            ) { Text("Complete payment") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !state.busy) { Text("Back") }
        },
    )
}

/** Manager reauth overlay for discount / void / refund / price-override — modal by design so
 * it blocks the whole till regardless of which left-pane function triggered it. */
@Composable
private fun ManagerAuthDialog(
    state: PosUiState,
    viewModel: PosViewModel,
) {
    val title = when (state.managerPrompt) {
        ManagerPrompt.Discount -> "Manager approve discount"
        ManagerPrompt.VoidCart -> "Manager approve void"
        ManagerPrompt.Refund -> "Manager approve refund (finance pipeline)"
        ManagerPrompt.PriceOverride -> "Manager approve price override"
        null -> "Manager approve"
    }
    AlertDialog(
        onDismissRequest = { if (!state.busy) viewModel.dismissManagerPrompt() },
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Admin or shop manager reauth — attendant cannot self-approve.",
                    style = MaterialTheme.typography.bodySmall,
                )
                if (state.managerPrompt == ManagerPrompt.Discount) {
                    OutlinedTextField(
                        value = state.discountPercent,
                        onValueChange = viewModel::onDiscountPercentChange,
                        label = { Text("Discount %") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                }
                if (state.managerPrompt == ManagerPrompt.PriceOverride) {
                    OutlinedTextField(
                        value = state.overrideUnitPrice,
                        onValueChange = viewModel::onOverrideUnitPriceChange,
                        label = { Text("New unit price") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                }
                OutlinedTextField(
                    value = state.managerIdentifier,
                    onValueChange = viewModel::onManagerIdentifierChange,
                    label = { Text("Manager emp# / email / phone") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !state.busy,
                )
                OutlinedTextField(
                    value = state.managerPassword,
                    onValueChange = viewModel::onManagerPasswordChange,
                    label = { Text("Manager password") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !state.busy,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = viewModel::confirmManagerAction,
                enabled = !state.busy,
            ) { Text(if (state.busy) "Approving…" else "Approve") }
        },
        dismissButton = {
            OutlinedButton(
                onClick = viewModel::dismissManagerPrompt,
                enabled = !state.busy,
            ) { Text("Cancel") }
        },
    )
}

private fun formatQty(qty: Double): String =
    if (qty == qty.toLong().toDouble()) qty.toLong().toString() else "%.1f".format(qty)

@Composable
internal fun QuotesPanel(
    state: PosUiState,
    viewModel: PosViewModel,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Quotations", style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(
            value = state.quoteNotes,
            onValueChange = viewModel::onQuoteNotesChange,
            label = { Text("Notes (for new quote from cart)") },
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.busy,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("print", "email", "sms", "whatsapp").forEach { ch ->
                FilterChip(
                    selected = state.quoteSendChannel == ch,
                    onClick = { viewModel.onQuoteSendChannelChange(ch) },
                    label = { Text(ch) },
                    enabled = !state.busy,
                )
            }
        }
        OutlinedTextField(
            value = state.quoteSendContact,
            onValueChange = viewModel::onQuoteSendContactChange,
            label = { Text("Send contact (email/phone)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !state.busy,
        )
        Button(
            onClick = viewModel::createQuotation,
            enabled = !state.busy,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
        ) { Text("Create quote from current cart") }

        if (state.quotations.isEmpty()) {
            Text("No quotations yet", style = MaterialTheme.typography.bodySmall)
        }
        state.quotations.forEach { q ->
            ShopOrderBox(
                title = "${q.documentNumber ?: q.id.take(8)} · ${q.status}",
                subtitle = "${q.lineCount} lines · ${q.createdAt ?: ""}",
                onClick = {},
                metaLabel = q.currency.rpcValue,
                metaValue = "%.2f".format(q.total),
                badges = {
                    ShopStatusChip(label = q.status)
                },
                expanded = true,
                expandedContent = {
                    if (q.status in setOf("issued", "sent")) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ShopSecondaryButton(
                                label = "Send",
                                onClick = { viewModel.sendQuotation(q.id) },
                                enabled = !state.busy,
                                modifier = Modifier.weight(1f),
                            )
                            ShopPrimaryButton(
                                label = "Convert → sale",
                                onClick = { viewModel.convertQuotation(q.id) },
                                enabled = !state.busy,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                },
            )
        }
    }
}

@Composable
internal fun CompanionSection(
    state: PosUiState,
    viewModel: PosViewModel,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = state.companionPairingInput,
            onValueChange = viewModel::onCompanionPairingInputChange,
            label = { Text("6-digit pairing code") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !state.busy,
        )
        Button(
            onClick = viewModel::claimCompanionSession,
            enabled = !state.busy,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
        ) { Text("Claim session") }

        if (state.companionSessionId.isNotBlank()) {
            Text("Session: ${state.companionSessionId}", style = MaterialTheme.typography.bodySmall)
        }
        OutlinedTextField(
            value = state.companionCartId,
            onValueChange = viewModel::onCompanionCartIdChange,
            label = { Text("Cart UUID (auto after claim)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !state.busy,
        )
        OutlinedTextField(
            value = state.addQty,
            onValueChange = viewModel::onAddQtyChange,
            label = { Text("Qty") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !state.busy,
        )
        Button(
            onClick = viewModel::companionScanAddLine,
            enabled = !state.busy,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
        ) { Text("Scan inventory QR → add line") }
        state.lastQrPayload?.let {
            Text("Last QR: $it", style = MaterialTheme.typography.bodySmall)
        }
    }
}
