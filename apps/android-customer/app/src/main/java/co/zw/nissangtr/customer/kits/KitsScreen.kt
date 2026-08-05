package co.zw.nissangtr.customer.kits

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import co.zw.nissangtr.customer.rpc.RpcClient
import co.zw.nissangtr.ui.shop.ShopDefaultScreen
import co.zw.nissangtr.ui.shop.ShopHonestEmpty
import co.zw.nissangtr.ui.shop.ShopListCard
import co.zw.nissangtr.ui.shop.ShopSectionHeader

@Composable
fun KitsScreen(
    rpc: RpcClient,
    onBack: () -> Unit,
    onOpenProduct: (oem: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: KitsViewModel = viewModel(factory = KitsViewModel.factory(rpc)),
) {
    val state by viewModel.state.collectAsState()
    val sharp = MaterialTheme.shapes.extraSmall

    ShopDefaultScreen(
        title = "Service kits",
        subtitle = "Bundles",
        onBack = onBack,
        modifier = modifier,
        loading = state.busy && state.kits.isEmpty(),
    ) {
        Text(
            "Active kits from inventory. Tap a component OEM to open the product page.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = viewModel::refresh,
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth(),
            shape = sharp,
        ) { Text("Refresh kits") }

        if (state.kits.isEmpty() && !state.busy) {
            ShopHonestEmpty(
                title = "No active kits",
                body = "Service kits appear here when published in item_kits.",
            )
        }

        state.kits.forEach { kit ->
            ShopSectionHeader(title = kit.name, actionLabel = kit.sellMode)
            ShopListCard(
                title = kit.oem,
                subtitle = "${kit.components.size} component(s) · tap to open PDP",
                onClick = { onOpenProduct(kit.oem) },
            )
            kit.components.forEach { comp ->
                TextButton(onClick = { onOpenProduct(comp.oem) }) {
                    Text("${comp.oem} · ${comp.name} × ${comp.qty}")
                }
            }
        }

        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}
