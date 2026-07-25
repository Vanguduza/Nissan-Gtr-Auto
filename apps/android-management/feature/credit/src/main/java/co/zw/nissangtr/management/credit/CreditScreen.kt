package co.zw.nissangtr.management.credit

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import co.zw.nissangtr.management.rpc.RpcClient
import co.zw.nissangtr.management.rpc.RpcNames

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

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("B2B credit", style = MaterialTheme.typography.headlineSmall)
        Text(
            "RPC: ${RpcNames.SET_CUSTOMER_CREDIT} (admin|sales|finance). " +
                "Shows limit / hold / open_balance with explicit currency. No tax.",
            style = MaterialTheme.typography.bodySmall,
        )

        OutlinedTextField(
            value = state.query,
            onValueChange = viewModel::onQueryChange,
            label = { Text("Customer name or UUID") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !state.busy,
        )
        OutlinedButton(
            onClick = viewModel::search,
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Search customers") }
        state.hits.forEach { c ->
            Text(
                c.displayName,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.selectCustomer(c) }
                    .padding(vertical = 6.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
        }

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
        OutlinedButton(
            onClick = viewModel::load,
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Load credit") }

        state.snapshot?.let { snap ->
            Text(
                "Limit ${snap.creditLimit} · Open ${snap.openBalance} · " +
                    "${snap.currency.rpcValue} · hold=${snap.creditHold}",
                style = MaterialTheme.typography.bodyMedium,
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
        Button(
            onClick = viewModel::saveLimitAndHold,
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Save limit + hold") }
        OutlinedButton(
            onClick = viewModel::toggleHoldOnly,
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Toggle hold only") }

        state.message?.let { Text(it) }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back") }
    }
}
