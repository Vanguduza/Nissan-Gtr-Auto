package co.zw.nissangtr.customer.returns

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import co.zw.nissangtr.customer.rpc.RpcClient
import co.zw.nissangtr.customer.rpc.RpcNames
import co.zw.nissangtr.ui.shop.ShopDefaultScreen
import co.zw.nissangtr.ui.shop.ShopHonestEmpty
import co.zw.nissangtr.ui.shop.ShopListCard
import co.zw.nissangtr.ui.shop.ShopSectionHeader

@Composable
fun ReturnsScreen(
    rpc: RpcClient,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ReturnsViewModel = viewModel(factory = ReturnsViewModel.factory(rpc)),
) {
    val state by viewModel.state.collectAsState()
    val sharp = MaterialTheme.shapes.extraSmall

    ShopDefaultScreen(
        title = "Returns",
        subtitle = "Quarantine CN",
        onBack = onBack,
        modifier = modifier,
        loading = state.busy && state.invoices.isEmpty(),
    ) {
        Text(
            "Faulty returns via ${RpcNames.POST_CUSTOMER_RETURN_CREDIT_NOTE}. " +
                "Unit prices are forced server-side from the invoice. " +
                "Returned SKUs go to quarantine — never a direct exchange.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        ShopSectionHeader(title = "Select invoice", actionLabel = null)
        if (state.invoices.isEmpty() && !state.busy) {
            ShopHonestEmpty(
                title = "No invoices yet",
                body = "Return a line from a posted order. Checkout from Cart first.",
            )
        }
        state.invoices.forEach { inv ->
            FilterChip(
                selected = state.selectedInvoiceId == inv.id,
                onClick = { viewModel.selectInvoice(inv.id) },
                enabled = !state.busy,
                label = {
                    Text("${inv.documentNumber ?: inv.id.take(8)} · ${inv.currency.rpcValue}")
                },
                modifier = Modifier.padding(end = 4.dp, bottom = 4.dp),
            )
        }

        if (state.lines.isNotEmpty()) {
            ShopSectionHeader(title = "Lines to return", actionLabel = null)
            state.lines.forEach { line ->
                val selected = state.selectedLineIds.contains(line.id)
                ShopListCard(
                    title = line.oemPartNumber ?: line.stockItemId.take(8),
                    subtitle = buildString {
                        append(line.description ?: "Qty ${line.qty}")
                        append(if (selected) " · Selected" else " · Tap to select")
                    },
                    onClick = { viewModel.toggleLine(line.id) },
                )
            }
            Button(
                onClick = viewModel::submitReturn,
                enabled = !state.busy && state.selectedLineIds.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
                shape = sharp,
            ) { Text("Request return credit note") }
        }

        state.message?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}
