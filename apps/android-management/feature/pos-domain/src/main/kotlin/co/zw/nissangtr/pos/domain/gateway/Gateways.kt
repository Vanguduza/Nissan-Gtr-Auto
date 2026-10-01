package co.zw.nissangtr.pos.domain.gateway

import co.zw.nissangtr.pos.domain.model.ApprovalRequest
import co.zw.nissangtr.pos.domain.model.CartProjection
import co.zw.nissangtr.pos.domain.model.Customer
import co.zw.nissangtr.pos.domain.model.CustomerDraft
import co.zw.nissangtr.pos.domain.model.EpcDiagram
import co.zw.nissangtr.pos.domain.model.EpcDiagramDetail
import co.zw.nissangtr.pos.domain.model.EpcSection
import co.zw.nissangtr.pos.domain.model.EpcVariant
import co.zw.nissangtr.pos.domain.model.GarageVehicle
import co.zw.nissangtr.pos.domain.model.InvoiceSummary
import co.zw.nissangtr.pos.domain.model.ManagerCredentials
import co.zw.nissangtr.pos.domain.model.Money
import co.zw.nissangtr.pos.domain.model.ParkedSale
import co.zw.nissangtr.pos.domain.model.QuoteChannel
import co.zw.nissangtr.pos.domain.model.Quotation
import co.zw.nissangtr.pos.domain.model.ReceiptContacts
import co.zw.nissangtr.pos.domain.model.TenderLine
import co.zw.nissangtr.pos.domain.model.CatalogPart
import co.zw.nissangtr.pos.domain.model.CurrencyCode
import co.zw.nissangtr.pos.domain.model.Operator
import co.zw.nissangtr.pos.domain.model.PopularPin
import co.zw.nissangtr.pos.domain.model.VehicleGeneration
import co.zw.nissangtr.pos.domain.model.VehicleModel
import co.zw.nissangtr.pos.domain.model.VehicleSelection
import co.zw.nissangtr.pos.domain.result.PosResult

/**
 * Narrow, feature-owned gateways (Blueprint §10.2, ARCH-04). Declared here, implemented in
 * `pos-data`, faked in tests. Business outcomes come back as [PosResult], never as exceptions.
 */
interface CatalogGateway {
    /** Fitment-aware search (`search_pos_vehicle_spares`); [vehicle] narrows results when set. */
    suspend fun search(query: String, vehicle: VehicleSelection?): PosResult<List<CatalogPart>>

    /** Server-ranked best sellers (`list_pos_popular_spares`). */
    suspend fun bestSellers(): PosResult<List<CatalogPart>>
}

interface FitmentGateway {
    suspend fun models(): PosResult<List<VehicleModel>>
    suspend fun generations(model: VehicleModel): PosResult<List<VehicleGeneration>>
    suspend fun engines(model: VehicleModel, generation: VehicleGeneration): PosResult<List<String>>
}

interface CartGateway {
    suspend fun open(currency: CurrencyCode): PosResult<CartProjection>
    suspend fun addLine(cartId: String, part: CatalogPart, qty: Double): PosResult<CartProjection>
    suspend fun setQuantity(cartId: String, lineId: String, qty: Double): PosResult<CartProjection>
    suspend fun removeLine(cartId: String, lineId: String): PosResult<CartProjection>
    suspend fun setVehicle(cartId: String, vehicle: VehicleSelection?): PosResult<Unit>
}

/** Operator pins and the D1 best-seller hide list (owner-only RLS on the server). */
interface PinGateway {
    suspend fun pins(): PosResult<List<PopularPin>>
    suspend fun pin(pin: PopularPin): PosResult<Unit>
    suspend fun unpin(pin: PopularPin): PosResult<Unit>
    suspend fun hiddenBestSellers(): PosResult<Set<String>>
    suspend fun hideBestSeller(stockItemId: String): PosResult<Unit>
    suspend fun unhideBestSeller(stockItemId: String): PosResult<Unit>
}

interface SessionGateway {
    suspend fun operator(): PosResult<Operator>
}

/** Outcome of a posted sale; the receipt is assembled from it and the cart snapshot. */
data class CheckoutResult(val invoiceId: String, val documentNumber: String?)

interface CheckoutGateway {
    /** `checkout_pos_cart_with_tenders`: tenders must equal the balance exactly. */
    suspend fun checkout(cartId: String, tenders: List<TenderLine>, contacts: ReceiptContacts): PosResult<CheckoutResult>

    /** EcoCash push to the customer's phone; returns the payment reference. */
    suspend fun requestEcoCash(msisdn: String, amount: Money, reference: String): PosResult<String>
}

interface CustomerGateway {
    suspend fun search(query: String): PosResult<List<Customer>>
    suspend fun create(draft: CustomerDraft): PosResult<Customer>
    suspend fun update(customerId: String, draft: CustomerDraft): PosResult<Customer>
    suspend fun garage(customerId: String): PosResult<List<GarageVehicle>>
    suspend fun attach(cartId: String, customerId: String?): PosResult<Unit>
    suspend fun saveToGarage(customerId: String, vehicle: VehicleSelection, isPrimary: Boolean): PosResult<Unit>
}

interface SalesGateway {
    suspend fun parked(): PosResult<List<ParkedSale>>
    suspend fun park(cartId: String): PosResult<Unit>
    suspend fun resume(cartId: String): PosResult<CartProjection>
    suspend fun quotations(): PosResult<List<Quotation>>
    suspend fun createQuotation(cartId: String, validUntil: String?, notes: String?): PosResult<String>
    suspend fun sendQuotation(quotationId: String, channel: QuoteChannel, contact: String?): PosResult<Unit>
    suspend fun convertQuotation(quotationId: String): PosResult<CartProjection>
    suspend fun recentInvoices(query: String?): PosResult<List<InvoiceSummary>>

    /**
     * Runs [request] with a manager's own sign-in for this one action; the cashier's session is
     * never replaced. Returns the refreshed cart when the action changed it.
     */
    suspend fun approve(credentials: ManagerCredentials, request: ApprovalRequest, cartId: String): PosResult<CartProjection?>
}

interface EpcGateway {
    suspend fun variants(model: VehicleModel): PosResult<List<EpcVariant>>
    suspend fun sections(model: VehicleModel, variant: EpcVariant): PosResult<List<EpcSection>>
    suspend fun diagrams(model: VehicleModel, variant: EpcVariant, section: EpcSection): PosResult<List<EpcDiagram>>
    suspend fun diagram(model: VehicleModel, variant: EpcVariant, section: EpcSection, diagram: EpcDiagram): PosResult<EpcDiagramDetail>

    /** Diagram image bytes (public Storage object); decoded on the UI side. */
    suspend fun image(url: String): PosResult<ByteArray>
}
