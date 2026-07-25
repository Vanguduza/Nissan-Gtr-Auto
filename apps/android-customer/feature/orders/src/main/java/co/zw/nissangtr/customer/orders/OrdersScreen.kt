package co.zw.nissangtr.customer.orders

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import co.zw.nissangtr.customer.rpc.CustomerOrder
import co.zw.nissangtr.customer.rpc.RpcClient
import co.zw.nissangtr.customer.rpc.RpcNames

/**
 * Thin orders scaffold: list own invoices + [RpcNames.GET_CUSTOMER_ORDER].
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

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Orders", style = MaterialTheme.typography.headlineSmall)
        Text(
            "RPC: ${RpcNames.GET_CUSTOMER_ORDER} (p_invoice_id). List via RLS SELECT.",
            style = MaterialTheme.typography.bodySmall,
        )
        OutlinedButton(
            onClick = viewModel::refresh,
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Refresh invoices") }

        state.invoices.forEach { inv ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !state.busy) { viewModel.loadOrder(inv.id) }
                    .padding(vertical = 4.dp),
            ) {
                Text(
                    inv.documentNumber ?: inv.id,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "${inv.status} · ${inv.currency.rpcValue} ${"%.2f".format(inv.total)} " +
                        "(paid ${"%.2f".format(inv.amountPaid)})",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            HorizontalDivider()
        }

        state.selected?.let { o ->
            Text("Selected order", style = MaterialTheme.typography.titleSmall)
            Text(
                "${o.documentNumber ?: o.invoiceId}\n" +
                    "status=${o.status} fulfillment=${o.fulfillmentMode.rpcValue}\n" +
                    "${o.currency.rpcValue} total=${o.total} open=${o.amountOpen}\n" +
                    "pick=${o.pickListStatus ?: "—"} dn=${o.deliveryNoteStatus ?: "—"}\n" +
                    "activeJob=${o.activeDeliveryJobId ?: "—"}",
                style = MaterialTheme.typography.bodySmall,
            )
            o.activeDeliveryJobId?.let { jobId ->
                Text(
                    "Active delivery — track shows last point + ETA only (no trail).",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedButton(
                    onClick = { onTrackDelivery(jobId, null) },
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Track delivery") }
            }
        }

        OutlinedButton(
            onClick = { onTrackDelivery(null, null) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Track with share token") }

        state.message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        OutlinedButton(onClick = onBack) { Text("Back") }
    }
}