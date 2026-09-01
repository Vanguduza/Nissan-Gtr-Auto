package co.zw.nissangtr.customer.pay

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import co.zw.nissangtr.customer.rpc.RpcClient
import co.zw.nissangtr.customer.visual.GtrPremiumColors
import co.zw.nissangtr.customer.visual.PremiumMessageBanner
import co.zw.nissangtr.customer.visual.PremiumMessageKind
import co.zw.nissangtr.customer.visual.PremiumPrimaryButton
import co.zw.nissangtr.customer.visual.PremiumScreenHeader
import co.zw.nissangtr.customer.visual.PremiumStatusChip
import co.zw.nissangtr.customer.visual.PremiumStatusTone
import co.zw.nissangtr.customer.visual.PremiumSurfaceCard
import co.zw.nissangtr.ui.shop.ShopGtrPayMethod
import co.zw.nissangtr.ui.shop.ShopPaymentMethodList

@Composable
fun PayIntentScreen(
    rpc: RpcClient,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    initialInvoiceId: String? = null,
    viewModel: PayIntentViewModel = viewModel(factory = PayIntentViewModel.factory(rpc)),
) {
    val state by viewModel.state.collectAsState()
    var payMethod by remember { mutableStateOf(ShopGtrPayMethod.ContiPay) }

    LaunchedEffect(initialInvoiceId) {
        initialInvoiceId?.trim()?.takeIf { it.isNotEmpty() }?.let(viewModel::onInvoiceIdChange)
    }

    Column(modifier.fillMaxSize().background(GtrPremiumColors.Background)) {
        PremiumScreenHeader(
            title = "Secure Payment",
            subtitle = "Choose how you want to pay",
            onBack = onBack,
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                PremiumSurfaceCard {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(Modifier.fillMaxWidth()) {
                            Text(
                                "Payment",
                                color = GtrPremiumColors.TextPrimary,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f),
                            )
                            PremiumStatusChip("Secure", PremiumStatusTone.Success)
                        }
                        ShopPaymentMethodList(
                            selected = payMethod,
                            onSelect = { payMethod = it },
                        )
                    }
                }
            }

            if (initialInvoiceId.isNullOrBlank()) {
                item {
                    Text(
                        "Choose an order",
                        color = GtrPremiumColors.TextPrimary,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                state.invoices.forEach { inv ->
                    item {
                        PremiumSurfaceCard(onClick = { viewModel.selectInvoice(inv.id) }) {
                            Row(Modifier.fillMaxWidth()) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        inv.documentNumber ?: "Order",
                                        color = GtrPremiumColors.TextPrimary,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    val open = inv.total - inv.amountPaid
                                    Text(
                                        "${inv.currency.rpcValue} %.2f outstanding".format(open),
                                        color = GtrPremiumColors.TextSecondary,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                                if (state.invoiceId == inv.id) {
                                    PremiumStatusChip("Selected", PremiumStatusTone.Premium)
                                }
                            }
                        }
                    }
                }
            }

            if (payMethod == ShopGtrPayMethod.EcoCash) {
                item {
                    OutlinedTextField(
                        value = state.ecocashMsisdn,
                        onValueChange = viewModel::onEcocashMsisdnChange,
                        label = { Text("EcoCash number") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !state.busy,
                    )
                }
            }

            item {
                PremiumPrimaryButton(
                    text = when (payMethod) {
                        ShopGtrPayMethod.ContiPay -> "Continue with ContiPay"
                        ShopGtrPayMethod.Paynow -> "Continue with Paynow"
                        ShopGtrPayMethod.EcoCash -> "Pay with EcoCash"
                    },
                    onClick = {
                        when (payMethod) {
                            ShopGtrPayMethod.ContiPay -> viewModel.createContipay()
                            ShopGtrPayMethod.Paynow -> viewModel.createPaynow()
                            ShopGtrPayMethod.EcoCash -> viewModel.createEcocash()
                        }
                    },
                    enabled = !state.busy && state.invoiceId.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            state.message?.let { item { PremiumMessageBanner(it, PremiumMessageKind.Success) } }
            state.error?.let { item { PremiumMessageBanner(it, PremiumMessageKind.Error) } }
        }
    }
}
