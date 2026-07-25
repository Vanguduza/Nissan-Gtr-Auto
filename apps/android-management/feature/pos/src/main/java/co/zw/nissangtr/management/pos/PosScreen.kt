package co.zw.nissangtr.management.pos

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import co.zw.nissangtr.bridges.escpos.EscPosPrinterBridge
import co.zw.nissangtr.bridges.qr.QrScannerBridge
import co.zw.nissangtr.management.rpc.CatalogSearchMode
import co.zw.nissangtr.management.rpc.CurrencyCode
import co.zw.nissangtr.management.rpc.FulfillmentMode
import co.zw.nissangtr.management.rpc.RpcClient
import co.zw.nissangtr.management.rpc.RpcNames

/**
 * Sales POS workspace: standalone search/catalog/cart/checkout + optional companion.
 * QR via bridges only — never browser/HTML5 scan. No ZIMRA.
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
    viewModel: PosViewModel = viewModel(
        factory = PosViewModel.factory(rpc, qr, printer),
    ),
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(isSalesHome) {
        viewModel.setSalesHome(isSalesHome)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            if (isSalesHome) "POS — Sales till" else "POS — Cart / Checkout",
            style = MaterialTheme.typography.headlineSmall,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = state.mode == PosWorkspaceMode.Till,
                onClick = { viewModel.setMode(PosWorkspaceMode.Till) },
                label = { Text("Till") },
                enabled = !state.busy,
            )
            FilterChip(
                selected = state.mode == PosWorkspaceMode.Companion,
                onClick = { viewModel.setMode(PosWorkspaceMode.Companion) },
                label = { Text("Scan companion") },
                enabled = !state.busy,
            )
        }

        when (state.mode) {
            PosWorkspaceMode.Till -> TillSection(state = state, viewModel = viewModel)
            PosWorkspaceMode.Companion -> CompanionSection(state = state, viewModel = viewModel)
        }

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

        if (isSalesHome) {
            OutlinedButton(
                onClick = { onOpenHub?.invoke() ?: onBack() },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("All modules (hub)") }
        } else {
            OutlinedButton(onClick = onBack) { Text("Back") }
        }
    }
}

@Composable
private fun TillSection(
    state: PosUiState,
    viewModel: PosViewModel,
) {
    Text("Warehouse", style = MaterialTheme.typography.titleSmall)
    if (state.warehouses.isNotEmpty()) {
        state.warehouses.forEach { wh ->
            FilterChip(
                selected = state.warehouseId == wh.id,
                onClick = { viewModel.selectWarehouse(wh) },
                label = { Text("${wh.code} — ${wh.name}") },
                enabled = !state.busy,
            )
        }
    }
    OutlinedTextField(
        value = state.warehouseId,
        onValueChange = viewModel::onWarehouseIdChange,
        label = { Text("Warehouse UUID") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        enabled = !state.busy,
    )
    OutlinedTextField(
        value = state.customerQuery,
        onValueChange = viewModel::onCustomerQueryChange,
        label = { Text("Named customer search") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        enabled = !state.busy,
    )
    OutlinedButton(
        onClick = viewModel::searchCustomers,
        enabled = !state.busy,
        modifier = Modifier.fillMaxWidth(),
    ) { Text("Search customers") }
    state.customerHits.forEach { c ->
        Text(
            c.displayName,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { viewModel.selectCustomer(c) }
                .padding(vertical = 4.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
    if (state.customerName.isNotBlank()) {
        Text(
            "Selected: ${state.customerName}",
            style = MaterialTheme.typography.bodySmall,
        )
        TextButton(onClick = viewModel::clearCustomer, enabled = !state.busy) {
            Text("Clear customer")
        }
    }
    OutlinedTextField(
        value = state.customerId,
        onValueChange = viewModel::onCustomerIdChange,
        label = { Text("Customer UUID (optional / from search)") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        enabled = !state.busy,
    )

    Text("Currency", style = MaterialTheme.typography.titleSmall)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CurrencyCode.entries.forEach { code ->
            FilterChip(
                selected = state.currency == code,
                onClick = { viewModel.onCurrencyChange(code) },
                label = { Text(code.rpcValue) },
                enabled = !state.busy,
            )
        }
    }
    Text("Fulfillment", style = MaterialTheme.typography.titleSmall)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FulfillmentMode.entries.forEach { mode ->
            FilterChip(
                selected = state.fulfillmentMode == mode,
                onClick = { viewModel.onFulfillmentModeChange(mode) },
                label = { Text(mode.rpcValue) },
                enabled = !state.busy,
            )
        }
    }

    Button(
        onClick = viewModel::createCart,
        enabled = !state.busy,
        modifier = Modifier.fillMaxWidth(),
    ) { Text("Open cart") }

    if (state.cartId.isNotBlank()) {
        Text("Cart: ${state.cartId}", style = MaterialTheme.typography.bodySmall)
    }

    HorizontalDivider()
    Text("Parts search / catalog", style = MaterialTheme.typography.titleMedium)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CatalogSearchMode.entries.forEach { mode ->
            FilterChip(
                selected = state.searchMode == mode,
                onClick = { viewModel.onSearchModeChange(mode) },
                label = { Text(mode.rpcValue) },
                enabled = !state.busy,
            )
        }
    }
    OutlinedTextField(
        value = state.searchQuery,
        onValueChange = viewModel::onSearchQueryChange,
        label = { Text("Query (OEM / VIN / model / PNC)") },
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
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = viewModel::searchCatalog, enabled = !state.busy) {
            Text("Search")
        }
        OutlinedButton(onClick = viewModel::tillScanAddLine, enabled = !state.busy) {
            Text("Scan QR → add")
        }
    }

    state.searchHits.forEach { hit ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = !state.busy) { viewModel.addPartFromCatalog(hit) }
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(hit.oemPartNumber, style = MaterialTheme.typography.bodyMedium)
                val meta = listOfNotNull(
                    hit.categoryName,
                    hit.subcategoryName,
                    hit.pncCode?.let { "PNC $it" },
                ).joinToString(" · ")
                if (meta.isNotBlank()) {
                    Text(meta, style = MaterialTheme.typography.bodySmall)
                }
            }
            TextButton(
                onClick = { viewModel.addPartFromCatalog(hit) },
                enabled = !state.busy,
            ) { Text("Add") }
        }
    }

    HorizontalDivider()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text("Cart lines", style = MaterialTheme.typography.titleMedium)
        TextButton(onClick = viewModel::refreshCart, enabled = !state.busy) {
            Text("Refresh")
        }
    }
    if (state.cartLines.isEmpty()) {
        Text("No lines yet — search or scan to add", style = MaterialTheme.typography.bodySmall)
    } else {
        state.cartLines.forEach { line ->
            Text(
                "${line.oemPartNumber ?: line.stockItemId.take(8)}  ×${line.qty}  " +
                    "@ ${line.unitPrice} = ${line.lineTotal}" +
                    if (line.isCoreCharge) " (core)" else "",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }

    HorizontalDivider()
    Text("Checkout — receipt contacts", style = MaterialTheme.typography.titleMedium)
    Text(
        "WhatsApp and/or email for PDF receipt. Unique match binds registered/trade account; " +
            "otherwise walk-in.",
        style = MaterialTheme.typography.bodySmall,
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
        label = { Text("Receipt WhatsApp (E.164)") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        enabled = !state.busy,
    )
    Button(
        onClick = viewModel::checkout,
        enabled = !state.busy,
        modifier = Modifier.fillMaxWidth(),
    ) { Text("Checkout") }

    HorizontalDivider()
    Text("Optional phone companion", style = MaterialTheme.typography.titleMedium)
    Text(
        "Not required for single-device sales. Creates ${RpcNames.CREATE_POS_SCAN_SESSION} code.",
        style = MaterialTheme.typography.bodySmall,
    )
    if (state.pairingCodeDisplay.isNotBlank()) {
        Text(
            "Code: ${state.pairingCodeDisplay}",
            style = MaterialTheme.typography.headlineMedium,
        )
        Text("Expires: ${state.pairingExpiresAt}", style = MaterialTheme.typography.bodySmall)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = viewModel::createPairingSession, enabled = !state.busy) {
            Text("Show pairing code")
        }
        OutlinedButton(onClick = viewModel::revokePairingSession, enabled = !state.busy) {
            Text("Revoke")
        }
    }

    HorizontalDivider()
    Text("ESC/POS printer (optional)", style = MaterialTheme.typography.titleMedium)
    OutlinedTextField(
        value = state.printerMac,
        onValueChange = viewModel::onPrinterMacChange,
        label = { Text("Printer Bluetooth MAC") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        enabled = !state.busy,
    )
    OutlinedButton(
        onClick = viewModel::connectPrinter,
        enabled = !state.busy,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(if (state.printerConnected) "Printer connected — reconnect" else "Connect printer")
    }
    state.lastQrPayload?.let {
        Text("Last QR: $it", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun CompanionSection(
    state: PosUiState,
    viewModel: PosViewModel,
) {
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
        modifier = Modifier.fillMaxWidth(),
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
        modifier = Modifier.fillMaxWidth(),
    ) { Text("Scan inventory QR → add line") }
    state.lastQrPayload?.let {
        Text("Last QR: $it", style = MaterialTheme.typography.bodySmall)
    }
}
