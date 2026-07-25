package co.zw.nissangtr.management.warehouse

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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import co.zw.nissangtr.management.rpc.CurrencyCode
import co.zw.nissangtr.management.rpc.ReconciliationScope
import co.zw.nissangtr.management.rpc.RpcClient
import co.zw.nissangtr.management.rpc.RpcNames
import co.zw.nissangtr.management.rpc.ValuationMethod

/**
 * Warehouse scaffold: receive, dual-auth transfers, cycle-count draft/submit.
 * Explicit USD|ZIG. Typed UUID inputs only — Bridge-First for QR (not browser).
 */
@Composable
fun WarehouseScreen(
    rpc: RpcClient,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WarehouseViewModel = viewModel(
        factory = WarehouseViewModel.factory(rpc),
    ),
) {
    val state by viewModel.state.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Warehouse — Receive / Transfer / Cycle count", style = MaterialTheme.typography.headlineSmall)
        Text(
            "RPCs: ${RpcNames.POST_STOCK_RECEIPT}, ${RpcNames.CREATE_STOCK_TRANSFER}, " +
                "${RpcNames.APPROVE_STOCK_TRANSFER}, ${RpcNames.REJECT_STOCK_TRANSFER}, " +
                "${RpcNames.CREATE_STOCK_RECONCILIATION_DRAFT}, …",
            style = MaterialTheme.typography.bodySmall,
        )

        // --- Receive ---
        Text("Receive", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = state.receiveWarehouseId,
            onValueChange = viewModel::onReceiveWarehouseIdChange,
            label = { Text("To warehouse UUID") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !state.busy,
        )
        OutlinedTextField(
            value = state.receiveNotes,
            onValueChange = viewModel::onReceiveNotesChange,
            label = { Text("Notes (optional)") },
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.busy,
        )
        OutlinedTextField(
            value = state.receiveStockItemId,
            onValueChange = viewModel::onReceiveStockItemIdChange,
            label = { Text("Stock item UUID") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !state.busy,
        )
        OutlinedTextField(
            value = state.receiveUomId,
            onValueChange = viewModel::onReceiveUomIdChange,
            label = { Text("UOM UUID") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !state.busy,
        )
        OutlinedTextField(
            value = state.receiveQty,
            onValueChange = viewModel::onReceiveQtyChange,
            label = { Text("Qty") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !state.busy,
        )
        OutlinedTextField(
            value = state.receiveUnitCost,
            onValueChange = viewModel::onReceiveUnitCostChange,
            label = { Text("Unit cost") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !state.busy,
        )
        Text("Line currency", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CurrencyCode.entries.forEach { code ->
                FilterChip(
                    selected = state.receiveCurrency == code,
                    onClick = { viewModel.onReceiveCurrencyChange(code) },
                    label = { Text(code.rpcValue) },
                    enabled = !state.busy,
                )
            }
        }
        Text("Valuation", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ValuationMethod.entries.forEach { method ->
                FilterChip(
                    selected = state.receiveValuation == method,
                    onClick = { viewModel.onReceiveValuationChange(method) },
                    label = { Text(method.rpcValue) },
                    enabled = !state.busy,
                )
            }
        }
        Button(
            onClick = viewModel::postReceipt,
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Post receipt") }

        HorizontalDivider()
        Text("Transfers (dual-auth)", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = state.fromWarehouseId,
            onValueChange = viewModel::onFromWarehouseIdChange,
            label = { Text("From warehouse UUID") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !state.busy,
        )
        OutlinedTextField(
            value = state.toWarehouseId,
            onValueChange = viewModel::onToWarehouseIdChange,
            label = { Text("To warehouse UUID") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !state.busy,
        )
        OutlinedTextField(
            value = state.transferNotes,
            onValueChange = viewModel::onTransferNotesChange,
            label = { Text("Notes (optional)") },
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.busy,
        )
        OutlinedTextField(
            value = state.transferStockItemId,
            onValueChange = viewModel::onTransferStockItemIdChange,
            label = { Text("Stock item UUID") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !state.busy,
        )
        OutlinedTextField(
            value = state.transferUomId,
            onValueChange = viewModel::onTransferUomIdChange,
            label = { Text("UOM UUID") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !state.busy,
        )
        OutlinedTextField(
            value = state.transferQty,
            onValueChange = viewModel::onTransferQtyChange,
            label = { Text("Qty") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !state.busy,
        )
        Button(
            onClick = viewModel::createTransfer,
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Create transfer") }
        OutlinedTextField(
            value = state.transferEntryId,
            onValueChange = viewModel::onTransferEntryIdChange,
            label = { Text("Transfer entry UUID") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !state.busy,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = viewModel::approveTransfer,
                enabled = !state.busy,
            ) { Text("Approve") }
            OutlinedButton(
                onClick = viewModel::rejectTransfer,
                enabled = !state.busy,
            ) { Text("Reject") }
        }

        HorizontalDivider()
        Text("Cycle count", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = state.reconWarehouseId,
            onValueChange = viewModel::onReconWarehouseIdChange,
            label = { Text("Warehouse UUID") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !state.busy,
        )
        Text("Scope", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ReconciliationScope.entries.forEach { scope ->
                FilterChip(
                    selected = state.reconScope == scope,
                    onClick = { viewModel.onReconScopeChange(scope) },
                    label = { Text(scope.rpcValue) },
                    enabled = !state.busy,
                )
            }
        }
        Text("Draft currency", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CurrencyCode.entries.forEach { code ->
                FilterChip(
                    selected = state.reconCurrency == code,
                    onClick = { viewModel.onReconCurrencyChange(code) },
                    label = { Text(code.rpcValue) },
                    enabled = !state.busy,
                )
            }
        }
        OutlinedTextField(
            value = state.reconExchangeRate,
            onValueChange = viewModel::onReconExchangeRateChange,
            label = { Text("Exchange rate (required for ZIG)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !state.busy,
        )
        OutlinedTextField(
            value = state.reconItemIds,
            onValueChange = viewModel::onReconItemIdsChange,
            label = { Text("Item UUIDs (partial, comma-separated)") },
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.busy,
        )
        OutlinedTextField(
            value = state.reconNotes,
            onValueChange = viewModel::onReconNotesChange,
            label = { Text("Notes (optional)") },
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.busy,
        )
        Button(
            onClick = viewModel::createReconDraft,
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Create draft") }

        OutlinedTextField(
            value = state.reconciliationId,
            onValueChange = viewModel::onReconciliationIdChange,
            label = { Text("Reconciliation UUID") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !state.busy,
        )
        OutlinedTextField(
            value = state.reconLineStockItemId,
            onValueChange = viewModel::onReconLineStockItemIdChange,
            label = { Text("Line stock item UUID") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !state.busy,
        )
        OutlinedTextField(
            value = state.reconCountedQty,
            onValueChange = viewModel::onReconCountedQtyChange,
            label = { Text("Counted qty") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !state.busy,
        )
        Button(
            onClick = viewModel::upsertReconLines,
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Upsert counted lines") }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = viewModel::submitRecon,
                enabled = !state.busy,
            ) { Text("Submit") }
            Button(
                onClick = viewModel::approveRecon,
                enabled = !state.busy,
            ) { Text("Approve") }
        }
        OutlinedTextField(
            value = state.cancelNotes,
            onValueChange = viewModel::onCancelNotesChange,
            label = { Text("Cancel notes (posted only)") },
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.busy,
        )
        OutlinedButton(
            onClick = viewModel::cancelRecon,
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Cancel posted recon") }

        state.message?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium)
        }
        state.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
        }
        OutlinedButton(onClick = onBack) { Text("Back") }
    }
}
