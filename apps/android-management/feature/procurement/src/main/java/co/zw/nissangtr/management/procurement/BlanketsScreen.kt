package co.zw.nissangtr.management.procurement

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
import co.zw.nissangtr.management.rpc.CurrencyCode
import co.zw.nissangtr.management.rpc.RpcClient
import co.zw.nissangtr.management.rpc.RpcNames

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

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Blanket POs", style = MaterialTheme.typography.headlineSmall)
        Text(
            "RPCs: ${RpcNames.CREATE_BLANKET_PURCHASE_ORDER}, ${RpcNames.SUBMIT_PURCHASE_ORDER}, " +
                "${RpcNames.CREATE_BLANKET_RELEASE}. Alerts: expiry ≤14d / remaining value ≤15% / qty ≤5.",
            style = MaterialTheme.typography.bodySmall,
        )

        Text("Create blanket", style = MaterialTheme.typography.titleMedium)
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
        Button(
            onClick = viewModel::createBlanket,
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Create blanket") }

        HorizontalDivider()
        OutlinedButton(
            onClick = viewModel::refresh,
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Refresh blankets") }

        state.blankets.forEach { b ->
            Column(
                modifier = Modifier.padding(vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    "${b.documentNumber} · ${b.status} · ${b.supplierName ?: b.supplierId}",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    "Remaining ${"%.2f".format(b.remainingValue)} / ${"%.2f".format(b.blanketMaxValue)} " +
                        "${b.currency.rpcValue}" +
                        (b.expectedDate?.let { " · expected $it" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                )
                b.alertMessages().forEach { alert ->
                    Text(alert, color = MaterialTheme.colorScheme.error)
                }
                if (b.status == "draft") {
                    TextButton(
                        onClick = { viewModel.submitBlanket(b.id) },
                        enabled = !state.busy,
                    ) { Text("Submit blanket") }
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
                    Button(
                        onClick = { viewModel.releaseCallOff(b) },
                        enabled = !state.busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Call-off release") }
                }
            }
            HorizontalDivider()
        }

        state.message?.let { Text(it) }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back") }
    }
}
