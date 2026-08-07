package co.zw.nissangtr.customer.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import co.zw.nissangtr.customer.rpc.CatalogListItem
import co.zw.nissangtr.ui.shop.ShopCircleIconButton
import co.zw.nissangtr.ui.shop.ShopFilterDialog
import co.zw.nissangtr.ui.shop.ShopFilterSortBar
import co.zw.nissangtr.ui.shop.ShopFilterState
import co.zw.nissangtr.ui.shop.ShopHonestEmpty
import co.zw.nissangtr.ui.shop.ShopProductCard
import co.zw.nissangtr.ui.shop.ShopSortDialog
import co.zw.nissangtr.ui.shop.ShopSortOption

/**
 * Category PLP — live browse with filter/sort dialogs (ShopKit parity).
 * Shows honest empty only when filtered API returns no rows.
 */
@Composable
fun CategoryPlpScreen(
    title: String,
    products: List<CatalogListItem>,
    wishOems: Set<String>,
    busy: Boolean,
    filterState: ShopFilterState,
    sortOption: ShopSortOption,
    categoryLabels: List<String>,
    onBack: () -> Unit,
    onOpenProduct: (String) -> Unit,
    onToggleWish: (CatalogListItem) -> Unit,
    onApplyFilter: (ShopFilterState) -> Unit,
    onApplySort: (ShopSortOption) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showFilter by remember { mutableStateOf(false) }
    var showSort by remember { mutableStateOf(false) }

    if (showFilter) {
        ShopFilterDialog(
            state = filterState,
            categories = categoryLabels,
            onDismiss = { showFilter = false },
            onApply = {
                onApplyFilter(it)
                showFilter = false
            },
        )
    }
    if (showSort) {
        ShopSortDialog(
            selected = sortOption,
            onDismiss = { showSort = false },
            onSelect = {
                onApplySort(it)
                showSort = false
            },
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ShopCircleIconButton(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                onClick = onBack,
                contentDescription = "Back",
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Text(
                    "${products.size} part(s)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (busy) {
                CircularProgressIndicator(modifier = Modifier.padding(8.dp))
            }
        }
        ShopFilterSortBar(
            onFilter = { showFilter = true },
            onSort = { showSort = true },
            sortLabel = sortOption.label,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        if (products.isEmpty() && !busy) {
            ShopHonestEmpty(
                title = "No parts in this category",
                body = "No items.",
                modifier = Modifier.padding(24.dp),
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize().padding(8.dp),
                contentPadding = PaddingValues(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(products, key = { it.stockItemId }) { item ->
                    ShopProductCard(
                        title = item.oem,
                        subtitle = item.name,
                        priceLabel = item.usd?.let { "USD %.2f".format(it) } ?: "On request",
                        liked = wishOems.contains(item.oem.trim().uppercase()),
                        onLikeClick = { onToggleWish(item) },
                        onClick = { onOpenProduct(item.oem) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
