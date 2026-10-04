package co.zw.nissangtr.pos.domain.state

import co.zw.nissangtr.pos.domain.error.PosError
import co.zw.nissangtr.pos.domain.model.ApprovalRequest
import co.zw.nissangtr.pos.domain.model.CartProjection
import co.zw.nissangtr.pos.domain.model.CompanionSession
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
import co.zw.nissangtr.pos.domain.model.ScannerLink
import co.zw.nissangtr.pos.domain.model.VehicleModel
import co.zw.nissangtr.pos.domain.model.CatalogPart
import co.zw.nissangtr.pos.domain.model.CurrencyCode
import co.zw.nissangtr.pos.domain.model.OfflineSyncStatus
import co.zw.nissangtr.pos.domain.model.Operator
import co.zw.nissangtr.pos.domain.model.PopularPin
import co.zw.nissangtr.pos.domain.model.PopularRowItem
import co.zw.nissangtr.pos.domain.model.VehicleCascade
import co.zw.nissangtr.pos.domain.model.VehicleSelection
import co.zw.nissangtr.pos.domain.model.buildPopularRow

/** Rail destinations in canonical order (Blueprint §6.1). `Reports` is forbidden (D-001). */
enum class PosDestination {
    Home, SearchSpares, QuickSale, Customer, Orders, Returns, EpcBrowse, Till, Settings,
    /** Payment recovery (§10.7): opened from an Unknown outcome or Orders, never on the rail. */
    Recovery,
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
    OfflineSaleQueued, OfflineSynced,
    TillOpened, CashRecorded, TillClosed, TillVariancePending, TillHandedOver,
    PolicySaved,
    ReservationExpired, ReservationReleased, Collected,
    SplitCancelled, SplitCancelledRefund, SplitRefundOwed, SplitRefundRecorded,
    TerminalPaired, TerminalReversed, TerminalFinished,
    ReturnPosted, ReturnCashOut, ReturnSwap, ReturnWarranty, CoreReturned, ClaimOpened, ClaimDecided, ClaimClosed, CardRefunded,
    HeldHere, HeldElsewhere, TransferRequested, Backordered, TransferSent, FulfillmentReady, TransferReceived, HandedOver, FulfillmentReleased, BackorderOnSale,
    LetterIssued, SignatureSaved, ProfileSaved,
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
    /** Reasons for the open approval; null while loading. Empty for drawer actions (chosen before). */
    val approvalReasons: List<co.zw.nissangtr.pos.domain.model.ReasonCode>? = null,
    /** Policy decision for the open approval; true until known (fail closed). */
    val approvalNeedsManager: Boolean = true,
    /** The signed-in operator is a POS manager: approvals are theirs, no badge or password asked. */
    val selfApprover: Boolean = false,
    /** The front camera is reading a manager badge. */
    val badgeScanning: Boolean = false,
    /** Approval policies (Settings); null until loaded. */
    val policies: List<co.zw.nissangtr.pos.domain.model.ApprovalPolicy>? = null,
    val parked: List<ParkedSale>? = null,
    val quotations: List<Quotation>? = null,
    val invoiceQuery: String = "",
    val invoices: List<InvoiceSummary>? = null,
    val epc: EpcBrowse = EpcBrowse(),
    val hapticsEnabled: Boolean = true,
    /** Offline outbox: sales waiting to replay and refused replays awaiting review (§10.12). */
    val offlineQueue: OfflineSyncStatus = OfflineSyncStatus(0, 0),
    val offlineSyncing: Boolean = false,
    /** Companion phone pairing for the open sale, its dialog, and a create in flight. */
    val companion: CompanionSession? = null,
    val companionOpen: Boolean = false,
    val companionPairing: Boolean = false,
    /** This device scanning into another till's sale. */
    val scanner: ScannerLink? = null,
    val scannerOpen: Boolean = false,
    val scannerClaiming: Boolean = false,
    /** Cash drawer session (D-016); online selling needs it open. */
    val till: TillPanel = TillPanel(),
    /** Reserve-first checkout (§10.6) is wired for this build; otherwise payment posts directly. */
    val reserveCheckout: Boolean = false,
    /** The reserved order while payment is open; the sale is locked while it exists (§10.5). */
    val checkout: CheckoutSession? = null,
    val reserving: Boolean = false,
    /** Digital providers and why each is unavailable (null reason = available); null until probed. */
    val providers: Map<co.zw.nissangtr.pos.domain.model.DigitalProvider, String?>? = null,
    /** Order behind the receipt on screen, for "Customer collects later" / "Handed over". */
    val receiptOrderId: String? = null,
    val recoveryItems: List<co.zw.nissangtr.pos.domain.model.RecoveryItem>? = null,
    val recoveryOrderId: String? = null,
    val recoveryStatus: co.zw.nissangtr.pos.domain.model.PaymentStatus? = null,
    val pickups: List<co.zw.nissangtr.pos.domain.model.PickupOrder>? = null,
    /** Part payments on the reserved order (staged split); null when paying in one go. */
    val split: co.zw.nissangtr.pos.domain.model.SplitSession? = null,
    val splitBusy: Boolean = false,
    /** Key of a part whose answer was lost: the retry reuses it so the part cannot be taken twice. */
    val splitPartKey: String? = null,
    val splitRecovery: List<co.zw.nissangtr.pos.domain.model.SplitRecoveryItem>? = null,
    /** Part payments of the order open on the recovery screen, if it has any. */
    val recoverySplit: co.zw.nissangtr.pos.domain.model.SplitSession? = null,
    /** Card machine for this tablet (Settings) and whether it can take payments; null until loaded. */
    val terminalSetup: co.zw.nissangtr.pos.domain.model.TerminalSetup? = null,
    /** The card-machine attempt of the sale being paid. */
    val terminalAttempt: co.zw.nissangtr.pos.domain.model.TerminalAttempt? = null,
    val terminalBusy: Boolean = false,
    val terminalPairing: Boolean = false,
    /** Key of a start whose answer was lost: starting again returns the same attempt (no second charge). */
    val terminalKey: String? = null,
    val terminalRecovery: List<co.zw.nissangtr.pos.domain.model.TerminalRecoveryItem>? = null,
    /** The card-machine attempt opened on the recovery screen. */
    val recoveryTerminal: co.zw.nissangtr.pos.domain.model.TerminalAttempt? = null,
    // Returns, cores, warranty and stock by branch (phase 6)
    /** The sale opened on Returns and its lines with what can still come back. */
    val returnSale: InvoiceSummary? = null,
    val returnInvoice: co.zw.nissangtr.pos.domain.model.InvoiceDetail? = null,
    val returnsBusy: Boolean = false,
    /** A drafted return not posted yet (what was drafted → case id): posting again reuses it. */
    val returnDraft: Pair<String, String>? = null,
    val serialLookup: SerialLookup? = null,
    /** `return_post` / `core_return` → configured reasons. */
    val returnReasons: Map<String, List<co.zw.nissangtr.pos.domain.model.ReasonCode>> = emptyMap(),
    val warrantyClaims: List<co.zw.nissangtr.pos.domain.model.WarrantyClaim>? = null,
    val warrantyStatus: String? = "open",
    val warrantyQuery: String = "",
    /** The part whose stock by branch is on screen. */
    val stockPart: co.zw.nissangtr.pos.domain.model.CatalogPart? = null,
    val branchStock: List<co.zw.nissangtr.pos.domain.model.BranchStock>? = null,
    /** A card refund running on the machine (approved to start, not answered yet). */
    val cardRefund: co.zw.nissangtr.pos.domain.model.TerminalAttempt? = null,
    // Fulfilment (phase 7)
    val fulfillment: List<co.zw.nissangtr.pos.domain.model.FulfillmentRequest>? = null,
    val fulfillmentStatus: String? = null,
    val fulfillmentQuery: String = "",
    val fulfillmentBusy: Boolean = false,
    /** An arrived back-order whose part is being put on the current sale; tied to the sale once it is there. */
    val sellingBackorder: co.zw.nissangtr.pos.domain.model.FulfillmentRequest? = null,
    // Payment letters (phase 8)
    /** [co.zw.nissangtr.pos.domain.model.LetterSource.key] → letters issued for that payment. */
    val letters: Map<String, List<co.zw.nissangtr.pos.domain.model.PaymentLetterSummary>> = emptyMap(),
    val openLetter: co.zw.nissangtr.pos.domain.model.PaymentLetter? = null,
    val mySignature: co.zw.nissangtr.pos.domain.model.MySignature? = null,
    val businessProfile: co.zw.nissangtr.pos.domain.model.BusinessProfile? = null,
    val lettersBusy: Boolean = false,
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
