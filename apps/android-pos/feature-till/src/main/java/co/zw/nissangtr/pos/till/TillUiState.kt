package co.zw.nissangtr.pos.till

import co.zw.nissangtr.pos.api.TillFakeState
import co.zw.nissangtr.pos.api.TillItem
import co.zw.nissangtr.pos.api.VehicleLatch
import co.zw.nissangtr.pos.lookup.FinderMode

data class TillUiState(
    val layoutMode: TillLayoutMode,
    val fake: TillFakeState,
    val finderMode: FinderMode = FinderMode.SHOP_STOCK,
    val searchQuery: String = "",
    /** Null = all categories. Facets are shop_stock `p_category`, not part OEMs. */
    val selectedCategory: String? = null,
    val inStockOnly: Boolean = true,
    val selectedOem: String? = null,
    val epcSectionPnc: String? = null,
    val banner: String? = null,
    val boundCustomerId: String? = null,
    val chassisShortcuts: List<co.zw.nissangtr.pos.api.ChassisShortcut> = emptyList(),
) {
    val latch: VehicleLatch? get() = fake.latch
    val tiles: List<TillItem> get() = fake.tiles

    /** Distinct category names from current tiles (facet chips). */
    val categoryFacets: List<String>
        get() = tiles.mapNotNull { it.categoryName?.trim()?.takeIf { n -> n.isNotEmpty() } }
            .distinct()
            .sorted()

    val visibleTiles: List<TillItem>
        get() {
            // Server already applied inStock/category on shop_stock loads; keep a
            // client pass for Fake/offline snapshots that return a full catalog.
            var list = tiles.distinctBy { it.oemPartNumber }
            if (inStockOnly) list = list.filter { it.saleableQty > 0 }
            selectedCategory?.let { cat ->
                list = list.filter { it.categoryName.equals(cat, ignoreCase = true) }
            }
            return list
        }
}
