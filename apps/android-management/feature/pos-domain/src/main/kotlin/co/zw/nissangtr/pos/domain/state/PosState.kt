package co.zw.nissangtr.pos.domain.state

import co.zw.nissangtr.pos.domain.error.PosError
import co.zw.nissangtr.pos.domain.model.ApprovalRequest
import co.zw.nissangtr.pos.domain.model.CartProjection
import co.zw.nissangtr.pos.domain.model.Customer
import co.zw.nissangtr.pos.domain.model.EpcDiagram
import co.zw.nissangtr.pos.domain.model.EpcDiagramDetail
import co.zw.nissangtr.pos.domain.model.EpcImage
import co.zw.nissangtr.pos.domain.model.EpcSection
import co.zw.nissangtr.pos.domain.model.EpcVariant
import co.zw.nissangtr.pos.domain.model.GarageVehicle
import co.zw.nissangtr.pos.domain.model.InvoiceSummary
import co.zw.nissangtr.pos.domain.model.ParkedSale
import co.zw.nissangtr.pos.domain.model.Quotation
import co.zw.nissangtr.pos.domain.model.Receipt
import co.zw.nissangtr.pos.domain.model.VehicleModel
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

enum class PosNotice {
    Pinned, Unpinned, BestSellerHidden,
    SaleParked, SaleResumed, QuoteCreated, QuoteSent, QuoteConverted,
    CustomerSaved, VehicleSaved, EcoCashSent, Approved, Refunded,
}

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

    // Sale, customer and back-office destinations
    val customer: Customer? = null,
    val customerResults: List<Customer>? = null,
    val customerSearching: Boolean = false,
    val garage: List<GarageVehicle> = emptyList(),
    /** Several garage vehicles and none chosen yet: ask which one this sale is for. */
    val garagePrompt: Boolean = false,
    val paymentOpen: Boolean = false,
    val paying: Boolean = false,
    val ecoCashReference: String? = null,
    val receipt: Receipt? = null,
    val approval: ApprovalRequest? = null,
    val approving: Boolean = false,
    val parked: List<ParkedSale>? = null,
    val quotations: List<Quotation>? = null,
    val invoiceQuery: String = "",
    val invoices: List<InvoiceSummary>? = null,
    val epc: EpcBrowse = EpcBrowse(),
    val hapticsEnabled: Boolean = true,
) {
    val popularRow: List<PopularRowItem>
        get() = buildPopularRow(pins, bestSellers, hiddenBestSellers)

    fun isPinned(pin: PopularPin): Boolean = pins.any { it.stableKey == pin.stableKey }

    companion object {
        const val RECENT_SEARCH_LIMIT = 8
    }
}

/** EPC Browse drill-down: model → variant → section → diagram → parts. */
data class EpcBrowse(
    val model: VehicleModel? = null,
    val variants: List<EpcVariant>? = null,
    val variant: EpcVariant? = null,
    val sections: List<EpcSection>? = null,
    val section: EpcSection? = null,
    val diagrams: List<EpcDiagram>? = null,
    val detail: EpcDiagramDetail? = null,
    val image: EpcImage? = null,
    /** OEM selected on the diagram or in the parts list; both highlight it. */
    val activeOem: String? = null,
    val loading: Boolean = false,
)
