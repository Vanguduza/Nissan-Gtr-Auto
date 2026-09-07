package co.zw.nissangtr.management.pos

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
            OutlinedTextField(
                value = state.customerQuery,
                onValueChange = viewModel::onCustomerQueryChange,
                label = { Text("Customer") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !state.busy,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = viewModel::searchCustomers,
                    enabled = !state.busy,
                    modifier = Modifier.height(48.dp),
                ) { Text("Find") }
                if (state.customerName.isNotBlank()) {
                    TextButton(onClick = viewModel::clearCustomer) {
                        Text(state.customerName)
                    }
                }
            }
            state.customerHits.take(4).forEach { c ->
                Text(
                    c.displayName,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.selectCustomer(c) }
                        .padding(vertical = 6.dp),
                    style = MaterialTheme.typography.bodyLarge,
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
    ShopStaffPanel(modifier = modifier, title = "Current sale") {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (state.cartId.isBlank()) "Preparing sale…" else "Cart ${state.cartId.take(8)}…",
                    style = MaterialTheme.typography.bodySmall,
                )
                TextButton(
                    onClick = viewModel::requestVoidCart,
                    enabled = !state.busy && state.cartId.isNotBlank() && state.cartLines.isNotEmpty(),
                ) { Text("Clear sale") }
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

            HorizontalDivider()
            state.cartLines.forEach { line ->
                ShopListCard(
                    title = line.oemPartNumber ?: line.stockItemId.take(8),
                    subtitle = "@ ${line.unitPrice} = ${line.lineTotal}" +
                        if (line.isCoreCharge) " (core)" else "",
                    onClick = {},
                    trailing = if (!line.isCoreCharge) {
                        {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TextButton(
                                    onClick = { viewModel.requestPriceOverride(line) },
                                    enabled = !state.busy,
                                ) { Text("Price") }
                                OutlinedButton(
                                    onClick = { viewModel.bumpLineQty(line, -1.0) },
                                    enabled = !state.busy,
                                    modifier = Modifier.height(40.dp),
                                ) { Text("−") }
                                Text(
                                    "×${line.qty.toInt()}",
                                    modifier = Modifier.padding(horizontal = 6.dp),
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                OutlinedButton(
                                    onClick = { viewModel.bumpLineQty(line, 1.0) },
                                    enabled = !state.busy,
                                    modifier = Modifier.height(40.dp),
                                ) { Text("+") }
                            }
                        }
                    } else {
                        null
                    },
                    badges = if (line.isCoreCharge) {
                        { ShopStatusChip(label = "core", background = GtrColors.Mist) }
                    } else {
                        null
                    },
                )
            }
            if (state.cartLines.isEmpty()) {
                ShopHonestEmpty(
                    title = "Empty cart",
                    body = "Search, scan, choose Popular Spares, or browse EPC to add lines.",
                )
            }
            if (onCustomerClick != null) {
                ShopSecondaryButton(
                    label = if (state.customerName.isBlank()) {
                        "Add Customer (Optional)"
                    } else {
                        "Customer · ${state.customerName}"
                    },
                    onClick = onCustomerClick,
                    enabled = !state.busy,
                )
            }
            Text(
                "Total ${state.currency.rpcValue} ${"%.2f".format(cartTotal)}",
                style = MaterialTheme.typography.headlineSmall,
            )

            HorizontalDivider()
            Text("Tender / receipt", style = MaterialTheme.typography.titleSmall)
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
                    listOf("cash", "ecocash", "paynow").forEach { t ->
                        FilterChip(
                            selected = row.tender == t,
                            onClick = { viewModel.onTenderChange(index, t) },
                            label = { Text(t) },
                            enabled = !state.busy,
                        )
                    }
                }
                OutlinedTextField(
                    value = row.amount,
                    onValueChange = { viewModel.onTenderAmountChange(index, it) },
                    label = { Text("Amount (optional split)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !state.busy,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            }

            ShopPrimaryButton(
                label = "Checkout",
                onClick = viewModel::checkout,
                enabled = !state.busy,
            )
        }
    }
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
