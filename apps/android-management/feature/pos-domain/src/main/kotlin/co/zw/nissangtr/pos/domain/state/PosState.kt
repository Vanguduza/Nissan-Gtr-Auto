package co.zw.nissangtr.pos.domain.state

import co.zw.nissangtr.pos.domain.error.PosError
import co.zw.nissangtr.pos.domain.model.CartProjection
import co.zw.nissangtr.pos.domain.model.CatalogPart
import co.zw.nissangtr.pos.domain.model.CurrencyCode
import co.zw.nissangtr.pos.domain.model.Operator
import co.zw.nissangtr.pos.domain.model.PopularPin
import co.zw.nissangtr.pos.domain.model.PopularRowItem
import co.zw.nissangtr.pos.domain.model.VehicleCascade
import co.zw.nissangtr.pos.domain.model.VehicleSelection
import co.zw.nissangtr.pos.domain.model.buildPopularRow

/** Rail destinations in canonical order (Blueprint §6.1). `Reports` is forbidden (D-001). */
enum class PosDestination {
    Home, SearchSpares, QuickSale, Customer, Orders, Returns, EpcBrowse, Settings,
}

/** Transient operator feedback. [error] maps to a string resource in the UI (ARCH-10). */
sealed interface PosFeedback {
    data class Failure(val error: PosError) : PosFeedback
    data class Notice(val notice: PosNotice) : PosFeedback
}

enum class PosNotice { Pinned, Unpinned, BestSellerHidden }

data class PosState(
    val destination: PosDestination = PosDestination.Home,
    val operator: Operator? = null,
    val online: Boolean = true,
    val currency: CurrencyCode = CurrencyCode.USD,
    val cascade: VehicleCascade = VehicleCascade(),
    val vehicle: VehicleSelection? = null,
    val searchQuery: String = "",
    val searchResults: List<CatalogPart>? = null,
    val searching: Boolean = false,
    val recentSearches: List<String> = emptyList(),
    val pins: List<PopularPin> = emptyList(),
    val bestSellers: List<CatalogPart> = emptyList(),
    val hiddenBestSellers: Set<String> = emptySet(),
    val cart: CartProjection = CartProjection.empty(CurrencyCode.USD),
    /** Count of cart mutations in flight; the UI disables duplicate adds while > 0. */
    val cartBusy: Int = 0,
    val feedback: PosFeedback? = null,
) {
    val popularRow: List<PopularRowItem>
        get() = buildPopularRow(pins, bestSellers, hiddenBestSellers)

    fun isPinned(pin: PopularPin): Boolean = pins.any { it.stableKey == pin.stableKey }

    companion object {
        const val RECENT_SEARCH_LIMIT = 8
    }
}
