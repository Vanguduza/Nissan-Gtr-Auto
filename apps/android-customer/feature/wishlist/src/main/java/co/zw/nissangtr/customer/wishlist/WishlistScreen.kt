package co.zw.nissangtr.customer.wishlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import co.zw.nissangtr.customer.rpc.RpcClient
import co.zw.nissangtr.customer.rpc.RpcNames

/**
 * Wishlist scaffold — list / add / remove / back-in-stock notify / move-to-cart.
 */
@Composable
fun WishlistScreen(
    rpc: RpcClient,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WishlistViewModel = viewModel(factory = WishlistViewModel.factory(rpc)),
) {
    val state by viewModel.state.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Wishlist", style = MaterialTheme.typography.headlineSmall)
        Text(
            "RPCs: ${RpcNames.ADD_CUSTOMER_WISHLIST_ITEM}, " +
                "${RpcNames.REMOVE_CUSTOMER_WISHLIST_ITEM}, " +
                "${RpcNames.SET_WISHLIST_NOTIFY_WHEN_IN_STOCK}, " +
                RpcNames.WISHLIST_MOVE_TO_CART,
            style = MaterialTheme.typography.bodySmall,
        )

        Text("Saved", style = MaterialTheme.typography.titleSmall)
        if (state.items.isEmpty()) {
            Text("No wishlist items", style = MaterialTheme.typography.bodyMedium)
        }
        state.items.forEach { item ->
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(item.oemPartNumber, style = MaterialTheme.typography.titleSmall)
                item.description?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = item.notifyWhenInStock,
                        onCheckedChange = { viewModel.setNotify(item, it) },
                        enabled = !state.busy,
                    )
                    Text("Notify when back in stock", style = MaterialTheme.typography.bodySmall)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { viewModel.moveToCart(item) },
                        enabled = !state.busy,
                    ) { Text("Move to cart") }
                    OutlinedButton(
                        onClick = { viewModel.remove(item) },
                        enabled = !state.busy,
                    ) { Text("Remove") }
                }
            }
            HorizontalDivider()
        }

        Text("Add by OEM", style = MaterialTheme.typography.titleSmall)
        OutlinedTextField(
            value = state.oem,
            onValueChange = viewModel::onOemChange,
            label = { Text("OEM part number") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !state.busy,
        )
        Button(
            onClick = viewModel::add,
            enabled = !state.busy && state.oem.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Add to wishlist") }

        OutlinedButton(onClick = viewModel::refresh, enabled = !state.busy) {
            Text("Refresh")
        }

        state.message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        OutlinedButton(onClick = onBack) { Text("Back") }
    }
}
