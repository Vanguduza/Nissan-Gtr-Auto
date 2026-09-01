package co.zw.nissangtr.customer.cart

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import co.zw.nissangtr.customer.rpc.CartLineSummary
import co.zw.nissangtr.customer.rpc.CatalogProduct
import co.zw.nissangtr.customer.rpc.CurrencyCode
import co.zw.nissangtr.customer.rpc.FulfillmentMode
import co.zw.nissangtr.customer.rpc.RpcClient
import co.zw.nissangtr.customer.visual.GtrPremiumColors
import co.zw.nissangtr.customer.visual.PremiumEmptyState
import co.zw.nissangtr.customer.visual.PremiumMessageBanner
import co.zw.nissangtr.customer.visual.PremiumMessageKind
import co.zw.nissangtr.customer.visual.PremiumPrimaryButton
import co.zw.nissangtr.customer.visual.PremiumScreenHeader
import co.zw.nissangtr.customer.visual.PremiumSectionHeader
import co.zw.nissangtr.customer.visual.R
import co.zw.nissangtr.ui.shop.ShopGtrPayMethod
import co.zw.nissangtr.ui.shop.ShopPaymentMethodList
import co.zw.nissangtr.ui.shop.ShopRemoteImage

/**
 * Preview-aligned cart. OEM stays internal; customer sees the friendly product description loaded
 * through the existing catalog read. Cart line count is small, so this presentation hydration is
 * bounded and does not require backend/schema changes.
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
    val productByLine = remember { mutableStateMapOf<String, CatalogProduct?>() }

    LaunchedEffect(refreshKey) { viewModel.refresh() }

    LaunchedEffect(lines.map { it.id to it.oemPartNumber }) {
        lines.forEach { line ->
            val oem = line.oemPartNumber?.trim()?.takeIf { it.isNotEmpty() } ?: return@forEach
            if (!productByLine.containsKey(line.id)) {
                productByLine[line.id] = runCatching { rpc.loadCatalogProduct(oem) }.getOrNull()
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(GtrPremiumColors.Background),
    ) {
        PremiumScreenHeader(
            title = "Your Cart",
            subtitle = if (hasLines) "${lines.size} item(s)" else null,
            onBack = onBack,
            trailing = {
                if (onManageOrders != null) {
                    TextButton(onClick = onManageOrders) {
                        Text("Orders", color = GtrPremiumColors.RedBright)
                    }
                }
            },
        )

        if (!hasLines && state.lastInvoiceId == null) {
            Box(
                Modifier.fillMaxSize().padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    PremiumEmptyState(
                        title = "Your cart is empty",
                        body = "Add in-stock parts to continue.",
                        art = R.drawable.gtr_empty_state_cart_empty,
                    )
                    if (onContinueShopping != null) {
                        Spacer(Modifier.height(12.dp))
                        PremiumPrimaryButton(
                            "Shop now",
                            onContinueShopping,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
            return
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(lines, key = { it.id }) { line ->
                PremiumCartLine(
                    line = line,
                    product = productByLine[line.id],
                )
            }

            item {
                Spacer(Modifier.height(8.dp))
                PremiumSectionHeader(title = "Delivery method")
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FulfillmentCard(
                        title = "CLICK & COLLECT",
                        body = "Same-day counter pickup",
                        art = R.drawable.gtr_support_help_vin_search,
                        selected = state.fulfillmentMode == FulfillmentMode.IMMEDIATE,
                        onClick = { viewModel.onFulfillmentChange(FulfillmentMode.IMMEDIATE) },
                        modifier = Modifier.weight(1f),
                    )
                    FulfillmentCard(
                        title = "NATIONWIDE DELIVERY",
                        body = "Dispatch to your address",
                        art = R.drawable.gtr_support_delivery_van,
                        selected = state.fulfillmentMode == FulfillmentMode.DISPATCH,
                        onClick = { viewModel.onFulfillmentChange(FulfillmentMode.DISPATCH) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            if (state.fulfillmentMode == FulfillmentMode.DISPATCH) {
                item {
                    Spacer(Modifier.height(10.dp))
                    PremiumSectionHeader(
                        title = "Shipping address",
                        action = if (onManageAddresses != null) "Change" else null,
                        onAction = onManageAddresses,
                    )
                    if (state.addresses.isEmpty()) {
                        Text(
                            "No shipping address saved.",
                            color = GtrPremiumColors.TextSecondary,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
                }
            }

            item {
                Spacer(Modifier.height(10.dp))
                PremiumSectionHeader(title = "Settle currency")
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
                Spacer(Modifier.height(10.dp))
                PremiumSectionHeader(title = "Payment method")
                ShopPaymentMethodList(
                    selected = payMethod,
                    onSelect = { payMethod = it },
                )
                Text(
                    "Secure payment opens after your order is placed.",
                    color = GtrPremiumColors.TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            state.error?.let { item { PremiumMessageBanner(it, PremiumMessageKind.Error) } }
            state.message?.let { item { PremiumMessageBanner(it, PremiumMessageKind.Success) } }

            item { Spacer(Modifier.height(24.dp)) }
        }

        if (hasLines) {
            Card(
                colors = CardDefaults.cardColors(containerColor = GtrPremiumColors.Surface),
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            ) {
                Column(Modifier.fillMaxWidth().padding(14.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("Total", color = GtrPremiumColors.TextSecondary)
                        Text(
                            "${cart?.currency?.rpcValue ?: "USD"} · ${lines.size} item(s)",
                            color = GtrPremiumColors.TextPrimary,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    PremiumPrimaryButton(
                        text = when {
                            state.busy -> "Working…"
                            state.lastInvoiceId != null -> "Continue to secure payment"
                            else -> "Checkout"
                        },
                        onClick = {
                            val invoice = state.lastInvoiceId
                            if (invoice != null) onPay(invoice)
                            else viewModel.checkout { id -> onPay(id) }
                        },
                        enabled = !state.busy,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun PremiumCartLine(
    line: CartLineSummary,
    product: CatalogProduct?,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = GtrPremiumColors.SurfaceRaised),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, GtrPremiumColors.Border),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier
                    .size(72.dp)
                    .background(GtrPremiumColors.SurfaceSoft, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                ShopRemoteImage(
                    url = product?.imageUrls?.firstOrNull(),
                    contentDescription = product?.name,
                    modifier = Modifier.fillMaxSize().padding(5.dp),
                    contentScale = ContentScale.Fit,
                    placeholderLabel = null,
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    product?.name ?: if (line.isCoreDeposit) "Refundable core deposit" else "Part in your cart",
                    color = GtrPremiumColors.TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "Qty ${line.qty}",
                    color = GtrPremiumColors.TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
                line.unitPriceUsd?.let {
                    Text(
                        "USD %.2f".format(it),
                        color = GtrPremiumColors.TextPrimary,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun FulfillmentCard(
    title: String,
    body: String,
    art: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = GtrPremiumColors.SurfaceRaised),
        border = BorderStroke(
            if (selected) 2.dp else 1.dp,
            if (selected) GtrPremiumColors.Red else GtrPremiumColors.Border,
        ),
        shape = RoundedCornerShape(12.dp),
    ) {
        Box(Modifier.fillMaxWidth().height(132.dp)) {
            androidx.compose.foundation.Image(
                painter = painterResource(art),
                contentDescription = null,
                modifier = Modifier.align(Alignment.BottomEnd).size(86.dp),
                contentScale = ContentScale.Fit,
            )
            Column(Modifier.padding(10.dp).width(104.dp)) {
                Text(
                    title,
                    color = GtrPremiumColors.TextPrimary,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    body,
                    color = GtrPremiumColors.TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (selected) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(22.dp)
                        .background(GtrPremiumColors.Red, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Check, null, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}
