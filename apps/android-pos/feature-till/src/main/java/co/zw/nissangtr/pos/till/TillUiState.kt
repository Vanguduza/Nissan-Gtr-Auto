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
    val selectedCategory: String? = "Brakes",
    val inStockOnly: Boolean = true,
    val selectedOem: String? = "40206-JF00A",
    val epcSectionPnc: String? = null,
    val banner: String? = null,
    val boundCustomerId: String? = null,
    val chassisShortcuts: List<co.zw.nissangtr.pos.api.ChassisShortcut> = emptyList(),
) {
    val latch: VehicleLatch? get() = fake.latch
    val tiles: List<TillItem> get() = fake.tiles
    val visibleTiles: List<TillItem>
        get() {
            var list = tiles
            if (inStockOnly) list = list.filter { it.saleableQty > 0 }
            selectedCategory?.let { cat ->
                list = list.filter { it.categoryName.equals(cat, ignoreCase = true) }
            }
            return list
        }
}
