package co.zw.nissangtr.management.pos

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
import co.zw.nissangtr.management.rpc.FulfillmentMode
import co.zw.nissangtr.management.rpc.RpcClient
import co.zw.nissangtr.management.rpc.RpcNames

/**
 * POS scaffold: create cart → add OEM/stock line (typed UUIDs) → checkout.
 * Explicit [CurrencyCode] USD|ZIG. No ZIMRA. No HTML5 QR.
 */
@Composable
fun PosScreen(
    rpc: RpcClient,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PosViewModel = viewModel(
        factory = PosViewModel.factory(rpc),
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
        Text("POS — Cart / Checkout", style = MaterialTheme.typography.headlineSmall)
        Text(
            "RPCs: ${RpcNames.CREATE_POS_CART}, ${RpcNames.ADD_CART_LINE}, " +
                "${RpcNames.CHECKOUT_POS_CART}",
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            "Typed stock_item + UOM UUIDs only. QR via bridges/ later — never browser scan.",
            style = MaterialTheme.typography.bodySmall,
        )

        OutlinedTextField(
            value = state.warehouseId,
            onValueChange = viewModel::onWarehouseIdChange,
            label = { Text("Warehouse UUID") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !state.busy,
        )
        OutlinedTextField(
            value = state.customerId,
            onValueChange = viewModel::onCustomerIdChange,
            label = { Text("Customer UUID (optional)") },
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
        ) { Text("Create cart") }

        HorizontalDivider()
        Text("Add line", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = state.cartId,
            onValueChange = viewModel::onCartIdChange,
            label = { Text("Cart UUID") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !state.busy,
        )
        OutlinedTextField(
            value = state.stockItemId,
            onValueChange = viewModel::onStockItemIdChange,
            label = { Text("Stock item UUID (OEM resolved)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !state.busy,
        )
        OutlinedTextField(
            value = state.uomId,
            onValueChange = viewModel::onUomIdChange,
            label = { Text("UOM UUID") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !state.busy,
        )
        OutlinedTextField(
            value = state.qty,
            onValueChange = viewModel::onQtyChange,
            label = { Text("Qty") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !state.busy,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = viewModel::addLine,
                enabled = !state.busy,
            ) { Text("Add line") }
            Button(
                onClick = viewModel::checkout,
                enabled = !state.busy,
            ) { Text("Checkout") }
        }

        state.message?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium)
        }
        state.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
        }
        OutlinedButton(onClick = onBack) { Text("Back") }
    }
}
