package co.zw.nissangtr.management.pos

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import co.zw.nissangtr.bridges.escpos.EscPosPrinterBridge
import co.zw.nissangtr.bridges.qr.QrScannerBridge
import co.zw.nissangtr.management.pos.offline.InMemoryOfflinePosStore
import co.zw.nissangtr.management.pos.offline.OfflinePosConnectivity
import co.zw.nissangtr.management.pos.offline.OfflinePosRpcHolder
import co.zw.nissangtr.management.pos.offline.OfflinePosSyncEngine
import co.zw.nissangtr.management.pos.offline.OfflinePosSyncWorker
import co.zw.nissangtr.management.pos.offline.SqlCipherOfflinePosStore
import co.zw.nissangtr.management.rpc.CatalogSearchMode
import co.zw.nissangtr.management.rpc.CurrencyCode
import co.zw.nissangtr.management.rpc.FulfillmentMode
import co.zw.nissangtr.management.rpc.RpcClient
import co.zw.nissangtr.ui.shop.ShopHonestEmpty
import co.zw.nissangtr.ui.shop.ShopListCard
import co.zw.nissangtr.ui.shop.ShopOrderBox
import co.zw.nissangtr.ui.shop.ShopPresenceBanner
import co.zw.nissangtr.ui.shop.ShopPrimaryButton
import co.zw.nissangtr.ui.shop.ShopProductCard
import co.zw.nissangtr.ui.shop.ShopSecondaryButton
import co.zw.nissangtr.ui.shop.ShopStaffPanel
import co.zw.nissangtr.ui.shop.ShopStaffScreen
import co.zw.nissangtr.ui.shop.ShopStatusChip
import co.zw.nissangtr.ui.theme.GtrColors
import co.zw.nissangtr.ui.theme.GtrDensity
import co.zw.nissangtr.ui.theme.GtrTheme

/**
 * Tablet counter POS: two-pane landscape layout — LEFT: catalog/search + every POS function
 * entry point (till setup, companion, quotations, discount/void/refund/price-override
 * triggers, offline status); RIGHT: the active cart (line items, totals, tender/checkout).
 * Falls back to a stacked single column below [TWO_PANE_MIN_WIDTH] (phone-width fallback —
 * `feature/pos` has no separate tablet source set, see `app/src/tablet` for the flavor that
 * only overrides kiosk lock-task wiring, not this screen).
 * QR / print via bridges only — never browser/HTML5 / Web Bluetooth. No ZIMRA.
 */
@Composable
fun PosScreen(
    rpc: RpcClient,
    qr: QrScannerBridge,
    printer: EscPosPrinterBridge,
    onBack: () -> Unit,
    isSalesHome: Boolean = false,
    onOpenHub: (() -> Unit)? = null,
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
        factory = PosViewModel.factory(rpc, qr, printer, offlineEngine, onlineFlow),
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

    GtrTheme(density = GtrDensity.Standard) {
        ShopStaffScreen(
            title = "POS",
            subtitle = "Dial UX · Companion · Bridge QR/print",
            modifier = modifier,
            scrollable = false,
            onBack = onBack,
        ) {
            Text(
                "Counter till · Bridge QR / ESC/POS · WH2 pick · No ZIMRA",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            PosWorkspace(
                state = state,
                viewModel = viewModel,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )

            state.lastBindMessage?.let {
                Text("Last bind: $it", style = MaterialTheme.typography.bodyMedium)
            }
            state.lastInvoiceId?.let {
                Text("Last invoice: $it", style = MaterialTheme.typography.bodySmall)
            }
            state.message?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }
            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }

            if (onOpenHub != null) {
                ShopSecondaryButton(
                    label = if (isSalesHome) "All modules (hub)" else "Hub",
                    onClick = { onOpenHub.invoke() },
                    modifier = Modifier.heightIn(min = POS_TOUCH_MIN),
                )
            }
        }

        if (state.managerPrompt != null) {
            ManagerAuthDialog(state = state, viewModel = viewModel)
        }
    }
}

/** Landscape / large-phone dual-pane breakpoint (catalog | cart). */
private val TWO_PANE_MIN_WIDTH = 700.dp

/** Material a11y minimum; POS till must stay finger-friendly. */
private val POS_TOUCH_MIN = 48.dp

/**
 * Outer two-pane split. Left ~60% hosts catalog + every non-cart POS function; right ~40%
 * is always the active cart (line items / totals / tender / checkout) regardless of which
 * left-pane function is open, so a cashier can companion-pair or browse quotations without
 * losing sight of the cart in progress.
 *
 * Breakpoints (static recon):
 * - ≥700dp width → Row catalog|cart (weights 0.6|0.4), each pane verticalScroll only —
 *   no horizontalScroll; chip groups use FlowRow wrap.
 * - &lt;700dp → stacked Column (phone-width fallback).
 */
@Composable
private fun PosWorkspace(
    state: PosUiState,
    viewModel: PosViewModel,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val twoPane = maxWidth >= TWO_PANE_MIN_WIDTH
        if (twoPane) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .heightIn(min = 480.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                LeftFunctionsPane(
                    state = state,
                    viewModel = viewModel,
                    modifier = Modifier
                        .weight(0.6f)
                        .fillMaxHeight(),
                )
                RightCartPane(
                    state = state,
                    viewModel = viewModel,
                    modifier = Modifier
                        .weight(0.4f)
                        .fillMaxHeight(),
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                LeftFunctionsPane(state = state, viewModel = viewModel, modifier = Modifier.fillMaxWidth())
                RightCartPane(state = state, viewModel = viewModel, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

/** Wrapping chip row — avoids horizontal overflow / scroll traps in narrow dual-pane panes. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PosChipFlow(content: @Composable androidx.compose.foundation.layout.FlowRowScope.() -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

/**
 * LEFT pane: offline status, mode switcher (Till / Companion / Quotations), and every
 * function entry point that isn't part of the cart itself (catalog, setup, discount/void/
 * refund/price-override triggers, printer, park/resume, companion pairing).
 */
@Composable
private fun LeftFunctionsPane(
    state: PosUiState,
    viewModel: PosViewModel,
    modifier: Modifier = Modifier,
) {
    ShopStaffPanel(modifier = modifier, title = null) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.isOffline || state.pendingOfflineSales > 0) {
                OfflineStatusBanner(state = state, viewModel = viewModel)
            }

            PosChipFlow {
                FilterChip(
                    selected = state.mode == PosWorkspaceMode.Till,
                    onClick = { viewModel.setMode(PosWorkspaceMode.Till) },
                    label = { Text("Till") },
                    enabled = !state.busy,
                    shape = MaterialTheme.shapes.extraSmall,
                    modifier = Modifier.heightIn(min = POS_TOUCH_MIN),
                )
                FilterChip(
                    selected = state.mode == PosWorkspaceMode.Companion,
                    onClick = { viewModel.setMode(PosWorkspaceMode.Companion) },
                    label = { Text("Scan companion") },
                    enabled = !state.busy && !state.isOffline,
                    shape = MaterialTheme.shapes.extraSmall,
                    modifier = Modifier.heightIn(min = POS_TOUCH_MIN),
                )
                FilterChip(
                    selected = state.showQuotes,
                    onClick = viewModel::toggleQuotes,
                    label = { Text("Quotations") },
                    enabled = !state.busy && !state.isOffline,
                    shape = MaterialTheme.shapes.extraSmall,
                    modifier = Modifier.heightIn(min = POS_TOUCH_MIN),
                )
            }

            when {
                state.showQuotes -> QuotesPanel(state = state, viewModel = viewModel)
                state.mode == PosWorkspaceMode.Till -> TillFunctionsSection(state = state, viewModel = viewModel)
                else -> CompanionSection(state = state, viewModel = viewModel)
            }
        }
    }
}

@Composable
private fun OfflineStatusBanner(
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
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = POS_TOUCH_MIN),
            ) { Text("Pull snapshot") }
            OutlinedButton(
                onClick = viewModel::syncOfflineQueue,
                enabled = !state.busy && !state.isOffline,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = POS_TOUCH_MIN),
            ) { Text("Sync queue") }
        }
    }
}

/** Till mode content for the left pane: setup → catalog → cart-level action triggers. */
@Composable
private fun TillFunctionsSection(
    state: PosUiState,
    viewModel: PosViewModel,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CartSetupSection(state = state, viewModel = viewModel)
        CatalogPane(state = state, viewModel = viewModel, modifier = Modifier.fillMaxWidth())
        CartActionTriggers(state = state, viewModel = viewModel)
        PrinterSection(state = state, viewModel = viewModel)
        ParkedAndPairingSection(state = state, viewModel = viewModel)
    }
}

@Composable
private fun CatalogPane(
    state: PosUiState,
    viewModel: PosViewModel,
    modifier: Modifier = Modifier,
) {
    var showEpc by remember { mutableStateOf(false) }
    ShopStaffPanel(modifier = modifier, title = "Catalog") {
            // EPC hierarchy is online-only; offline cache stays flat catalog_items.
            PosChipFlow {
                CatalogSearchMode.entries.forEach { mode ->
                    FilterChip(
                        selected = !showEpc && state.searchMode == mode,
                        onClick = {
                            showEpc = false
                            viewModel.onSearchModeChange(mode)
                        },
                        label = { Text(mode.rpcValue) },
                        enabled = !state.busy,
                        modifier = Modifier.heightIn(min = POS_TOUCH_MIN),
                    )
                }
                FilterChip(
                    selected = showEpc,
                    onClick = { showEpc = true },
                    label = { Text("EPC") },
                    enabled = !state.busy && !state.isOffline,
                    modifier = Modifier.heightIn(min = POS_TOUCH_MIN),
                )
            }
            if (showEpc) {
                PosEpcBrowseScreen(
                    rpc = viewModel.rpcForEpc(),
                    onBack = { showEpc = false },
                    onSelectOem = { oem ->
                        viewModel.addOemToCart(oem)
                        showEpc = false
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 320.dp, max = 520.dp),
                )
            } else {
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = viewModel::onSearchQueryChange,
                label = { Text("OEM / VIN / model / PNC") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !state.busy,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ShopPrimaryButton(
                    label = "Search",
                    onClick = viewModel::searchCatalog,
                    enabled = !state.busy,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = POS_TOUCH_MIN),
                )
                ShopSecondaryButton(
                    label = "Scan QR",
                    onClick = viewModel::tillScanAddLine,
                    enabled = !state.busy,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = POS_TOUCH_MIN),
                )
            }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 140.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 180.dp, max = 360.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.searchHits, key = { it.oemPartNumber + (it.pncCode ?: "") }) { hit ->
                    val meta = listOfNotNull(
                        hit.categoryName,
                        hit.pncCode?.let { "PNC $it" },
                    ).joinToString(" · ").ifBlank { null }
                    ShopProductCard(
                        title = hit.oemPartNumber,
                        subtitle = meta,
                        priceLabel = hit.saleableQty?.let { "Stock ${formatQty(it)}" } ?: "Stock —",
                        onClick = { if (!state.busy) viewModel.addPartFromCatalog(hit) },
                        modifier = Modifier.fillMaxWidth(),
                        cardHeight = 160.dp,
                    )
                }
            }
            if (state.searchHits.isEmpty()) {
                ShopHonestEmpty(
                    title = "Empty catalog grid",
                    body = "Search or scan to fill the catalog grid",
                )
            }
            }
    }
}

/** Warehouse / currency / fulfillment / customer selection + cart-open trigger. */
@Composable
private fun CartSetupSection(
    state: PosUiState,
    viewModel: PosViewModel,
) {
    ShopStaffPanel(title = "Till setup") {
            if (state.warehouses.isNotEmpty()) {
                PosChipFlow {
                    state.warehouses.forEach { wh ->
                        FilterChip(
                            selected = state.warehouseId == wh.id,
                            onClick = { viewModel.selectWarehouse(wh) },
                            label = { Text(wh.code) },
                            enabled = !state.busy,
                            modifier = Modifier.heightIn(min = POS_TOUCH_MIN),
                        )
                    }
                }
            }
            PosChipFlow {
                CurrencyCode.entries.forEach { code ->
                    FilterChip(
                        selected = state.currency == code,
                        onClick = { viewModel.onCurrencyChange(code) },
                        label = { Text(code.rpcValue) },
                        enabled = !state.busy,
                        modifier = Modifier.heightIn(min = POS_TOUCH_MIN),
                    )
                }
                FulfillmentMode.entries.forEach { mode ->
                    FilterChip(
                        selected = state.fulfillmentMode == mode,
                        onClick = { viewModel.onFulfillmentModeChange(mode) },
                        label = { Text(mode.label) },
                        enabled = !state.busy,
                        modifier = Modifier.heightIn(min = POS_TOUCH_MIN),
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
                    modifier = Modifier.heightIn(min = POS_TOUCH_MIN),
                ) { Text("Find") }
                if (state.customerName.isNotBlank()) {
                    TextButton(
                        onClick = viewModel::clearCustomer,
                        modifier = Modifier.heightIn(min = POS_TOUCH_MIN),
                    ) {
                        Text(state.customerName)
                    }
                }
            }
            state.customerHits.take(4).forEach { c ->
                Text(
                    c.displayName,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = POS_TOUCH_MIN)
                        .clickable { viewModel.selectCustomer(c) }
                        .padding(vertical = 12.dp),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }

            if (state.cartId.isBlank()) {
                ShopPrimaryButton(
                    label = "Open cart",
                    onClick = viewModel::createCart,
                    enabled = !state.busy,
                    modifier = Modifier.heightIn(min = POS_TOUCH_MIN),
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
private fun CartActionTriggers(
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
private fun PrinterSection(
    state: PosUiState,
    viewModel: PosViewModel,
) {
    ShopStaffPanel(title = "ESC/POS (Bluetooth bridge)") {
            OutlinedButton(
                onClick = viewModel::refreshBondedPrinters,
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("List bonded printers") }
            state.bondedPrinters.forEach { device ->
                Text(
                    "${device.name} · ${device.address}",
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !state.busy) {
                            viewModel.selectBondedPrinter(device)
                        }
                        .padding(vertical = 8.dp),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            OutlinedTextField(
                value = state.printerMac,
                onValueChange = viewModel::onPrinterMacChange,
                label = { Text("Printer MAC") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !state.busy,
            )
            OutlinedButton(
                onClick = viewModel::connectPrinter,
                enabled = !state.busy,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
            ) {
                Text(if (state.printerConnected) "Printer connected" else "Connect printer")
            }
    }
}

/** Parked-cart resume + owner-side companion pairing code (create/revoke). */
@Composable
private fun ParkedAndPairingSection(
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
private fun RightCartPane(
    state: PosUiState,
    viewModel: PosViewModel,
    modifier: Modifier = Modifier,
) {
    val cartTotal = PosCartLineOps.cartTotal(state.cartLines)
    ShopStaffPanel(modifier = modifier, title = "Cart") {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                if (state.cartId.isBlank()) {
                    "No cart open — use the left panel to open one"
                } else {
                    "Cart ${state.cartId.take(8)}…"
                },
                style = MaterialTheme.typography.bodySmall,
            )

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
                    body = "Tap catalog tiles to add lines",
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
private fun QuotesPanel(
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
private fun CompanionSection(
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
