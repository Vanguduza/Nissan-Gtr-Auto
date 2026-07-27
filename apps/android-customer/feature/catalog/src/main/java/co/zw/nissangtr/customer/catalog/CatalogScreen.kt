package co.zw.nissangtr.customer.catalog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import co.zw.nissangtr.customer.rpc.CatalogListItem
import co.zw.nissangtr.customer.rpc.CatalogPartHit
import co.zw.nissangtr.customer.rpc.RpcClient
import co.zw.nissangtr.customer.rpc.RpcNames
import co.zw.nissangtr.customer.rpc.SearchMode

/**
 * Storefront catalog — home browse, four-way search, PLP hits, PDP + add-to-cart.
 * Live: [RpcNames.SEARCH_CATALOG] + PostgREST pricing (USD). Bridge-First not used here.
 */
@Composable
fun CatalogScreen(
    rpc: RpcClient,
    onBack: () -> Unit,
    onOpenCart: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: CatalogViewModel = viewModel(factory = CatalogViewModel.factory(rpc)),
) {
    val state by viewModel.state.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Catalog", style = MaterialTheme.typography.headlineSmall)
            OutlinedButton(onClick = onBack, enabled = !state.busy) {
                Text("Home")
            }
        }

        Text(
            "RPC: ${RpcNames.SEARCH_CATALOG} · USD via default price list",
            style = MaterialTheme.typography.bodySmall,
        )

        state.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        state.message?.let {
            Text(it, style = MaterialTheme.typography.bodySmall)
        }

        when (state.route) {
            CatalogScreenRoute.Home -> CatalogHome(
                state = state,
                onQueryChange = viewModel::onQueryChange,
                onModeChange = viewModel::onSearchModeChange,
                onSearch = viewModel::runSearch,
                onRefreshBrowse = viewModel::refreshBrowse,
                onOpenProduct = viewModel::openProduct,
            )
            CatalogScreenRoute.SearchResults -> CatalogSearchResults(
                hits = state.searchHits,
                query = state.query,
                mode = state.searchMode,
                busy = state.busy,
                onBack = viewModel::navigateHome,
                onOpenProduct = viewModel::openProduct,
            )
            CatalogScreenRoute.Product -> state.product?.let { product ->
                CatalogProductDetail(
                    product = product,
                    qty = state.addQty,
                    busy = state.busy,
                    onQtyChange = viewModel::onAddQtyChange,
                    onBack = viewModel::navigateBackFromProduct,
                    onAddToCart = { viewModel.addToCart(onOpenCart) },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CatalogHome(
    state: CatalogUiState,
    onQueryChange: (String) -> Unit,
    onModeChange: (SearchMode) -> Unit,
    onSearch: () -> Unit,
    onRefreshBrowse: () -> Unit,
    onOpenProduct: (String) -> Unit,
) {
    OutlinedTextField(
        value = state.query,
        onValueChange = onQueryChange,
        label = { Text("Search query") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        enabled = !state.busy,
    )
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        SearchMode.entries.forEach { mode ->
            FilterChip(
                selected = state.searchMode == mode,
                onClick = { onModeChange(mode) },
                label = { Text(mode.rpcValue.uppercase()) },
                enabled = !state.busy,
            )
        }
    }
    Button(onClick = onSearch, modifier = Modifier.fillMaxWidth(), enabled = !state.busy) {
        Text("Search catalog")
    }
    OutlinedButton(onClick = onRefreshBrowse, modifier = Modifier.fillMaxWidth(), enabled = !state.busy) {
        Text("Refresh browse")
    }
    HorizontalDivider()
    Text("Browse", style = MaterialTheme.typography.titleMedium)
    LazyColumn(
        modifier = Modifier.weight(1f, fill = true),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(state.browseItems, key = { it.stockItemId }) { item ->
            CatalogListRow(item = item, onClick = { onOpenProduct(item.oem) })
        }
    }
}

@Composable
private fun CatalogSearchResults(
    hits: List<CatalogPartHit>,
    query: String,
    mode: SearchMode,
    busy: Boolean,
    onBack: () -> Unit,
    onOpenProduct: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Results for \"$query\" (${mode.rpcValue})", style = MaterialTheme.typography.titleMedium)
        OutlinedButton(onClick = onBack, enabled = !busy) { Text("Back to browse") }
        if (hits.isEmpty()) {
            Text("No parts matched.", style = MaterialTheme.typography.bodyMedium)
        } else {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                hits.forEach { hit ->
                    AssistChip(
                        onClick = { onOpenProduct(hit.oemPartNumber) },
                        label = { Text(hit.oemPartNumber) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    hit.categoryName?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun CatalogProductDetail(
    product: co.zw.nissangtr.customer.rpc.CatalogProduct,
    qty: String,
    busy: Boolean,
    onQtyChange: (String) -> Unit,
    onBack: () -> Unit,
    onAddToCart: () -> Unit,
) {
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        OutlinedButton(onClick = onBack, enabled = !busy) { Text("Back") }
        Text(product.oem, style = MaterialTheme.typography.headlineSmall)
        Text(product.name, style = MaterialTheme.typography.bodyLarge)
        product.brand?.let { Text("Brand: $it", style = MaterialTheme.typography.bodyMedium) }
        product.category?.let { Text("Category: $it", style = MaterialTheme.typography.bodyMedium) }
        Text(
            product.usd?.let { "USD %.2f".format(it) } ?: "Price on request",
            style = MaterialTheme.typography.titleMedium,
        )
        if (product.coreCharge > 0) {
            Text("Core charge USD %.2f".format(product.coreCharge), style = MaterialTheme.typography.bodySmall)
        }
        Text(product.stock.label(), style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(
            value = qty,
            onValueChange = onQtyChange,
            label = { Text("Qty") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !busy,
        )
        Button(onClick = onAddToCart, modifier = Modifier.fillMaxWidth(), enabled = !busy) {
            Text("Add to cart")
        }
    }
}

@Composable
private fun CatalogListRow(
    item: CatalogListItem,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(item.oem, style = MaterialTheme.typography.titleSmall)
        Text(item.name, style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(item.stock.label(), style = MaterialTheme.typography.bodySmall)
            item.usd?.let { Text("USD %.2f".format(it), style = MaterialTheme.typography.bodySmall) }
        }
    }
}
