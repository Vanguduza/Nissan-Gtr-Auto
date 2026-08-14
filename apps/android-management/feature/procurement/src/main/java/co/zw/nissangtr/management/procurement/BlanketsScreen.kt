package co.zw.nissangtr.management.procurement

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import co.zw.nissangtr.management.rpc.CurrencyCode
import co.zw.nissangtr.management.rpc.RpcClient
import co.zw.nissangtr.ui.shop.ShopOrderBox
import co.zw.nissangtr.ui.shop.ShopPrimaryButton
import co.zw.nissangtr.ui.shop.ShopSecondaryButton
import co.zw.nissangtr.ui.shop.ShopStaffPanel
import co.zw.nissangtr.ui.shop.ShopStaffScreen

@Composable
fun BlanketsScreen(
    rpc: RpcClient,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BlanketsViewModel = viewModel(
        factory = BlanketsViewModel.factory(rpc),
    ),
) {
    val state by viewModel.state.collectAsState()

    ShopStaffScreen(
        title = "Blankets",
        subtitle = "Call-off releases · preferred PO is in-app under Procurement",
        modifier = modifier,
        onBack = onBack,
    ) {
        ShopStaffPanel(title = "Create blanket") {
            OutlinedTextField(
                value = state.supplierId,
                onValueChange = viewModel::onSupplierIdChange,
                label = { Text("Supplier UUID") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !state.busy,
            )
            OutlinedTextField(
                value = state.warehouseId,
                onValueChange = viewModel::onWarehouseIdChange,
                label = { Text("Warehouse UUID") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !state.busy,
            )
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
                value = state.blanketMaxValue,
                onValueChange = viewModel::onBlanketMaxValueChange,
                label = { Text("Blanket max value") },
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
                value = state.lineStockItemId,
                onValueChange = viewModel::onLineStockItemIdChange,
                label = { Text("Line stock item UUID") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !state.busy,
            )
            OutlinedTextField(
                value = state.lineUomId,
                onValueChange = viewModel::onLineUomIdChange,
                label = { Text("Line UOM UUID") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !state.busy,
            )
            OutlinedTextField(
                value = state.lineQty,
                onValueChange = viewModel::onLineQtyChange,
                label = { Text("Line qty") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !state.busy,
            )
            OutlinedTextField(
                value = state.lineUnitPrice,
                onValueChange = viewModel::onLineUnitPriceChange,
                label = { Text("Line unit price") },
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
                label = "Create blanket",
                onClick = viewModel::createBlanket,
                enabled = !state.busy,
            )
        }

        ShopStaffPanel(title = "Blankets") {
            ShopSecondaryButton(
                label = "Refresh blankets",
                onClick = viewModel::refresh,
                enabled = !state.busy,
            )

            state.blankets.forEach { b ->
                ShopOrderBox(
                    title = "${b.documentNumber} · ${b.status}",
                    subtitle = b.supplierName ?: b.supplierId,
                    metaLabel = "Remaining",
                    metaValue = "${"%.2f".format(b.remainingValue)} / ${"%.2f".format(b.blanketMaxValue)} " +
                        b.currency.rpcValue +
                        (b.expectedDate?.let { " · expected $it" } ?: ""),
                    onClick = {},
                    expanded = true,
                    expandedContent = {
                        b.alertMessages().forEach { alert ->
                            Text(alert, color = MaterialTheme.colorScheme.error)
                        }
                        if (b.status == "draft") {
                            ShopSecondaryButton(
                                label = "Submit blanket",
                                onClick = { viewModel.submitBlanket(b.id) },
                                enabled = !state.busy,
                            )
                        }
                        if (b.status == "submitted") {
                            b.lines.forEach { line ->
                                Text(
                                    "L${line.lineNo} ${line.oemPartNumber ?: line.stockItemId} " +
                                        "rem ${line.remainingQty}",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                OutlinedTextField(
                                    value = state.releaseQtyByLineId[line.id] ?: "",
                                    onValueChange = { viewModel.onReleaseQtyChange(line.id, it) },
                                    label = { Text("Release qty") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    enabled = !state.busy,
                                )
                            }
                            ShopPrimaryButton(
                                label = "Call-off release",
                                onClick = { viewModel.releaseCallOff(b) },
                                enabled = !state.busy,
                            )
                        }
                    },
                )
            }

            state.message?.let { Text(it) }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}
