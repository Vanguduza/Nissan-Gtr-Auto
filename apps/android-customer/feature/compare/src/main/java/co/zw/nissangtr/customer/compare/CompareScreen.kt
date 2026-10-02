package co.zw.nissangtr.customer.compare

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import co.zw.nissangtr.customer.rpc.RpcClient
import co.zw.nissangtr.customer.visual.GtrPremiumColors
import co.zw.nissangtr.customer.visual.PremiumEmptyState
import co.zw.nissangtr.customer.visual.PremiumMessageBanner
import co.zw.nissangtr.customer.visual.PremiumMessageKind
import co.zw.nissangtr.customer.visual.PremiumScreenHeader
import co.zw.nissangtr.customer.visual.PremiumSecondaryButton
import co.zw.nissangtr.customer.visual.PremiumSurfaceCard

/**
 * Customer compare is entered from product UI. Manual part-number entry is
 * intentionally absent because customer part-number exposure has been retired.
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
            .background(GtrPremiumColors.Background),
    ) {
        PremiumScreenHeader(
            title = "Compare",
            subtitle = "Compare saved products side by side",
            onBack = onBack,
        )

        if (state.items.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
                PremiumEmptyState(
                    title = "Nothing to compare",
                    body = "Add products to Compare from the shop or product page.",
                )
            }
            return@Column
        }

        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.usingGuestStore) {
                Text(
                    "Guest comparison is stored on this device.",
                    color = GtrPremiumColors.TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                state.items.forEach { item ->
                    PremiumSurfaceCard(
                        modifier = Modifier.width(176.dp),
                    ) {
                        Column {
                            Text(
                                item.description?.takeIf { it.isNotBlank() } ?: "Selected product",
                                color = GtrPremiumColors.TextPrimary,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(Modifier.padding(top = 6.dp))
                            PremiumSecondaryButton(
                                text = "Remove",
                                onClick = { viewModel.remove(item) },
                                enabled = !state.busy,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }

            if (state.items.size >= 2) {
                Text(
                    "Comparison",
                    color = GtrPremiumColors.TextPrimary,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                CompareMatrixRow(
                    label = "Product",
                    values = state.items.map {
                        it.description?.takeIf { d -> d.isNotBlank() } ?: "Selected product"
                    },
                )
            }

            PremiumSecondaryButton(
                text = if (state.busy) "Refreshing…" else "Refresh",
                onClick = viewModel::refresh,
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth(),
            )
            state.message?.let { PremiumMessageBanner(it, PremiumMessageKind.Success) }
            state.error?.let { PremiumMessageBanner(it, PremiumMessageKind.Error) }
        }
    }
}

@Composable
private fun CompareMatrixRow(
    label: String,
    values: List<String>,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            label,
            color = GtrPremiumColors.TextSecondary,
            style = MaterialTheme.typography.labelMedium,
        )
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            values.forEach { value ->
                PremiumSurfaceCard(modifier = Modifier.width(176.dp)) {
                    Text(
                        value,
                        color = GtrPremiumColors.TextPrimary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}
