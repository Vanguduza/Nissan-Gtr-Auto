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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import co.zw.nissangtr.ui.shop.ShopHonestEmpty
import co.zw.nissangtr.ui.shop.ShopProceedButtonBox
import co.zw.nissangtr.ui.shop.ShopSectionHeader
import co.zw.nissangtr.ui.theme.GtrColors

/**
 * Cart tab — Shopping-By-KMP [CartScreen] (LazyColumn basket + bottom ProceedButtonBox).
 * GTR checkout options (fulfillment / address / currency) open as a step after Proceed.
 */
@Composable
fun CartScreen(
    rpc: RpcClient,
    onBack: (() -> Unit)? = null,
    onContinueShopping: (() -> Unit)? = null,
    onPay: ((invoiceId: String) -> Unit)? = null,
    onManageAddresses: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    viewModel: CartViewModel = viewModel(factory = CartViewModel.factory(rpc)),
) {
    val state by viewModel.state.collectAsState()
    val cart = state.cart
    val lines = cart?.lines.orEmpty()
    val hasLines = lines.isNotEmpty()
    var checkoutStep by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        if (!hasLines) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp),
                ) {
                    ShopHonestEmpty(
                        title = "Basket is empty",
                        body = "Add parts from Home or Search. Core deposits appear as sibling lines when applicable.",
                    )
                    if (onContinueShopping != null) {
                        Spacer(Modifier.height(16.dp))
                        androidx.compose.material3.Button(
                            onClick = onContinueShopping,
                            shape = MaterialTheme.shapes.small,
                        ) { Text("Continue shopping") }
                    }
                }
            }
        } else if (!checkoutStep) {
            // KMP CartScreen — product list only
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 120.dp),
            ) {
                item {
                    Text(
                        "Cart",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(16.dp),
                    )
                }
                items(lines, key = { it.id }) { line ->
                    KmpCartLineBox(line = line)
                }
            }
            ShopProceedButtonBox(
                totalLabel = "${cart!!.currency.rpcValue} · ${lines.size} line(s)",
                ctaLabel = if (state.busy) "Working…" else "Proceed to checkout",
                onClick = { checkoutStep = true },
                enabled = !state.busy,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        } else {
            // GTR checkout options (not in KMP demo — ContiPay path)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .padding(bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Checkout", style = MaterialTheme.typography.titleLarge)
                ShopSectionHeader(title = "Fulfillment", actionLabel = null)
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
                        label = { Text("Nationwide") },
                    )
                }
                if (state.fulfillmentMode == FulfillmentMode.DISPATCH) {
                    ShopSectionHeader(
                        title = "Delivery address",
                        actionLabel = if (onManageAddresses != null) "Manage" else null,
                        onAction = onManageAddresses,
                    )
                    if (state.addresses.isEmpty()) {
                        ShopHonestEmpty(
                            title = "Add an address",
                            body = "Nationwide dispatch needs a saved shipping address (map pick available).",
                        )
                    } else {
                        state.addresses.forEach { addr ->
                            FilterChip(
                                selected = state.selectedAddressId == addr.id,
                                onClick = { viewModel.onAddressSelect(addr.id) },
                                enabled = !state.busy,
                                label = { Text(addr.summaryLabel()) },
                            )
                        }
                    }
                }
                ShopSectionHeader(title = "Settle in", actionLabel = null)
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
                state.error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                state.lastInvoiceId?.let { invoiceId ->
                    ShopSectionHeader(title = "Last checkout", actionLabel = null)
                    Text(invoiceId, style = MaterialTheme.typography.bodySmall)
                    if (onPay != null) {
                        androidx.compose.material3.Button(
                            onClick = { onPay(invoiceId) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.small,
                        ) { Text("Pay · ContiPay / Paynow / EcoCash") }
                    }
                }
            }
            ShopProceedButtonBox(
                totalLabel = cart?.currency?.rpcValue ?: "USD",
                ctaLabel = if (state.busy) "Working…" else "Place order",
                onClick = viewModel::checkout,
                enabled = !state.busy,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

/** KMP CartBox-shaped row — image placeholder + title + qty. */
@Composable
private fun KmpCartLineBox(line: CartLineSummary) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
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
                Spacer(Modifier.height(4.dp))
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
