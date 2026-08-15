package co.zw.nissangtr.customer.pay

import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import co.zw.nissangtr.customer.rpc.CheckoutPayMethod
import co.zw.nissangtr.customer.rpc.CurrencyCode
import co.zw.nissangtr.customer.rpc.MoneyDualRead
import co.zw.nissangtr.customer.rpc.RpcClient
import co.zw.nissangtr.ui.shop.ShopDefaultScreen
import co.zw.nissangtr.ui.shop.ShopGtrPayMethod
import co.zw.nissangtr.ui.shop.ShopPaymentMethodList
import co.zw.nissangtr.ui.shop.ShopSectionHeader

/**
 * Thin ContiPay / Paynow / EcoCash intent-create scaffold with D-57 display:
 * browse open amount in USD; EcoCash shows ZiG from MoneyMinor + ops fxRateId.
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

    val checkoutMethod = when (payMethod) {
        ShopGtrPayMethod.ContiPay -> CheckoutPayMethod.CONTIPAY
        ShopGtrPayMethod.Paynow -> CheckoutPayMethod.PAYNOW
        ShopGtrPayMethod.EcoCash -> CheckoutPayMethod.ECOCASH
    }
    val display = viewModel.checkoutDisplay(checkoutMethod)
    val ecoCashBlocked = payMethod == ShopGtrPayMethod.EcoCash && display == null

    ShopDefaultScreen(
        title = "Secure payment",
        subtitle = null,
        onBack = onBack,
        modifier = modifier,
    ) {
        Text(
            "EcoCash settles in ZiG at the ops daily rate. ContiPay / Paynow browse as USD.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        ShopPaymentMethodList(
            selected = payMethod,
            onSelect = { payMethod = it },
        )

        ShopSectionHeader(title = "Amount due", actionLabel = null)
        when {
            display == null && payMethod == ShopGtrPayMethod.EcoCash -> {
                Text(
                    "Daily ZiG rate required for EcoCash. Try again later or pay via ContiPay/Paynow (USD).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            display != null && display.payCurrency == CurrencyCode.ZIG -> {
                val zig = MoneyDualRead.fromAmountMinor(display.payable.amountMinor, CurrencyCode.ZIG)
                val usd = viewModel.selectedOpenUsdMinor()?.let {
                    MoneyDualRead.fromAmountMinor(it, CurrencyCode.USD)
                }
                Text(
                    buildString {
                        append("ZIG %.2f".format(zig))
                        if (usd != null) append(" (USD %.2f browse)".format(usd))
                        state.zigRate?.let { append(" @ %.4f ZiG/USD".format(it)) }
                        display.fxRateId?.let { append(" · fx $it") }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            display != null -> {
                val usd = MoneyDualRead.fromAmountMinor(display.payable.amountMinor, CurrencyCode.USD)
                Text(
                    buildString {
                        append("USD %.2f".format(usd))
                        display.indicativeZigMinor?.let { zm ->
                            val zig = MoneyDualRead.fromAmountMinor(zm, CurrencyCode.ZIG)
                            append(" · ≈ ZIG %.2f indicative".format(zig))
                        }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            else -> {
                Text(
                    "Select an invoice to see the amount due.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

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

        if (payMethod == ShopGtrPayMethod.EcoCash) {
            ShopSectionHeader(title = "EcoCash payer", actionLabel = null)
            Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { viewModel.onEcocashModeChange(EcoCashPayerMode.Saved) },
                    enabled = !state.busy,
                    shape = sharp,
                    modifier = Modifier.weight(1f).padding(end = 4.dp),
                ) {
                    Text(
                        if (state.ecocashMode == EcoCashPayerMode.Saved) {
                            "● Saved / profile"
                        } else {
                            "Saved / profile"
                        },
                    )
                }
                OutlinedButton(
                    onClick = { viewModel.onEcocashModeChange(EcoCashPayerMode.Other) },
                    enabled = !state.busy,
                    shape = sharp,
                    modifier = Modifier.weight(1f).padding(start = 4.dp),
                ) {
                    Text(
                        if (state.ecocashMode == EcoCashPayerMode.Other) {
                            "● Other number"
                        } else {
                            "Other number"
                        },
                    )
                }
            }
            state.profilePhone?.takeIf { it.isNotBlank() }?.let { phone ->
                Text(
                    "Profile phone: $phone",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } ?: Text(
                "No profile phone yet — set one under Edit profile, or use Other number.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (state.ecocashMode == EcoCashPayerMode.Other) {
                OutlinedTextField(
                    value = state.ecocashMsisdn,
                    onValueChange = viewModel::onEcocashMsisdnChange,
                    label = { Text("EcoCash number (07… / +263…)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !state.busy,
                    shape = sharp,
                )
            }
        }

        ShopSectionHeader(title = "Own invoices", actionLabel = null)
        state.invoices.forEach { inv ->
            val open = (inv.total - inv.amountPaid).coerceAtLeast(0.0)
            Text(
                // D-57 browse: open balance as USD dual-read major
                "${inv.documentNumber ?: inv.id} · open USD %.2f".format(open),
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
            enabled = !state.busy && !ecoCashBlocked,
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
