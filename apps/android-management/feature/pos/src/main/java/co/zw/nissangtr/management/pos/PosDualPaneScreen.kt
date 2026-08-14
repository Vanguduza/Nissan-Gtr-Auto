package co.zw.nissangtr.management.pos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import co.zw.nissangtr.management.rpc.CurrencyCode
import co.zw.nissangtr.management.rpc.MoneyDualRead
import co.zw.nissangtr.management.rpc.RpcClient
import co.zw.nissangtr.ui.shop.ShopPrimaryButton
import co.zw.nissangtr.ui.shop.ShopSecondaryButton
import co.zw.nissangtr.ui.shop.ShopStaffPanel
import co.zw.nissangtr.ui.shop.ShopStaffScreen
import co.zw.nissangtr.ui.theme.GtrColors

/**
 * CoolMall-inspired counter POS skeleton: left catalog/search, right cart.
 * GTR colors only; cart/catalog data from [RpcClient] (Fake/Live). Not a full CoolMall port.
 */
@Composable
fun PosDualPaneScreen(
    rpc: RpcClient,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    vm: PosSkeletonViewModel = viewModel(factory = PosSkeletonViewModel.factory(rpc)),
) {
    val state by vm.state.collectAsState()

    ShopStaffScreen(
        title = "POS",
        subtitle = "Dual-pane skeleton · Phase 1",
        onBack = onBack,
        scrollable = false,
        modifier = modifier,
    ) {
        state.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        state.status?.let {
            Text(it, style = MaterialTheme.typography.labelMedium, color = GtrColors.SilverDim)
        }

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val twoPane = PosDualPaneLayout.useTwoPane(maxWidth.value)
            if (twoPane) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CatalogPane(
                        state = state,
                        vm = vm,
                        modifier = Modifier
                            .weight(0.6f)
                            .fillMaxHeight(),
                    )
                    CartPane(
                        state = state,
                        vm = vm,
                        modifier = Modifier
                            .weight(0.4f)
                            .fillMaxHeight(),
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CatalogPane(state = state, vm = vm, modifier = Modifier.fillMaxWidth())
                    CartPane(state = state, vm = vm, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun CatalogPane(
    state: PosSkeletonState,
    vm: PosSkeletonViewModel,
    modifier: Modifier = Modifier,
) {
    ShopStaffPanel(title = "Catalog", modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            state.warehouses.forEach { wh ->
                FilterChip(
                    selected = state.selectedWarehouseId == wh.id,
                    onClick = { vm.selectWarehouse(wh.id) },
                    label = { Text(wh.code) },
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = state.currency == CurrencyCode.USD,
                onClick = { if (state.currency != CurrencyCode.USD) vm.toggleCurrency() },
                label = { Text("USD") },
            )
            FilterChip(
                selected = state.currency == CurrencyCode.ZIG,
                onClick = { if (state.currency != CurrencyCode.ZIG) vm.toggleCurrency() },
                label = { Text("ZiG") },
            )
        }
        ShopPrimaryButton(
            label = if (state.cartId == null) "Open cart" else "Cart open",
            onClick = vm::openCart,
            enabled = !state.busy && state.cartId == null && state.selectedWarehouseId != null,
        )
        OutlinedTextField(
            value = state.query,
            onValueChange = vm::onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("OEM / part search") },
            enabled = !state.busy,
        )
        ShopSecondaryButton(
            label = if (state.busy) "Searching…" else "Search",
            onClick = vm::search,
            enabled = !state.busy && state.query.isNotBlank(),
        )
        HorizontalDivider(color = GtrColors.Mist)
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(state.hits, key = { it.oemPartNumber }) { hit ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(hit.oemPartNumber, style = MaterialTheme.typography.titleSmall)
                        Text(
                            listOfNotNull(hit.categoryName, hit.pncCode).joinToString(" · "),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    ShopSecondaryButton(
                        label = "Add",
                        onClick = { vm.addHit(hit) },
                        enabled = !state.busy && state.cartId != null,
                    )
                }
            }
        }
    }
}

@Composable
private fun CartPane(
    state: PosSkeletonState,
    vm: PosSkeletonViewModel,
    modifier: Modifier = Modifier,
) {
    ShopStaffPanel(title = "Cart", modifier = modifier) {
        if (state.lines.isEmpty()) {
            Text(
                "No lines — search left, Add to cart.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            state.lines.forEach { line ->
                val total = MoneyDualRead.displayLineTotalMajor(line.lineTotal, line.lineTotalMinor)
                Text(
                    "${line.oemPartNumber ?: line.stockItemId} × ${line.qty} = $total" +
                        if (line.isCoreCharge) " (core)" else "",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        ShopPrimaryButton(
            label = "Checkout",
            onClick = vm::checkout,
            enabled = !state.busy && state.cartId != null && state.lines.isNotEmpty(),
        )
    }
}
