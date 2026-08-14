package co.zw.nissangtr.management.credit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import co.zw.nissangtr.management.rpc.RpcClient
import co.zw.nissangtr.ui.shop.ShopHonestEmpty
import co.zw.nissangtr.ui.shop.ShopListCard
import co.zw.nissangtr.ui.shop.ShopPrimaryButton
import co.zw.nissangtr.ui.shop.ShopSecondaryButton
import co.zw.nissangtr.ui.shop.ShopStaffPanel
import co.zw.nissangtr.ui.shop.ShopStaffScreen
import co.zw.nissangtr.ui.shop.ShopStatusChip
import co.zw.nissangtr.ui.theme.GtrColors

@Composable
fun CreditScreen(
    rpc: RpcClient,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CreditViewModel = viewModel(
        factory = CreditViewModel.factory(rpc),
    ),
) {
    val state by viewModel.state.collectAsState()

    ShopStaffScreen(
        title = "Credit",
        subtitle = "AR · limits",
        modifier = modifier,
        onBack = onBack,
    ) {
        ShopStaffPanel(title = "Find customer") {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::onQueryChange,
                label = { Text("Customer name or UUID") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !state.busy,
            )
            ShopSecondaryButton(
                label = "Search customers",
                onClick = viewModel::search,
                enabled = !state.busy,
            )
            if (state.hits.isEmpty() && state.query.isNotBlank()) {
                ShopHonestEmpty(title = "No matches", body = "Try another name or UUID")
            }
            state.hits.forEach { c ->
                ShopListCard(
                    title = c.displayName,
                    subtitle = c.id.take(8) + "…",
                    onClick = { viewModel.selectCustomer(c) },
                )
            }
        }

        ShopStaffPanel(title = "Credit account") {
            OutlinedTextField(
                value = state.customerId,
                onValueChange = viewModel::onCustomerIdChange,
                label = { Text("Customer UUID") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !state.busy,
            )
            if (state.customerName.isNotBlank()) {
                Text(state.customerName, style = MaterialTheme.typography.bodyMedium)
            }
            ShopSecondaryButton(
                label = "Load credit",
                onClick = viewModel::load,
                enabled = !state.busy,
            )

            state.snapshot?.let { snap ->
                ShopListCard(
                    title = "Limit ${snap.displayCreditLimit()} · Open ${snap.displayOpenBalance()}",
                    subtitle = "${snap.currency.rpcValue}",
                    onClick = {},
                    badges = {
                        ShopStatusChip(
                            label = if (snap.creditHold) "HOLD" else "OK",
                            background = if (snap.creditHold) GtrColors.Danger else GtrColors.Accent,
                        )
                    },
                )
            }

            OutlinedTextField(
                value = state.creditLimitInput,
                onValueChange = viewModel::onCreditLimitInputChange,
                label = { Text("Credit limit") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !state.busy,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.creditHold,
                    onClick = { viewModel.onCreditHoldChange(true) },
                    label = { Text("Hold ON") },
                    enabled = !state.busy,
                )
                FilterChip(
                    selected = !state.creditHold,
                    onClick = { viewModel.onCreditHoldChange(false) },
                    label = { Text("Hold OFF") },
                    enabled = !state.busy,
                )
            }
            ShopPrimaryButton(
                label = "Save limit + hold",
                onClick = viewModel::saveLimitAndHold,
                enabled = !state.busy,
            )
            ShopSecondaryButton(
                label = "Toggle hold only",
                onClick = viewModel::toggleHoldOnly,
                enabled = !state.busy,
            )

            state.message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
        ShopSecondaryButton(label = "Back", onClick = onBack)
    }
}
