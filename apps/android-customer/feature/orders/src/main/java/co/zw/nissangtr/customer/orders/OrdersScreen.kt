package co.zw.nissangtr.customer.orders

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import co.zw.nissangtr.customer.rpc.RpcClient
import co.zw.nissangtr.ui.shop.ShopDefaultScreen
import co.zw.nissangtr.ui.shop.ShopHonestEmpty
import co.zw.nissangtr.ui.shop.ShopOrderBox
import co.zw.nissangtr.ui.shop.ShopStatusChip
import co.zw.nissangtr.ui.shop.ShopSectionHeader

/**
 * Thin orders scaffold: list own invoices + order detail.
 * When [CustomerOrder.activeDeliveryJobId] is set, Track uses owner job-id path.
 */
@Composable
fun OrdersScreen(
    rpc: RpcClient,
    onBack: () -> Unit,
    onTrackDelivery: (jobId: String?, token: String?) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
    viewModel: OrdersViewModel = viewModel(factory = OrdersViewModel.factory(rpc)),
) {
    val state by viewModel.state.collectAsState()
    val sharp = MaterialTheme.shapes.extraSmall

    ShopDefaultScreen(
        title = "Orders",
        subtitle = null,
        onBack = onBack,
        modifier = modifier,
    ) {
        OutlinedButton(
            onClick = viewModel::refresh,
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth(),
            shape = sharp,
        ) { Text("Refresh invoices") }

        ShopSectionHeader(title = "Invoices", actionLabel = null)
        if (state.invoices.isEmpty()) {
            ShopHonestEmpty(
                title = "No invoices yet",
                body = "No orders.",
            )
        }
        state.invoices.forEach { inv ->
            ShopOrderBox(
                title = inv.documentNumber ?: inv.id,
                subtitle = "${inv.status} · ${inv.currency.rpcValue}",
                metaLabel = "Total",
                metaValue = "%.2f".format(inv.total),
                thumbLabel = inv.currency.rpcValue.take(3),
                badges = {
                    ShopStatusChip(label = inv.status)
                },
                onClick = { if (!state.busy) viewModel.loadOrder(inv.id) },
            )
        }

        state.selected?.let { o ->
            ShopSectionHeader(title = "Selected order", actionLabel = null)
            Text(
                "${o.documentNumber ?: o.invoiceId}\n" +
                    "Status ${o.status} · ${o.fulfillmentMode.rpcValue}\n" +
                    "${o.currency.rpcValue} total=${o.total} open=${o.amountOpen}",
                style = MaterialTheme.typography.bodySmall,
            )
            o.activeDeliveryJobId?.let { jobId ->
                Text(
                    "Active delivery — track shows last point and ETA only.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(
                    onClick = { onTrackDelivery(jobId, null) },
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth(),
                    shape = sharp,
                ) { Text("Track delivery") }
            }
        }

        OutlinedButton(
            onClick = { onTrackDelivery(null, null) },
            modifier = Modifier.fillMaxWidth(),
            shape = sharp,
        ) { Text("Track with share token") }

        state.message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        OutlinedButton(onClick = onBack, shape = sharp) { Text("Back") }
    }
}
