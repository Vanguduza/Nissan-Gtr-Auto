package co.zw.nissangtr.management.procurement

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import co.zw.nissangtr.bridges.qr.QrScannerBridge
import co.zw.nissangtr.management.rpc.CurrencyCode
import co.zw.nissangtr.management.rpc.PROCUREMENT_TRACKER_STEPS
import co.zw.nissangtr.management.rpc.RpcClient
import co.zw.nissangtr.ui.shop.ShopPrimaryButton
import co.zw.nissangtr.ui.shop.ShopSecondaryButton
import co.zw.nissangtr.ui.shop.ShopStaffPanel
import co.zw.nissangtr.ui.shop.ShopStaffScreen

/**
 * H2 — native preferred-supplier PO create/submit.
 * Not RFQ-gated; quoted unit costs are staff-entered.
 * Web GRN / orders detail remains a secondary path outside this screen.
 */
@Composable
fun PreferredPoScreen(
    rpc: RpcClient,
    qr: QrScannerBridge,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PreferredPoViewModel = viewModel(
        factory = PreferredPoViewModel.factory(rpc, qr),
    ),
) {
    val state by viewModel.state.collectAsState()

    ShopStaffScreen(
        title = "Preferred PO",
        subtitle = "Roster · quoted lines · no RFQ-win gate",
        modifier = modifier,
        onBack = onBack,
    ) {
        ShopStaffPanel(title = "Progress") {
            Text(
                state.progressStep.label,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                PROCUREMENT_TRACKER_STEPS.joinToString(" → ") { it.label },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            state.createdPoId?.let { id ->
                Text("PO $id", style = MaterialTheme.typography.bodySmall)
            }
        }

        ShopStaffPanel(title = "Preferred supplier") {
            if (state.suppliers.isEmpty()) {
                Text(
                    "No preferred suppliers loaded.",
                    color = MaterialTheme.colorScheme.error,
                )
            } else {
                state.suppliers.forEach { s ->
                    FilterChip(
                        selected = state.supplierId == s.id,
                        onClick = { viewModel.onSupplierIdChange(s.id) },
                        label = { Text("${s.code} · ${s.name}") },
                        enabled = !state.busy,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            Text("Receiving warehouse", style = MaterialTheme.typography.titleSmall)
            state.warehouses.forEach { w ->
                FilterChip(
                    selected = state.warehouseId == w.id,
                    onClick = { viewModel.onWarehouseIdChange(w.id) },
                    label = { Text("${w.code} · ${w.name}") },
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
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
            OutlinedTextField(
                value = state.exchangeRate,
                onValueChange = viewModel::onExchangeRateChange,
                label = { Text("Exchange rate") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !state.busy,
            )
            OutlinedTextField(
                value = state.expectedDate,
                onValueChange = viewModel::onExpectedDateChange,
                label = { Text("Expected date (YYYY-MM-DD)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !state.busy,
            )
            OutlinedTextField(
                value = state.notes,
                onValueChange = viewModel::onNotesChange,
                label = { Text("Notes") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.busy,
            )
        }

        ShopStaffPanel(title = "Quoted line") {
            OutlinedTextField(
                value = state.lineOem,
                onValueChange = viewModel::onLineOemChange,
                label = { Text("OEM part number") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !state.busy,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ShopSecondaryButton(
                    label = "Resolve OEM",
                    onClick = viewModel::resolveOem,
                    enabled = !state.busy,
                )
                OutlinedButton(
                    onClick = viewModel::scanQrForLine,
                    enabled = !state.busy,
                ) { Text("Scan QR") }
            }
            OutlinedTextField(
                value = state.lineStockItemId,
                onValueChange = viewModel::onLineStockItemIdChange,
                label = { Text("Stock item UUID") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !state.busy,
            )
            OutlinedTextField(
                value = state.lineUomId,
                onValueChange = viewModel::onLineUomIdChange,
                label = { Text("UOM UUID") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !state.busy,
            )
            OutlinedTextField(
                value = state.lineQty,
                onValueChange = viewModel::onLineQtyChange,
                label = { Text("Qty") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !state.busy,
            }
            OutlinedTextField(
                value = state.lineUnitPrice,
                onValueChange = viewModel::onLineUnitPriceChange,
                label = { Text("Quoted unit cost") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !state.busy,
            )
            ShopSecondaryButton(
                label = "Add line",
                onClick = viewModel::addLine,
                enabled = !state.busy,
            )
            state.lines.forEachIndexed { idx, line ->
                Text(
                    "${idx + 1}. ${line.oemPartNumber ?: line.stockItemId} × ${line.qty} @ " +
                        "${"%.2f".format(line.unitPrice)} ${state.currency.rpcValue}",
                    style = MaterialTheme.typography.bodySmall,
                )
                ShopSecondaryButton(
                    label = "Remove line ${idx + 1}",
                    onClick = { viewModel.removeLine(idx) },
                    enabled = !state.busy,
                )
            }
        }

        ShopStaffPanel(title = "Submit") {
            ShopPrimaryButton(
                label = "Create draft PO",
                onClick = viewModel::createDraft,
                enabled = !state.busy && state.lines.isNotEmpty(),
            )
            ShopPrimaryButton(
                label = "Create & submit",
                onClick = viewModel::createAndSubmit,
                enabled = !state.busy && state.lines.isNotEmpty(),
            )
            Text(
                "GRN receive remains on web /procurement (secondary).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            state.message?.let { Text(it) }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}
