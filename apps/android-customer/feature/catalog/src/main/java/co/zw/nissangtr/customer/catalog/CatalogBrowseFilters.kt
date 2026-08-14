package co.zw.nissangtr.customer.catalog

import co.zw.nissangtr.customer.rpc.CatalogListItem
import co.zw.nissangtr.ui.shop.ShopFilterState
import co.zw.nissangtr.ui.shop.ShopSortOption

/** Client-side filter + sort on browse rows (web PLP / `applyCatalogFiltersAndSort` parity). */
internal fun applyCatalogFilterSort(
    items: List<CatalogListItem>,
    filter: ShopFilterState,
    sort: ShopSortOption,
    /** When true (default for /shop), keep only qty > 0 and priced > 0. */
    shopStockOnly: Boolean = true,
): List<CatalogListItem> {
    var out = items.asSequence()
    if (shopStockOnly) {
        out = out.filter { item ->
            val qty = item.qty
            val inStock = when {
                qty != null -> qty > 0
                else -> item.stock != co.zw.nissangtr.customer.rpc.StockState.BACKORDER
            }
            inStock && item.usd != null && item.usd > 0
        }
    }
    if (filter.minPrice > 0f || filter.maxPrice < 500f) {
        out = out.filter { item ->
            val usd = item.usd ?: return@filter false
            usd >= filter.minPrice && usd <= filter.maxPrice
        }
    }
    filter.category?.trim()?.takeIf { it.isNotEmpty() }?.let { cat ->
        out = out.filter { item ->
            item.category?.equals(cat, ignoreCase = true) == true
        }
    }
    val list = out.toList()
    return when (sort) {
        ShopSortOption.Relevance -> list
        ShopSortOption.PriceAsc -> list.sortedBy { it.usd ?: Double.MAX_VALUE }
        ShopSortOption.PriceDesc -> list.sortedByDescending { it.usd ?: Double.MIN_VALUE }
        ShopSortOption.NameAsc -> list.sortedBy { it.name.lowercase() }
    }
}
