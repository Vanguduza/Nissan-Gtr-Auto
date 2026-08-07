package co.zw.nissangtr.customer.cart

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import co.zw.nissangtr.customer.rpc.CartLineSummary
import co.zw.nissangtr.customer.rpc.CurrencyCode
import co.zw.nissangtr.customer.rpc.FulfillmentMode
import co.zw.nissangtr.customer.rpc.RpcClient
import co.zw.nissangtr.ui.shop.ShopDefaultScreen
import co.zw.nissangtr.ui.shop.ShopGtrPayMethod
import co.zw.nissangtr.ui.shop.ShopHonestEmpty
import co.zw.nissangtr.ui.shop.ShopPaymentMethodList
import co.zw.nissangtr.ui.shop.ShopProceedButtonBox
import co.zw.nissangtr.ui.shop.ShopSectionHeader
import co.zw.nissangtr.ui.theme.GtrColors

/**
 * Shopping-By-KMP cart + checkout pattern adapted for GTR:
 * back toolbar · basket lines · delivery options · payment method · * proceed places order then opens secure PayIntent · order management link.
 *
 * Always reloads [RpcClient.getOpenCart] on appear so the list matches the shell badge
 * (Activity-scoped ViewModel otherwise keeps a stale empty cart).
 */
@Composable
fun CartScreen(
    rpc: RpcClient,
    onBack: () -> Unit,
    onContinueShopping: (() -> Unit)? = null,
    onPay: (invoiceId: String) -> Unit,
    onManageAddresses: (() -> Unit)? = null,
    onManageOrders: (() -> Unit)? = null,
    refreshKey: Int = 0,
    modifier: Modifier = Modifier,
    viewModel: CartViewModel = viewModel(
        key = "customer-cart",
        factory = CartViewModel.factory(rpc),
    ),
) {
    val state by viewModel.state.collectAsState()
    val cart = state.cart
    val lines = cart?.lines.orEmpty()
    val hasLines = lines.isNotEmpty()
    var payMethod by remember { mutableStateOf(ShopGtrPayMethod.ContiPay) }

    // Same source of truth as the top-bar badge: getOpenCart().
    LaunchedEffect(refreshKey) {
        viewModel.refresh()
    }

    ShopDefaultScreen(
        title = "Cart",
        onBack = onBack,
        scrollable = false,
        loading = state.busy && !hasLines,
        modifier = modifier,
        bottomBar = {
            if (hasLines) {
                ShopProceedButtonBox(
                    totalLabel = buildString {
                        append(cart?.currency?.rpcValue ?: "USD")
                        append(" · ")
                        append(lines.size)
                        append(" line(s)")
                    },
                    ctaLabel = when {
                        state.busy -> "Working…"
                        state.lastInvoiceId != null -> "Continue to secure payment"
                        else -> "Place order & pay"
                    },
                    onClick = {
                        val invoice = state.lastInvoiceId
                        if (invoice != null) {
                            onPay(invoice)
                        } else {
                            viewModel.checkout { id -> onPay(id) }
                        }
                    },
                    enabled = !state.busy,
                )
            }
        },
    ) {
        if (!hasLines && state.lastInvoiceId == null) {
            Column(
                Modifier.fillMaxSize().padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                ShopHonestEmpty(
                    title = "Basket is empty",
                    body = "No items.",
                )
                if (onContinueShopping != null) {
                    Spacer(Modifier.height(12.dp))
                    androidx.compose.material3.Button(
                        onClick = onContinueShopping,
                        shape = MaterialTheme.shapes.small,
                    ) { Text("Continue shopping") }
                }
                if (onManageOrders != null) {
                    TextButton(onClick = onManageOrders) { Text("My orders") }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (hasLines) {
                    items(lines, key = { it.id }) { line ->
                        KmpCartLineBox(line = line)
                    }
                }

                item {
                    Spacer(modifier.height(8.dp))
                    ShopSectionHeader(title = "Delivery", actionLabel = null)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = state.fulfillmentMode == FulfillmentMode.IMMEDIATE,
                            onClick = { viewModel.onFulfillmentChange(FulfillmentMode.IMMEDIATE) },
                            enabled = !state.busy,
                            label = { Text("Click & collect") },
                        )
                        FilterChip(
                            selected = state.fulfillmentMode == FulfillmentMode.DISPATCH,
                            onClick = { viewModel.onFulfillmentChange(FulfillmentMode.DISPATCH) },
                            enabled = !state.busy,
                            label = { Text("Nationwide delivery") },
                        )
                    }
                }

                if (state.fulfillmentMode == FulfillmentMode.DISPATCH) {
                    item {
                        ShopSectionHeader(
                            title = "Shipping address",
                            actionLabel = if (onManageAddresses != null) "Change" else null,
                            onAction = onManageAddresses,
                        )
                        if (state.addresses.isEmpty()) {
                            ShopHonestEmpty(
                                title = "Add an address",
                                body = "No shipping address.",
                            )
                        } else {
                            state.addresses.forEach { addr ->
                                FilterChip(
                                    selected = state.selectedAddressId == addr.id,
                                    onClick = { viewModel.onAddressSelect(addr.id) },
                                    enabled = !state.busy,
                                    label = { Text(addr.summaryLabel()) },
                                    modifier = Modifier.padding(end = 4.dp, bottom = 4.dp),
                                )
                            }
                        }
                    }
                }

                item {
                    HorizontalDivider(color = GtrColors.Mist)
                    Spacer(Modifier.height(8.dp))
                    ShopSectionHeader(title = "Settle currency", actionLabel = null)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = state.currency == CurrencyCode.USD,
                            onClick = { viewModel.onCurrencyChange(CurrencyCode.USD) },
                            enabled = !state.busy,
                            label = { Text("USD") },
                        )
                        FilterChip(
                            selected = state.currency == CurrencyCode.ZIG,
                            onClick = { viewModel.onCurrencyChange(CurrencyCode.ZIG) },
                            enabled = !state.busy,
                            label = { Text("ZiG") },
                        )
                    }
                }

                item {
                    Spacer(Modifier.height(8.dp))
                    ShopSectionHeader(title = "Payment method", actionLabel = null)
                    ShopPaymentMethodList(
                        selected = payMethod,
                        onSelect = { payMethod = it },
                    )
                    Text(
                        "You will be redirected to a secure ContiPay / Paynow / EcoCash payment page after placing the order.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }

                if (onManageOrders != null) {
                    item {
                        Spacer(Modifier.height(8.dp))
                        ShopSectionHeader(title = "Orders", actionLabel = null)
                        TextButton(onClick = onManageOrders) {
                            Text("Order management · track & history")
                        }
                    }
                }

                state.error?.let {
                    item {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
                state.message?.let {
                    item {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                }

                item { Spacer(Modifier.height(100.dp)) }
            }
        }
    }
}

@Composable
private fun KmpCartLineBox(line: CartLineSummary) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp),
        shape = MaterialTheme.shapes.small,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(GtrColors.Mist),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    (line.oemPartNumber ?: "OEM").take(6),
                    style = MaterialTheme.typography.labelSmall,
                    color = GtrColors.Steel,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    line.oemPartNumber ?: line.stockItemId.take(8),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    buildString {
                        append("Qty ${line.qty}")
                        if (line.isCoreDeposit) append(" · Core deposit")
                        line.unitPriceUsd?.let { append(" · USD %.2f".format(it)) }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
