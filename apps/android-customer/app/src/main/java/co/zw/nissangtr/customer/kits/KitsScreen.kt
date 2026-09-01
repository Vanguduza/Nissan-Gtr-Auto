package co.zw.nissangtr.customer.kits

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
fun KitsScreen(
    rpc: RpcClient,
    onBack: () -> Unit,
    onOpenProduct: (oem: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: KitsViewModel = viewModel(factory = KitsViewModel.factory(rpc)),
) {
    val state by viewModel.state.collectAsState()

    Column(modifier.fillMaxSize().background(GtrPremiumColors.Background)) {
        PremiumScreenHeader(
            title = "Service Kits",
            subtitle = "Everything needed for common maintenance in one place",
            onBack = onBack,
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.kits.isEmpty() && !state.busy) {
                item {
                    PremiumEmptyState(
                        title = "No active service kits",
                        body = "Available maintenance kits will appear here.",
                    )
                }
            }

            items(state.kits, key = { it.kitId }) { kit ->
                PremiumSurfaceCard {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth()) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    kit.name,
                                    color = GtrPremiumColors.TextPrimary,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    "${kit.components.size} included item(s)",
                                    color = GtrPremiumColors.TextSecondary,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            PremiumStatusChip("Kit", PremiumStatusTone.Premium)
                        }

                        kit.components.forEach { component ->
                            PremiumSurfaceCard(
                                onClick = { onOpenProduct(component.oem) },
                            ) {
                                Row(Modifier.fillMaxWidth()) {
                                    Text(
                                        component.name,
                                        color = GtrPremiumColors.TextPrimary,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Text(
                                        "× ${component.qty}",
                                        color = GtrPremiumColors.TextSecondary,
                                    )
                                }
                            }
                        }

                        PremiumPrimaryButton(
                            text = "View kit",
                            onClick = { onOpenProduct(kit.oem) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            item {
                PremiumPrimaryButton(
                    text = if (state.busy) "Refreshing…" else "Refresh kits",
                    onClick = viewModel::refresh,
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            state.error?.let { item { PremiumMessageBanner(it, PremiumMessageKind.Error) } }
        }
    }
}
