package co.zw.nissangtr.customer.catalog

import co.zw.nissangtr.customer.rpc.CatalogListItem
import co.zw.nissangtr.ui.shop.ShopFilterState
import co.zw.nissangtr.ui.shop.ShopSortOption

/** Client-side filter + sort on browse rows (web PLP parity). */
internal fun applyCatalogFilterSort(
    items: List<CatalogListItem>,
    filter: ShopFilterState,
    sort: ShopSortOption,
): List<CatalogListItem> {
    var out = items.asSequence()
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
