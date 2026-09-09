package co.zw.nissangtr.customer.track

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import co.zw.nissangtr.customer.rpc.RpcClient
import co.zw.nissangtr.customer.rpc.etaLabel
import co.zw.nissangtr.customer.visual.GtrPremiumColors
import co.zw.nissangtr.customer.visual.PremiumMessageBanner
import co.zw.nissangtr.customer.visual.PremiumMessageKind
import co.zw.nissangtr.customer.visual.PremiumPrimaryButton
import co.zw.nissangtr.customer.visual.PremiumScreenHeader
import co.zw.nissangtr.customer.visual.PremiumSecondaryButton
import co.zw.nissangtr.customer.visual.PremiumStatusChip
import co.zw.nissangtr.customer.visual.PremiumStatusTone
import co.zw.nissangtr.customer.visual.PremiumSurfaceCard
import co.zw.nissangtr.customer.visual.R

@Composable
fun DeliveryTrackScreen(
    rpc: RpcClient,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    initialToken: String? = null,
    initialJobId: String? = null,
    sessionKey: Int = 0,
    viewModel: DeliveryTrackViewModel = viewModel(
        key = "track|$sessionKey|${initialToken.orEmpty()}|${initialJobId.orEmpty()}",
        factory = DeliveryTrackViewModel.factory(rpc, initialToken, initialJobId),
    ),
) {
    val state by viewModel.state.collectAsState()

    Column(modifier.fillMaxSize().background(GtrPremiumColors.Background)) {
        PremiumScreenHeader(
            title = "Track Delivery",
            subtitle = "Latest permitted location and ETA",
            onBack = onBack,
        )

        Column(
            Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PremiumSurfaceCard {
                Box(Modifier.fillMaxWidth().height(126.dp)) {
                    Image(
                        painter = painterResource(R.drawable.gtr_support_delivery_van),
                        contentDescription = null,
                        modifier = Modifier.align(Alignment.CenterEnd).height(120.dp),
                        contentScale = ContentScale.Fit,
                    )
                    Column(Modifier.fillMaxWidth(.58f)) {
                        Text(
                            if (state.ended) "Delivery complete" else "YOUR DELIVERY",
                            color = GtrPremiumColors.TextSecondary,
                            style = MaterialTheme.typography.labelMedium,
                        )
                        Text(
                            state.point?.etaLabel()?.let { "ETA $it" }
                                ?: if (state.tracking) "Updating ETA…" else "Ready to track",
                            color = GtrPremiumColors.TextPrimary,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(6.dp))
                        PremiumStatusChip(
                            label = when {
                                state.ended -> "Completed"
                                state.tracking -> "Live"
                                else -> "Not started"
                            },
                            tone = if (state.ended) PremiumStatusTone.Success else PremiumStatusTone.Premium,
                        )
                    }
                }
            }

            if (!state.tracking) {
                Text(
                    "Use the tracking code from your delivery message.",
                    color = GtrPremiumColors.TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = state.tokenDraft,
                    onValueChange = viewModel::onTokenChange,
                    label = { Text("Tracking code") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !state.busy,
                )
                if (state.jobIdDraft.isNotBlank()) {
                    // Owner-job path is preserved when the app opened tracking from an order.
                    Text(
                        "This order is ready to track.",
                        color = GtrPremiumColors.TextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                PremiumPrimaryButton(
                    text = if (state.busy) "Starting…" else "Start tracking",
                    onClick = viewModel::startFromDrafts,
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                state.point?.let { p ->
                    PremiumSurfaceCard {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                if (p.status == "dispatched") "Out for delivery" else p.status,
                                color = GtrPremiumColors.TextPrimary,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                "ETA: ${p.etaLabel() ?: "Updating…"}",
                                color = GtrPremiumColors.TextPrimary,
                            )
                            Text(
                                "Last update: ${p.recordedAt}",
                                color = GtrPremiumColors.TextSecondary,
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Text(
                                "For privacy, Nissan GTR Auto shows the latest permitted location only — never a historical GPS trail.",
                                color = GtrPremiumColors.TextSecondary,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }

                if (!state.ended) {
                    PremiumPrimaryButton(
                        text = if (state.busy) "Refreshing…" else "Refresh",
                        onClick = viewModel::refreshOnce,
                        enabled = !state.busy,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                PremiumSecondaryButton(
                    text = if (state.ended) "Done" else "Stop tracking",
                    onClick = viewModel::stopTracking,
                    modifier = Modifier.fillMaxWidth(),
                )
                state.emptyHint?.let { PremiumMessageBanner(it, PremiumMessageKind.Info) }
            }

            state.message?.let { PremiumMessageBanner(it, PremiumMessageKind.Success) }
            state.error?.let { PremiumMessageBanner(it, PremiumMessageKind.Error) }
        }
    }
}
