package co.zw.nissangtr.customer.returns

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import co.zw.nissangtr.customer.rpc.RpcClient
import co.zw.nissangtr.customer.visual.GtrPremiumColors
import co.zw.nissangtr.customer.visual.PremiumEmptyState
import co.zw.nissangtr.customer.visual.PremiumMessageBanner
import co.zw.nissangtr.customer.visual.PremiumMessageKind
import co.zw.nissangtr.customer.visual.PremiumPrimaryButton
import co.zw.nissangtr.customer.visual.PremiumScreenHeader
import co.zw.nissangtr.customer.visual.PremiumStatusChip
import co.zw.nissangtr.customer.visual.PremiumStatusTone
import co.zw.nissangtr.customer.visual.PremiumSurfaceCard

@Composable
fun ReturnsScreen(
    rpc: RpcClient,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ReturnsViewModel = viewModel(factory = ReturnsViewModel.factory(rpc)),
) {
    val state by viewModel.state.collectAsState()

    Column(
        modifier = modifier.fillMaxSize().background(GtrPremiumColors.Background),
    ) {
        PremiumScreenHeader(
            title = "Returns",
            subtitle = "Select an order and the items you want us to review",
            onBack = onBack,
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.invoices.isEmpty() && !state.busy) {
                item {
                    PremiumEmptyState(
                        title = "No eligible orders",
                        body = "Your completed purchases will appear here when they can be returned.",
                    )
                }
            } else {
                item {
                    Text(
                        "Choose an order",
                        color = GtrPremiumColors.TextPrimary,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.invoices.take(5).forEach { inv ->
                            FilterChip(
                                selected = state.selectedInvoiceId == inv.id,
                                onClick = { viewModel.selectInvoice(inv.id) },
                                enabled = !state.busy,
                                label = { Text(inv.documentNumber ?: "Order") },
                            )
                        }
                    }
                }
            }

            if (state.lines.isNotEmpty()) {
                item {
                    Text(
                        "Choose items",
                        color = GtrPremiumColors.TextPrimary,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                items(state.lines, key = { it.id }) { line ->
                    val selected = state.selectedLineIds.contains(line.id)
                    PremiumSurfaceCard(
                        onClick = { viewModel.toggleLine(line.id) },
                    ) {
                        Row(Modifier.fillMaxWidth()) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    line.description?.trim()?.takeIf { it.isNotEmpty() }
                                        ?: "Purchased part",
                                    color = GtrPremiumColors.TextPrimary,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    "Quantity ${line.qty}",
                                    color = GtrPremiumColors.TextSecondary,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            if (selected) {
                                PremiumStatusChip("Selected", PremiumStatusTone.Premium)
                            }
                        }
                    }
                }
                item {
                    PremiumPrimaryButton(
                        text = if (state.busy) "Submitting…" else "Request return",
                        onClick = viewModel::submitReturn,
                        enabled = !state.busy && state.selectedLineIds.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            state.message?.let { item { PremiumMessageBanner(it, PremiumMessageKind.Success) } }
            state.error?.let { item { PremiumMessageBanner(it, PremiumMessageKind.Error) } }
            item { Spacer(Modifier.padding(bottom = 16.dp)) }
        }
    }
}
