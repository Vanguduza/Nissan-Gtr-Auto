package co.zw.nissangtr.customer.pay

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import co.zw.nissangtr.customer.rpc.RpcClient
import co.zw.nissangtr.customer.rpc.RpcNames
import co.zw.nissangtr.ui.shop.ShopGtrPayMethod
import co.zw.nissangtr.ui.shop.ShopPaymentMethodList
import co.zw.nissangtr.ui.shop.ShopDefaultScreen
import co.zw.nissangtr.ui.shop.ShopSectionHeader

/**
 * Thin ContiPay / Paynow intent-create scaffold.
 * No PSP crypto, secrets, or settle — webhook remains service_role.
 */
@Composable
fun PayIntentScreen(
    rpc: RpcClient,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    initialInvoiceId: String? = null,
    viewModel: PayIntentViewModel = viewModel(factory = PayIntentViewModel.factory(rpc)),
) {
    val state by viewModel.state.collectAsState()
    val sharp = MaterialTheme.shapes.extraSmall
    var payMethod by remember { mutableStateOf(ShopGtrPayMethod.ContiPay) }

    LaunchedEffect(initialInvoiceId) {
        val id = initialInvoiceId?.trim().orEmpty()
        if (id.isNotEmpty()) viewModel.onInvoiceIdChange(id)
    }

    ShopDefaultScreen(
        title = "Secure payment",
        subtitle = "ContiPay · Paynow · EcoCash",
        onBack = onBack,
        modifier = modifier) {
        Text(
            "RPCs: ${RpcNames.CREATE_CUSTOMER_CONTIPAY_INTENT}, " +
                "${RpcNames.CREATE_CUSTOMER_PAYNOW_INTENT}, " +
                RpcNames.CREATE_CUSTOMER_ECOCASH_INTENT,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "EcoCash direct is separate from ContiPay/Paynow. Enter the EcoCash MSISDN for the PIN prompt.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

                ShopPaymentMethodList(
            selected = payMethod,
            onSelect = { payMethod = it },
        )

        ShopSectionHeader(title = "Invoice", actionLabel = null)
        OutlinedTextField(
            value = state.invoiceId,
            onValueChange = viewModel::onInvoiceIdChange,
            label = { Text("Sales invoice UUID") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !state.busy,
            shape = sharp,
        )

        OutlinedTextField(
            value = state.ecocashMsisdn,
            onValueChange = viewModel::onEcocashMsisdnChange,
            label = { Text("EcoCash number (07… / +263…)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !state.busy,
            shape = sharp,
        )

        ShopSectionHeader(title = "Own invoices", actionLabel = null)
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

        ShopSectionHeader(title = "Create intent", actionLabel = null)
        Button(
            onClick = {
                when (payMethod) {
                    ShopGtrPayMethod.ContiPay -> viewModel.createContipay()
                    ShopGtrPayMethod.Paynow -> viewModel.createPaynow()
                    ShopGtrPayMethod.EcoCash -> viewModel.createEcocash()
                }
            },
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth(),
            shape = sharp,
        ) {
            Text(
                when (payMethod) {
                    ShopGtrPayMethod.ContiPay -> "Pay with ContiPay"
                    ShopGtrPayMethod.Paynow -> "Pay with Paynow"
                    ShopGtrPayMethod.EcoCash -> "Pay with EcoCash (C2B PIN)"
                },
            )
        }

        state.lastIntentId?.let {
            Text(
                "Last intent (${state.lastProvider}): $it",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        state.message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        OutlinedButton(onClick = onBack, shape = sharp) { Text("Back") }
    }
}
