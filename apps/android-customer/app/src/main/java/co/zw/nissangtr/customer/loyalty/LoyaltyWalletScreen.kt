package co.zw.nissangtr.customer.loyalty

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import co.zw.nissangtr.customer.rpc.RpcClient
import co.zw.nissangtr.ui.shop.ShopDefaultScreen
import co.zw.nissangtr.ui.shop.ShopHonestEmpty
import co.zw.nissangtr.ui.shop.ShopSectionHeader

@Composable
fun LoyaltyWalletScreen(
    rpc: RpcClient,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LoyaltyViewModel = viewModel(factory = LoyaltyViewModel.factory(rpc)),
) {
    val state by viewModel.state.collectAsState()
    val sharp = MaterialTheme.shapes.extraSmall

    ShopDefaultScreen(
        title = "Loyalty wallet",
        subtitle = null,
        onBack = onBack,
        modifier = modifier,
        loading = state.busy && state.balance == null,
    ) {
        state.balance?.let { bal ->
            ShopSectionHeader(title = "Balance", actionLabel = null)
            Text(
                "%.0f points".format(bal.pointsBalance),
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                "Currency: ${bal.currency}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "Estimated liability: ${bal.currency} %.2f".format(bal.displayEstimatedLiability()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Liability per point: %.4f".format(bal.liabilityPerPoint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } ?: run {
            if (!state.busy && state.error == null) {
                ShopHonestEmpty(
                    title = "No loyalty account",
                    body = "No loyalty points.",
                )
            }
        }

        ShopSectionHeader(title = "Recent activity", actionLabel = null)
        when {
            state.ledger.isEmpty() && !state.busy && state.error == null -> {
                Text(
                    "No loyalty movements yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            else -> {
                state.ledger.forEach { row ->
                    val pts = if (row.points > 0) "+%.0f".format(row.points) else "%.0f".format(row.points)
                    Text(
                        "${row.movement} · $pts pts · bal %.0f".format(row.pointsBalanceAfter),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    row.reason?.takeIf { it.isNotBlank() }?.let { reason ->
                        Text(
                            reason,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    HorizontalDivider()
                }
            }
        }

        Button(
            onClick = viewModel::refresh,
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth(),
            shape = sharp,
        ) { Text("Refresh") }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}
