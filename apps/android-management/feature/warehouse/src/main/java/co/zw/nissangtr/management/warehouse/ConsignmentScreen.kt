package co.zw.nissangtr.management.warehouse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
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
import co.zw.nissangtr.ui.shop.ShopListCard
import co.zw.nissangtr.ui.shop.ShopPrimaryButton
import co.zw.nissangtr.ui.shop.ShopSecondaryButton
import co.zw.nissangtr.ui.shop.ShopStaffPanel
import co.zw.nissangtr.ui.shop.ShopStaffScreen

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

    ShopStaffScreen(
        title = "Consignment",
        subtitle = "Stock on consignment",
        modifier = modifier,
        onBack = onBack,
    ) {
        ShopStaffPanel(title = "Draft header") {
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
                ShopSecondaryButton(
                    label = "Search customers",
                    onClick = viewModel::searchCustomers,
                    enabled = !state.busy,
                )
                state.customerHits.forEach { c ->
                    ShopListCard(
                        title = c.displayName,
                        subtitle = c.id.take(8) + "…",
                        onClick = { viewModel.selectCustomer(c) },
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
            ShopPrimaryButton(
                label = "Create draft",
                onClick = viewModel::createDraft,
                enabled = !state.busy,
            )
        }

        ShopStaffPanel(title = "Add line / submit") {
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
            ShopSecondaryButton(
                label = "Add line",
                onClick = viewModel::addLine,
                enabled = !state.busy,
            )
            ShopPrimaryButton(
                label = "Submit",
                onClick = { viewModel.submit() },
                enabled = !state.busy,
            )
            ShopSecondaryButton(
                label = "Cancel draft",
                onClick = { viewModel.cancel() },
                enabled = !state.busy,
            )
        }

        ShopStaffPanel(title = "Entries") {
            ShopSecondaryButton(
                label = "Refresh list",
                onClick = viewModel::refresh,
                enabled = !state.busy,
            )
            state.entries.forEach { e ->
                ShopListCard(
                    title = "${e.documentNumber} · ${e.status}",
                    subtitle = "${e.kind}/${e.purpose}",
                    onClick = { viewModel.onEntryIdChange(e.id) },
                    trailing = {
                        if (e.status == "draft") {
                            Row {
                                TextButton(onClick = { viewModel.onEntryIdChange(e.id) }) {
                                    Text("Select")
                                }
                                TextButton(onClick = { viewModel.submit(e.id) }) { Text("Submit") }
                                TextButton(onClick = { viewModel.cancel(e.id) }) { Text("Cancel") }
                            }
                        }
                    },
                )
            }
            state.message?.let { Text(it) }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}
