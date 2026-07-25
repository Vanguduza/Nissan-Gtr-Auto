package co.zw.nissangtr.management.warehouse

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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import co.zw.nissangtr.management.rpc.ConsignmentKind
import co.zw.nissangtr.management.rpc.ConsignmentPurpose
import co.zw.nissangtr.management.rpc.CurrencyCode
import co.zw.nissangtr.management.rpc.RpcClient
@Composable
fun ConsignmentScreen(
    rpc: RpcClient,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ConsignmentViewModel = viewModel(
        factory = ConsignmentViewModel.factory(rpc),
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
        Text("Consignment", style = MaterialTheme.typography.headlineSmall)

        Text("Kind", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ConsignmentKind.entries.forEach { k ->
                FilterChip(
                    selected = state.kind == k,
                    onClick = { viewModel.onKindChange(k) },
                    label = { Text(k.rpcValue) },
                    enabled = !state.busy,
                )
            }
        }
        Text("Purpose", style = MaterialTheme.typography.titleSmall)
        ConsignmentPurpose.entries.forEach { p ->
            FilterChip(
                selected = state.purpose == p,
                onClick = { viewModel.onPurposeChange(p) },
                label = { Text(p.rpcValue) },
                enabled = !state.busy,
            )
        }

        OutlinedTextField(
            value = state.warehouseId,
            onValueChange = viewModel::onWarehouseIdChange,
            label = { Text("Warehouse UUID") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !state.busy,
        )
        if (state.kind == ConsignmentKind.SUPPLIER_OWNED) {
            OutlinedTextField(
                value = state.supplierId,
                onValueChange = viewModel::onSupplierIdChange,
                label = { Text("Supplier UUID") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !state.busy,
            )
        } else {
            OutlinedTextField(
                value = state.customerQuery,
                onValueChange = viewModel::onCustomerQueryChange,
                label = { Text("Customer search") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !state.busy,
            )
            OutlinedButton(
                onClick = viewModel::searchCustomers,
                enabled = !state.busy,
            ) { Text("Search customers") }
            state.customerHits.forEach { c ->
                Text(
                    c.displayName,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.selectCustomer(c) }
                        .padding(vertical = 4.dp),
                )
            }
            OutlinedTextField(
                value = state.customerId,
                onValueChange = viewModel::onCustomerIdChange,
                label = { Text("Customer UUID") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !state.busy,
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
            label = { Text("Exchange rate (required for ZIG)") },
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
        Button(
            onClick = viewModel::createDraft,
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Create draft") }

        HorizontalDivider()
        Text("Add line / submit", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = state.entryId,
            onValueChange = viewModel::onEntryIdChange,
            label = { Text("Entry UUID") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !state.busy,
        )
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
        )
        OutlinedTextField(
            value = state.lineUnitCost,
            onValueChange = viewModel::onLineUnitCostChange,
            label = { Text("Unit cost") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !state.busy,
        )
        OutlinedTextField(
            value = state.lineUnitPrice,
            onValueChange = viewModel::onLineUnitPriceChange,
            label = { Text("Unit price (sale)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !state.busy,
        )
        OutlinedButton(
            onClick = viewModel::addLine,
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Add line") }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { viewModel.submit() }, enabled = !state.busy) { Text("Submit") }
            OutlinedButton(onClick = { viewModel.cancel() }, enabled = !state.busy) {
                Text("Cancel draft")
            }
        }

        HorizontalDivider()
        OutlinedButton(onClick = viewModel::refresh, enabled = !state.busy) { Text("Refresh list") }
        state.entries.forEach { e ->
            Column(Modifier.padding(vertical = 4.dp)) {
                Text(
                    "${e.documentNumber} · ${e.status} · ${e.kind}/${e.purpose}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (e.status == "draft") {
                    Row {
                        TextButton(onClick = {
                            viewModel.onEntryIdChange(e.id)
                        }) { Text("Select") }
                        TextButton(onClick = { viewModel.submit(e.id) }) { Text("Submit") }
                        TextButton(onClick = { viewModel.cancel(e.id) }) { Text("Cancel") }
                    }
                }
            }
        }

        state.message?.let { Text(it) }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back") }
    }
}
