package co.zw.nissangtr.customer.pay

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
import androidx.compose.material3.HorizontalDivider
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
import co.zw.nissangtr.customer.rpc.RpcClient
import co.zw.nissangtr.customer.rpc.RpcNames

/**
 * Thin ContiPay / Paynow intent-create scaffold.
 * No PSP crypto, secrets, or settle — webhook remains service_role.
 */
@Composable
fun PayIntentScreen(
    rpc: RpcClient,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PayIntentViewModel = viewModel(factory = PayIntentViewModel.factory(rpc)),
) {
    val state by viewModel.state.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Pay — intent create", style = MaterialTheme.typography.headlineSmall)
        Text(
            "RPCs: ${RpcNames.CREATE_CUSTOMER_CONTIPAY_INTENT}, " +
                RpcNames.CREATE_CUSTOMER_PAYNOW_INTENT,
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            "Create intent UUID only. No real PSP crypto. Settle via webhook.",
            style = MaterialTheme.typography.bodySmall,
        )

        OutlinedTextField(
            value = state.invoiceId,
            onValueChange = viewModel::onInvoiceIdChange,
            label = { Text("Sales invoice UUID") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !state.busy,
        )

        Text("Own invoices (tap to select)", style = MaterialTheme.typography.titleSmall)
        state.invoices.forEach { inv ->
            val open = inv.total - inv.amountPaid
            Text(
                "${inv.documentNumber ?: inv.id} · open ${"%.2f".format(open)} ${inv.currency.rpcValue}",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !state.busy) { viewModel.selectInvoice(inv.id) }
                    .padding(vertical = 4.dp),
            )
            HorizontalDivider()
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = viewModel::createContipay,
                enabled = !state.busy,
                modifier = Modifier.weight(1f),
            ) { Text("ContiPay") }
            Button(
                onClick = viewModel::createPaynow,
                enabled = !state.busy,
                modifier = Modifier.weight(1f),
            ) { Text("Paynow") }
        }

        state.lastIntentId?.let {
            Text(
                "Last intent (${state.lastProvider}): $it",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        state.message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        OutlinedButton(onClick = onBack) { Text("Back") }
    }
}
