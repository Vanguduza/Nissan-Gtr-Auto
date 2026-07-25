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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import co.zw.nissangtr.bridges.escpos.EscPosPrinterBridge
import co.zw.nissangtr.management.rpc.RpcClient
import co.zw.nissangtr.management.rpc.RpcNames

@Composable
fun BinsScreen(
    rpc: RpcClient,
    printer: EscPosPrinterBridge,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BinsViewModel = viewModel(
        factory = BinsViewModel.factory(rpc, printer),
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
        Text("Warehouse bins", style = MaterialTheme.typography.headlineSmall)

        if (state.warehouses.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
        OutlinedTextField(
            value = state.warehouseId,
            onValueChange = viewModel::onWarehouseIdChange,
            label = { Text("Warehouse UUID") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !state.busy,
        )
        OutlinedButton(
            onClick = viewModel::refreshBins,
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Refresh bins") }

        HorizontalDivider()
        Text("Create bin", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = state.code,
            onValueChange = viewModel::onCodeChange,
            label = { Text("Code") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !state.busy,
        )
        OutlinedTextField(
            value = state.name,
            onValueChange = viewModel::onNameChange,
            label = { Text("Name") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !state.busy,
        )
        OutlinedTextField(
            value = state.pickPathSeq,
            onValueChange = viewModel::onPickPathSeqChange,
            label = { Text("Pick path seq") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !state.busy,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = state.aisle,
                onValueChange = viewModel::onAisleChange,
                label = { Text("Aisle") },
                modifier = Modifier.weight(1f),
                singleLine = true,
                enabled = !state.busy,
            )
            OutlinedTextField(
                value = state.rack,
                onValueChange = viewModel::onRackChange,
                label = { Text("Rack") },
                modifier = Modifier.weight(1f),
                singleLine = true,
                enabled = !state.busy,
            )
            OutlinedTextField(
                value = state.shelf,
                onValueChange = viewModel::onShelfChange,
                label = { Text("Shelf") },
                modifier = Modifier.weight(1f),
                singleLine = true,
                enabled = !state.busy,
            )
        }
        Button(
            onClick = viewModel::createBin,
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Create bin") }

        HorizontalDivider()
        Text("Bins (${state.bins.size})", style = MaterialTheme.typography.titleMedium)
        state.bins.forEach { bin ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "${bin.code} · ${bin.name}" +
                        if (!bin.isActive) " (inactive)" else "",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "seq ${bin.pickPathSeq} · " +
                        listOfNotNull(bin.aisle, bin.rack, bin.shelf).joinToString("/")
                            .ifBlank { "—" },
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = { viewModel.printBinLabel(bin) },
                        enabled = !state.busy && bin.isActive,
                    ) { Text("Print label") }
                    TextButton(
                        onClick = { viewModel.deactivateBin(bin.id) },
                        enabled = !state.busy && bin.isActive,
                    ) { Text("Deactivate") }
                }
            }
        }

        HorizontalDivider()
        Text("Preferred bin assign", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = state.assignStockItemId,
            onValueChange = viewModel::onAssignStockItemIdChange,
            label = { Text("Stock item UUID") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !state.busy,
        )
        OutlinedTextField(
            value = state.assignBinId,
            onValueChange = viewModel::onAssignBinIdChange,
            label = { Text("Bin UUID (blank = clear)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !state.busy,
        )
        OutlinedButton(
            onClick = viewModel::assignPreferredBin,
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Set stock level bin") }

        HorizontalDivider()
        Text("Pick-path hints", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = state.pickItemIds,
            onValueChange = viewModel::onPickItemIdsChange,
            label = { Text("Stock item UUIDs (optional, comma-separated)") },
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.busy,
        )
        Button(
            onClick = viewModel::loadPickPathHints,
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Get pick-path hints") }
        state.pickHints.forEach { hint ->
            Text(
                "${hint.oemPartNumber ?: hint.stockItemId} → " +
                    (hint.binCode ?: "no bin") +
                    " (seq ${hint.pickPathSeq ?: "—"}) qty ${hint.quantity}",
                style = MaterialTheme.typography.bodySmall,
            )
        }

        HorizontalDivider()
        Text("ESC/POS printer", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = state.printerMac,
            onValueChange = viewModel::onPrinterMacChange,
            label = { Text("Printer MAC (bonded)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !state.busy,
        )
        OutlinedButton(
            onClick = viewModel::connectPrinter,
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (state.printerConnected) "Reconnect printer" else "Connect printer")
        }

        state.message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("Back")
        }
    }
}
