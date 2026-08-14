package co.zw.nissangtr.management.warehouse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import co.zw.nissangtr.bridges.escpos.EscPosPrinterBridge
import co.zw.nissangtr.management.rpc.RpcClient
import co.zw.nissangtr.ui.shop.ShopListCard
import co.zw.nissangtr.ui.shop.ShopPrimaryButton
import co.zw.nissangtr.ui.shop.ShopSecondaryButton
import co.zw.nissangtr.ui.shop.ShopStaffPanel
import co.zw.nissangtr.ui.shop.ShopStaffScreen
import co.zw.nissangtr.ui.shop.ShopStatusChip
import co.zw.nissangtr.ui.theme.GtrColors

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

    ShopStaffScreen(
        title = "Bins",
        subtitle = "Labels · Bridge print",
        modifier = modifier,
        onBack = onBack,
    ) {
        ShopStaffPanel(title = "Warehouse") {
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
            ShopSecondaryButton(
                label = "Refresh bins",
                onClick = viewModel::refreshBins,
                enabled = !state.busy,
            )
        }

        ShopStaffPanel(title = "Create bin") {
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
            ShopPrimaryButton(
                label = "Create bin",
                onClick = viewModel::createBin,
                enabled = !state.busy,
            )
        }

        ShopStaffPanel(title = "Bins (${state.bins.size})") {
            state.bins.forEach { bin ->
                ShopListCard(
                    title = "${bin.code} · ${bin.name}",
                    subtitle = "seq ${bin.pickPathSeq} · " +
                        listOfNotNull(bin.aisle, bin.rack, bin.shelf).joinToString("/")
                            .ifBlank { "—" },
                    onClick = {},
                    badges = {
                        if (!bin.isActive) {
                            ShopStatusChip(label = "inactive", background = GtrColors.Mist)
                        }
                    },
                    trailing = {
                        Column {
                            TextButton(
                                onClick = { viewModel.printBinLabel(bin) },
                                enabled = !state.busy && bin.isActive,
                            ) { Text("Print") }
                            TextButton(
                                onClick = { viewModel.deactivateBin(bin.id) },
                                enabled = !state.busy && bin.isActive,
                            ) { Text("Deactivate") }
                        }
                    },
                )
            }
        }

        ShopStaffPanel(title = "Preferred bin assign") {
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
            ShopSecondaryButton(
                label = "Set stock level bin",
                onClick = viewModel::assignPreferredBin,
                enabled = !state.busy,
            )
        }

        ShopStaffPanel(title = "Pick-path hints") {
            OutlinedTextField(
                value = state.pickItemIds,
                onValueChange = viewModel::onPickItemIdsChange,
                label = { Text("Stock item UUIDs (optional, comma-separated)") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.busy,
            )
            ShopPrimaryButton(
                label = "Get pick-path hints",
                onClick = viewModel::loadPickPathHints,
                enabled = !state.busy,
            )
            state.pickHints.forEach { hint ->
                ShopListCard(
                    title = hint.oemPartNumber ?: hint.stockItemId,
                    subtitle = "${hint.binCode ?: "no bin"} (seq ${hint.pickPathSeq ?: "—"}) qty ${hint.quantity}",
                    onClick = {},
                )
            }
        }

        ShopStaffPanel(title = "ESC/POS printer") {
            OutlinedTextField(
                value = state.printerMac,
                onValueChange = viewModel::onPrinterMacChange,
                label = { Text("Printer MAC (bonded)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !state.busy,
            )
            ShopSecondaryButton(
                label = if (state.printerConnected) "Reconnect printer" else "Connect printer",
                onClick = viewModel::connectPrinter,
                enabled = !state.busy,
            )
        }

        state.message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        ShopSecondaryButton(label = "Back", onClick = onBack)
    }
}
