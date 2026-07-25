package co.zw.nissangtr.customer.compare

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Compare", style = MaterialTheme.typography.headlineSmall)
        Text(
            if (state.usingGuestStore) {
                "Guest mode — OEMs stored on-device (SharedPreferences)."
            } else {
                "Synced via ${RpcNames.LIST_CUSTOMER_COMPARE_ITEMS} / add / remove."
            },
            style = MaterialTheme.typography.bodySmall,
        )

        Text("Compare list", style = MaterialTheme.typography.titleSmall)
        if (state.items.isEmpty()) {
            Text("No items to compare", style = MaterialTheme.typography.bodyMedium)
        }
        state.items.forEach { item ->
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text(item.oemPartNumber, style = MaterialTheme.typography.titleSmall)
                Text(
                    item.description?.takeIf { it.isNotBlank() } ?: "—",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedButton(
                    onClick = { viewModel.remove(item) },
                    enabled = !state.busy,
                ) { Text("Remove") }
            }
            HorizontalDivider()
        }

        if (state.items.size >= 2) {
            Text("Attribute matrix", style = MaterialTheme.typography.titleSmall)
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
            enabled = !state.busy &&
                state.oem.isNotBlank() &&
                state.items.size < RpcNames.MAX_COMPARE_ITEMS,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Add to compare") }
        Text(
            "Max ${RpcNames.MAX_COMPARE_ITEMS} items",
            style = MaterialTheme.typography.bodySmall,
        )

        OutlinedButton(onClick = viewModel::refresh, enabled = !state.busy) {
            Text("Refresh")
        }
        state.message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        OutlinedButton(onClick = onBack) { Text("Back") }
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
                Surface(tonalElevation = 2.dp) {
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
