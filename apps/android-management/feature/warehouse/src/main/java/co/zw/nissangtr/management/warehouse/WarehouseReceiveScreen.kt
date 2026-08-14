package co.zw.nissangtr.management.warehouse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import co.zw.nissangtr.management.rpc.RpcClient
import co.zw.nissangtr.ui.shop.ShopPrimaryButton
import co.zw.nissangtr.ui.shop.ShopSecondaryButton
import co.zw.nissangtr.ui.shop.ShopStaffPanel
import co.zw.nissangtr.ui.shop.ShopStaffScreen
import co.zw.nissangtr.ui.theme.GtrColors

/**
 * Warehouse receive skeleton — inventree-app IA (scan → location → action),
 * GTR steel/chalk/red tokens, [RpcClient.postStockReceipt] adapter.
 * Bridge QR wiring is Phase 2 (Bridge-First Camerax).
 */
@Composable
fun WarehouseReceiveScreen(
    rpc: RpcClient,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    vm: WarehouseReceiveViewModel = viewModel(factory = WarehouseReceiveViewModel.factory(rpc)),
) {
    val state by vm.state.collectAsState()

    ShopStaffScreen(
        title = "Receive",
        subtitle = "InvenTree IA · Phase 1",
        onBack = onBack,
        modifier = modifier,
    ) {
        Text(
            "Step: ${state.step.name}",
            style = MaterialTheme.typography.labelMedium,
            color = GtrColors.SilverDim,
        )
        state.lastReceiptId?.let {
            Text("Last receipt: $it", style = MaterialTheme.typography.bodySmall, color = GtrColors.Accent)
        }
        state.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        when (state.step) {
            ReceiveStep.Identify -> {
                ShopStaffPanel(title = "1 · Identify item") {
                    OutlinedTextField(
                        value = state.oemInput,
                        onValueChange = vm::onOemChange,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("OEM part number") },
                        enabled = !state.busy,
                    )
                    Text(
                        "Phase 2: Bridge QR scan fills OEM. No HTML5/WebView scanner.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    ShopPrimaryButton(
                        label = if (state.busy) "Looking up…" else "Resolve OEM",
                        onClick = vm::resolveOem,
                        enabled = !state.busy && state.oemInput.isNotBlank(),
                    )
                }
            }
            ReceiveStep.Location -> {
                ShopStaffPanel(title = "2 · Location") {
                    state.item?.let { item ->
                        Text("OEM ${item.oemPartNumber}", style = MaterialTheme.typography.titleSmall)
                        Text("stock ${item.stockItemId}", style = MaterialTheme.typography.labelSmall)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        state.warehouses.forEach { wh ->
                            val label = buildString {
                                append(wh.code)
                                if (wh.isQuarantine) append(" (Q)")
                            }
                            FilterChip(
                                selected = state.selectedWarehouseId == wh.id,
                                onClick = { vm.selectWarehouse(wh.id) },
                                label = { Text(label) },
                            )
                        }
                    }
                    ShopPrimaryButton(label = "Continue", onClick = vm::goToAction)
                    ShopSecondaryButton(
                        label = "Back to identify",
                        onClick = vm::resetToIdentify,
                    )
                }
            }
            ReceiveStep.Action -> {
                ShopStaffPanel(title = "3 · Receive") {
                    state.item?.let {
                        Text("Receiving ${it.oemPartNumber}", style = MaterialTheme.typography.titleSmall)
                    }
                    OutlinedTextField(
                        value = state.qty,
                        onValueChange = vm::onQtyChange,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Qty") },
                    )
                    OutlinedTextField(
                        value = state.unitCost,
                        onValueChange = vm::onUnitCostChange,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Unit cost (${state.currency.rpcValue})") },
                    )
                    ShopPrimaryButton(
                        label = if (state.busy) "Posting…" else "Post stock receipt",
                        onClick = vm::postReceipt,
                        enabled = !state.busy,
                    )
                }
            }
        }
    }
}
