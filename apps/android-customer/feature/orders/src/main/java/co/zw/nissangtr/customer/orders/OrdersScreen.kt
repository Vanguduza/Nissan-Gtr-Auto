package co.zw.nissangtr.customer.orders

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import co.zw.nissangtr.customer.rpc.InvoiceSummary
import co.zw.nissangtr.customer.rpc.RpcClient
import co.zw.nissangtr.customer.visual.GtrPremiumColors
import co.zw.nissangtr.customer.visual.PremiumEmptyState
import co.zw.nissangtr.customer.visual.PremiumMessageBanner
import co.zw.nissangtr.customer.visual.PremiumMessageKind
import co.zw.nissangtr.customer.visual.PremiumOrderTimeline
import co.zw.nissangtr.customer.visual.PremiumPrimaryButton
import co.zw.nissangtr.customer.visual.PremiumScreenHeader
import co.zw.nissangtr.customer.visual.PremiumSecondaryButton
import co.zw.nissangtr.customer.visual.PremiumStatusChip
import co.zw.nissangtr.customer.visual.PremiumStatusTone
import co.zw.nissangtr.customer.visual.PremiumSurfaceCard

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
            .background(GtrPremiumColors.Background),
    ) {
        PremiumScreenHeader(
            title = "Orders",
            subtitle = "History, status and delivery",
            onBack = onBack,
        )

        if (state.invoices.isEmpty() && !state.busy) {
            Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
                PremiumEmptyState(
                    title = "No orders yet",
                    body = "Orders you place will appear here.",
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(state.invoices, key = { it.id }) { invoice ->
                    PremiumInvoiceCard(
                        invoice = invoice,
                        onClick = { if (!state.busy) viewModel.loadOrder(invoice.id) },
                    )
                }

                state.selected?.let { order ->
                    item {
                        Spacer(Modifier.padding(top = 4.dp))
                        PremiumSurfaceCard {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            order.documentNumber ?: "Order",
                                            color = GtrPremiumColors.TextPrimary,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                        Text(
                                            "${order.currency.rpcValue} %.2f".format(order.total),
                                            color = GtrPremiumColors.TextSecondary,
                                        )
                                    }
                                    PremiumStatusChip(
                                        label = order.status,
                                        tone = statusTone(order.status),
                                    )
                                }

                                PremiumOrderTimeline(
                                    labels = listOf("Placed", "Preparing", "Ready / dispatched", "Delivered"),
                                    activeIndex = statusIndex(order.status),
                                )

                                order.activeDeliveryJobId?.let { jobId ->
                                    PremiumPrimaryButton(
                                        text = "Track delivery",
                                        onClick = { onTrackDelivery(jobId, null) },
                                        enabled = !state.busy,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                    Text(
                                        "Tracking shows the latest permitted location and ETA only.",
                                        color = GtrPremiumColors.TextSecondary,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    PremiumSecondaryButton(
                        text = "Track with share token",
                        onClick = { onTrackDelivery(null, null) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item {
                    PremiumSecondaryButton(
                        text = if (state.busy) "Refreshing…" else "Refresh",
                        onClick = viewModel::refresh,
                        enabled = !state.busy,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                state.message?.let { item { PremiumMessageBanner(it, PremiumMessageKind.Success) } }
                state.error?.let { item { PremiumMessageBanner(it, PremiumMessageKind.Error) } }
                item { Spacer(Modifier.padding(bottom = 16.dp)) }
            }
        }
    }
}

@Composable
private fun PremiumInvoiceCard(
    invoice: InvoiceSummary,
    onClick: () -> Unit,
) {
    PremiumSurfaceCard(onClick = onClick) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    invoice.documentNumber ?: "Order",
                    color = GtrPremiumColors.TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "${invoice.currency.rpcValue} %.2f".format(invoice.total),
                    color = GtrPremiumColors.TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            PremiumStatusChip(invoice.status, statusTone(invoice.status))
        }
    }
}

private fun statusTone(status: String): PremiumStatusTone {
    val s = status.lowercase()
    return when {
        "deliver" in s || "paid" in s || "complete" in s -> PremiumStatusTone.Success
        "cancel" in s || "fail" in s -> PremiumStatusTone.Danger
        "process" in s || "prepar" in s || "pending" in s -> PremiumStatusTone.Warning
        else -> PremiumStatusTone.Neutral
    }
}

private fun statusIndex(status: String): Int {
    val s = status.lowercase()
    return when {
        "deliver" in s || "complete" in s -> 3
        "dispatch" in s || "ready" in s -> 2
        "process" in s || "prepar" in s -> 1
        else -> 0
    }
}
