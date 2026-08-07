package co.zw.nissangtr.customer.compare

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import co.zw.nissangtr.customer.rpc.RpcClient
import co.zw.nissangtr.customer.rpc.RpcNames
import co.zw.nissangtr.ui.shop.ShopDefaultScreen
import co.zw.nissangtr.ui.shop.ShopSectionHeader

/**
 * Compare tray — auth RPCs, or [GuestCompareStore] when signed out.
 * Attribute matrix subset: OEM + description + stock id.
 */
@Composable
fun CompareScreen(
    rpc: RpcClient,
    isSignedIn: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val viewModel: CompareViewModel = viewModel(
        factory = CompareViewModel.factory(rpc, context, isSignedIn),
    )
    val state by viewModel.state.collectAsState()
    val sharp = MaterialTheme.shapes.extraSmall

    ShopDefaultScreen(
        title = "Compare",
        subtitle = null,
        onBack = onBack,
        modifier = modifier) {
        if (state.usingGuestStore) {
            Text(
                "Guest mode — OEMs stored on-device.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        ShopSectionHeader(title = "Compare list", actionLabel = null)
        if (state.items.isEmpty()) {
            Text("No items to compare", style = MaterialTheme.typography.bodyMedium)
        }
        state.items.forEach { item ->
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(item.oemPartNumber, style = MaterialTheme.typography.titleSmall)
                Text(
                    item.description?.takeIf { it.isNotBlank() } ?: "—",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(
                    onClick = { viewModel.remove(item) },
                    enabled = !state.busy,
                    shape = sharp,
                ) { Text("Remove") }
            }
            HorizontalDivider()
        }

        if (state.items.size >= 2) {
            ShopSectionHeader(title = "Attribute matrix", actionLabel = null)
            MatrixRow(label = "OEM", values = state.items.map { it.oemPartNumber })
            MatrixRow(
                label = "Description",
                values = state.items.map { it.description?.takeIf { d -> d.isNotBlank() } ?: "—" },
            )
            MatrixRow(
                label = "Stock item",
                values = state.items.map {
                    if (state.usingGuestStore) "local" else "${it.stockItemId.take(8)}…"
                },
            )
        }

        ShopSectionHeader(title = "Add by OEM", actionLabel = null)
        OutlinedTextField(
            value = state.oem,
            onValueChange = viewModel::onOemChange,
            label = { Text("OEM part number") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !state.busy,
            shape = sharp,
        )
        Button(
            onClick = viewModel::add,
            enabled = !state.busy &&
                state.oem.isNotBlank() &&
                state.items.size < RpcNames.MAX_COMPARE_ITEMS,
            modifier = Modifier.fillMaxWidth(),
            shape = sharp,
        ) { Text("Add to compare") }
        Text(
            "Max ${RpcNames.MAX_COMPARE_ITEMS} items",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedButton(onClick = viewModel::refresh, enabled = !state.busy, shape = sharp) {
            Text("Refresh")
        }
        state.message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        OutlinedButton(onClick = onBack, shape = sharp) { Text("Back") }
    }
}

@Composable
private fun MatrixRow(label: String, values: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            values.forEach { value ->
                Surface(
                    tonalElevation = 1.dp,
                    shape = MaterialTheme.shapes.extraSmall,
                ) {
                    Text(
                        value,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .width(120.dp)
                            .padding(8.dp),
                    )
                }
            }
        }
    }
}
