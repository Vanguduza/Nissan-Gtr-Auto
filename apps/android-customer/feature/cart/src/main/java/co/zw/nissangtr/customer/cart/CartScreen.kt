package co.zw.nissangtr.customer.cart

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
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
import co.zw.nissangtr.customer.rpc.CurrencyCode
import co.zw.nissangtr.customer.rpc.FulfillmentMode
import co.zw.nissangtr.customer.rpc.RpcClient
import co.zw.nissangtr.customer.rpc.RpcNames

/**
 * Thin cart scaffold: create → add line → checkout.
 * Pricing stays server-side / @gtr/shared — not reimplemented here.
 */
@Composable
fun CartScreen(
    rpc: RpcClient,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CartViewModel = viewModel(factory = CartViewModel.factory(rpc)),
) {
    val state by viewModel.state.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Cart", style = MaterialTheme.typography.headlineSmall)
        Text(
            "RPCs: ${RpcNames.CREATE_CUSTOMER_CART}, ${RpcNames.ADD_CUSTOMER_CART_LINE}, " +
                RpcNames.CHECKOUT_CUSTOMER_CART,
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
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { viewModel.onCurrencyChange(CurrencyCode.USD) },
                enabled = !state.busy,
            ) { Text("USD") }
            OutlinedButton(
                onClick = { viewModel.onCurrencyChange(CurrencyCode.ZIG) },
                enabled = !state.busy,
            ) { Text("ZIG") }
            OutlinedButton(
                onClick = { viewModel.onFulfillmentChange(FulfillmentMode.IMMEDIATE) },
                enabled = !state.busy,
            ) { Text("Collect") }
            OutlinedButton(
                onClick = { viewModel.onFulfillmentChange(FulfillmentMode.DISPATCH) },
                enabled = !state.busy,
            ) { Text("Dispatch") }
        }
        Text(
            "Currency ${state.currency.rpcValue} · ${state.fulfillmentMode.rpcValue}",
            style = MaterialTheme.typography.bodySmall,
        )
        Button(
            onClick = viewModel::createCart,
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Create cart") }

        OutlinedTextField(
            value = state.stockItemId,
            onValueChange = viewModel::onStockItemIdChange,
            label = { Text("Stock item UUID") },
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
        Button(
            onClick = viewModel::addLine,
            enabled = !state.busy && state.cart != null,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Add line") }
        Button(
            onClick = viewModel::checkout,
            enabled = !state.busy && state.cart?.lines?.isNotEmpty() == true,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Checkout") }

        state.cart?.let { cart ->
            Text(
                "Open cart ${cart.id} · ${cart.lines.size} line(s)",
                style = MaterialTheme.typography.bodyMedium,
            )
            cart.lines.forEach { line ->
                Text(
                    "· ${line.oemPartNumber ?: line.stockItemId} × ${line.qty}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        state.lastInvoiceId?.let {
            Text("Last invoice: $it", style = MaterialTheme.typography.bodyMedium)
        }
        state.message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        OutlinedButton(onClick = onBack) { Text("Back") }
    }
}
