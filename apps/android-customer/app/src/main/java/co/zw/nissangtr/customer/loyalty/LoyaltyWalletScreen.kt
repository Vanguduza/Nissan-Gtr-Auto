package co.zw.nissangtr.customer.loyalty

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import co.zw.nissangtr.customer.visual.PremiumSurfaceCard

@Composable
fun LoyaltyWalletScreen(
    rpc: RpcClient,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LoyaltyViewModel = viewModel(factory = LoyaltyViewModel.factory(rpc)),
) {
    val state by viewModel.state.collectAsState()

    Column(modifier.fillMaxSize().background(GtrPremiumColors.Background)) {
        PremiumScreenHeader(
            title = "GTR Rewards",
            subtitle = "Benefits earned from eligible purchases",
            onBack = onBack,
        )
        Column(
            Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            state.balance?.let { bal ->
                PremiumSurfaceCard {
                    Column(Modifier.padding(vertical = 6.dp)) {
                        Text(
                            "YOUR POINTS",
                            color = GtrPremiumColors.TextSecondary,
                            style = MaterialTheme.typography.labelMedium,
                        )
                        Text(
                            "%.0f".format(bal.pointsBalance),
                            color = GtrPremiumColors.TextPrimary,
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.padding(top = 4.dp))
                        Text(
                            "Use available rewards on eligible Nissan GTR Auto purchases.",
                            color = GtrPremiumColors.TextSecondary,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                PremiumSurfaceCard {
                    Column {
                        Text(
                            "How rewards work",
                            color = GtrPremiumColors.TextPrimary,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "Points accrue according to the active rewards programme. Eligible redemption is shown during checkout.",
                            color = GtrPremiumColors.TextSecondary,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            } ?: run {
                if (!state.busy && state.error == null) {
                    PremiumEmptyState(
                        title = "No rewards yet",
                        body = "Eligible purchases will build your rewards balance.",
                    )
                }
            }

            PremiumPrimaryButton(
                text = if (state.busy) "Refreshing…" else "Refresh rewards",
                onClick = viewModel::refresh,
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth(),
            )
            state.error?.let { PremiumMessageBanner(it, PremiumMessageKind.Error) }
        }
    }
}
