package co.zw.nissangtr.pos.domain.state

import co.zw.nissangtr.pos.domain.error.PosError
import co.zw.nissangtr.pos.domain.model.ApprovalRequest
import co.zw.nissangtr.pos.domain.model.CartProjection
import co.zw.nissangtr.pos.domain.model.CatalogPart
import co.zw.nissangtr.pos.domain.model.Customer
import co.zw.nissangtr.pos.domain.model.CustomerDraft
import co.zw.nissangtr.pos.domain.model.EpcDiagram
import co.zw.nissangtr.pos.domain.model.EpcDiagramDetail
import co.zw.nissangtr.pos.domain.model.EpcImage
import co.zw.nissangtr.pos.domain.model.EpcPart
import co.zw.nissangtr.pos.domain.model.EpcSection
import co.zw.nissangtr.pos.domain.model.EpcVariant
import co.zw.nissangtr.pos.domain.model.GarageVehicle
import co.zw.nissangtr.pos.domain.model.InvoiceSummary
import co.zw.nissangtr.pos.domain.model.ManagerCredentials
import co.zw.nissangtr.pos.domain.model.Money
import co.zw.nissangtr.pos.domain.model.ParkedSale
import co.zw.nissangtr.pos.domain.model.Quotation
import co.zw.nissangtr.pos.domain.model.QuoteChannel
import co.zw.nissangtr.pos.domain.model.Receipt
import co.zw.nissangtr.pos.domain.model.ReceiptContacts
import co.zw.nissangtr.pos.domain.model.Tender
import co.zw.nissangtr.pos.domain.model.TenderLine
import co.zw.nissangtr.pos.domain.model.VehicleCascade
import co.zw.nissangtr.pos.domain.model.VehicleModel
import co.zw.nissangtr.pos.domain.model.VehicleSelection

// ---------------------------------------------------------------- intents

sealed interface PosSaleIntent : PosIntent {
    data object OpenPayment : PosSaleIntent
    data object ClosePayment : PosSaleIntent
    data class Checkout(val tenders: List<TenderLine>, val cashGiven: Money?, val contacts: ReceiptContacts) : PosSaleIntent
    data class RequestEcoCash(val msisdn: String, val amount: Money) : PosSaleIntent
    data object NewSale : PosSaleIntent

    data class SearchCustomers(val query: String) : PosSaleIntent
    data class SelectCustomer(val customer: Customer) : PosSaleIntent
    data object ClearCustomer : PosSaleIntent
    data class CreateCustomer(val draft: CustomerDraft) : PosSaleIntent
    data class UpdateCustomer(val customerId: String, val draft: CustomerDraft) : PosSaleIntent
    data class ChooseGarageVehicle(val vehicle: GarageVehicle?) : PosSaleIntent
    data class SaveVehicleToGarage(val primary: Boolean) : PosSaleIntent

    data class RequestApproval(val request: ApprovalRequest) : PosSaleIntent
    data object CancelApproval : PosSaleIntent
    data class SubmitApproval(val credentials: ManagerCredentials) : PosSaleIntent

    data object Park : PosSaleIntent
    data class Resume(val sale: ParkedSale) : PosSaleIntent
    data object LoadOrders : PosSaleIntent
    data class CreateQuotation(val validUntil: String?, val notes: String?) : PosSaleIntent
    data class SendQuotation(val quotation: Quotation, val channel: QuoteChannel, val contact: String?) : PosSaleIntent
    data class ConvertQuotation(val quotation: Quotation) : PosSaleIntent
    data class LoadInvoices(val query: String) : PosSaleIntent

    data class EpcPickModel(val model: VehicleModel) : PosSaleIntent
    data class EpcPickVariant(val variant: EpcVariant) : PosSaleIntent
    data class EpcPickSection(val section: EpcSection) : PosSaleIntent
    data class EpcPickDiagram(val diagram: EpcDiagram) : PosSaleIntent
    data object EpcBack : PosSaleIntent
    /** Tap on a diagram callout or a parts row; tapping the selected one again clears it. */
    data class EpcSelect(val oemPartNumber: String) : PosSaleIntent
    /** Add a diagram part: the exact stocked OEM goes straight to the cart, else search for it. */
    data class EpcAdd(val part: EpcPart) : PosSaleIntent

    data class SetHaptics(val enabled: Boolean) : PosSaleIntent
}

// ---------------------------------------------------------------- events

sealed interface PosSaleEvent : PosEvent {
    data class CustomersLoaded(val query: String, val customers: List<Customer>) : PosSaleEvent
    data class CustomerSaved(val customer: Customer) : PosSaleEvent
    data class GarageLoaded(val customerId: String, val vehicles: List<GarageVehicle>) : PosSaleEvent
    data object VehicleSaved : PosSaleEvent
    data class CheckoutDone(val receipt: Receipt) : PosSaleEvent
    data class EcoCashSent(val reference: String) : PosSaleEvent
    data class Approved(val request: ApprovalRequest, val cart: CartProjection?) : PosSaleEvent
    data class ApprovalFailed(val error: PosError) : PosSaleEvent
    data class OrdersLoaded(val parked: List<ParkedSale>?, val quotations: List<Quotation>?) : PosSaleEvent
    data object SaleParked : PosSaleEvent
    data class CartReplaced(val cart: CartProjection, val notice: PosNotice) : PosSaleEvent
    data object QuoteCreated : PosSaleEvent
    data object QuoteSent : PosSaleEvent
    data class InvoicesLoaded(val query: String, val invoices: List<InvoiceSummary>) : PosSaleEvent
    data class EpcVariantsLoaded(val model: VehicleModel, val variants: List<EpcVariant>) : PosSaleEvent
    data class EpcSectionsLoaded(val variant: EpcVariant, val sections: List<EpcSection>) : PosSaleEvent
    data class EpcDiagramsLoaded(val section: EpcSection, val diagrams: List<EpcDiagram>) : PosSaleEvent
    data class EpcDetailLoaded(val detail: EpcDiagramDetail) : PosSaleEvent
    data class EpcImageLoaded(val url: String, val bytes: ByteArray?) : PosSaleEvent
    data class EpcResolved(val oemPartNumber: String, val part: CatalogPart?) : PosSaleEvent
    data class PaymentFailed(val error: PosError) : PosSaleEvent
}

// ---------------------------------------------------------------- effects

sealed interface PosSaleEffect : PosEffect {
    data class Checkout(
        val cart: CartProjection,
        val tenders: List<TenderLine>,
        val cashGiven: Money?,
        val contacts: ReceiptContacts,
        val customerName: String?,
        val vehicleLabel: String?,
        val operatorName: String?,
    ) : PosSaleEffect
    data class EcoCash(val msisdn: String, val amount: Money, val reference: String) : PosSaleEffect
    data class SearchCustomers(val query: String) : PosSaleEffect
    data class SaveCustomer(val customerId: String?, val draft: CustomerDraft) : PosSaleEffect
    data class LoadGarage(val customerId: String) : PosSaleEffect
    data class AttachCustomer(val cartId: String, val customerId: String?) : PosSaleEffect
    data class SaveToGarage(val customerId: String, val vehicle: VehicleSelection, val primary: Boolean) : PosSaleEffect
    data class Approve(val credentials: ManagerCredentials, val request: ApprovalRequest, val cartId: String) : PosSaleEffect
    data class Park(val cartId: String) : PosSaleEffect
    data class Resume(val cartId: String) : PosSaleEffect
    data object LoadOrders : PosSaleEffect
    data class CreateQuotation(val cartId: String, val validUntil: String?, val notes: String?) : PosSaleEffect
    data class SendQuotation(val quotationId: String, val channel: QuoteChannel, val contact: String?) : PosSaleEffect
    data class ConvertQuotation(val quotationId: String) : PosSaleEffect
    data class LoadInvoices(val query: String) : PosSaleEffect
    data class EpcVariants(val model: VehicleModel) : PosSaleEffect
    data class EpcSections(val model: VehicleModel, val variant: EpcVariant) : PosSaleEffect
    data class EpcDiagrams(val model: VehicleModel, val variant: EpcVariant, val section: EpcSection) : PosSaleEffect
    data class EpcDetail(val model: VehicleModel, val variant: EpcVariant, val section: EpcSection, val diagram: EpcDiagram) : PosSaleEffect
    data class EpcLoadImage(val url: String) : PosSaleEffect
    data class EpcResolve(val oemPartNumber: String) : PosSaleEffect
}

// ---------------------------------------------------------------- reducer

private fun notice(n: PosNotice) = PosFeedback.Notice(n)
private fun failure(e: PosError) = PosFeedback.Failure(e)

/** Money that the sale must settle: the server cart total, never a till calculation. */
private fun PosState.balance(): Money = cart.total

internal fun reduceSaleIntent(state: PosState, intent: PosSaleIntent): Reduction = when (intent) {
    PosSaleIntent.OpenPayment -> when {
        state.cart.isEmpty -> Reduction(state)
        !state.online -> Reduction(state.copy(feedback = failure(PosError.OfflineRestricted(setOf("checkout")))))
        else -> Reduction(state.copy(paymentOpen = true, ecoCashReference = null))
    }

    PosSaleIntent.ClosePayment -> Reduction(state.copy(paymentOpen = state.paying, receipt = null))

    is PosSaleIntent.Checkout -> {
        val paid = intent.tenders.sumOf { it.amount.minor }
        when {
            state.paying || state.cart.isEmpty -> Reduction(state)
            intent.tenders.any { it.amount.minor <= 0 } ->
                Reduction(state.copy(feedback = failure(PosError.Input("tender", "amount"))))
            paid != state.balance().minor ->
                Reduction(state.copy(feedback = failure(PosError.BusinessRule("tenders_unbalanced", ""))))
            intent.tenders.any { it.tender != Tender.Cash } && !state.online ->
                Reduction(state.copy(feedback = failure(PosError.OfflineRestricted(setOf("non_cash")))))
            else -> Reduction(
                state.copy(paying = true, feedback = null),
                listOf(
                    PosSaleEffect.Checkout(
                        cart = state.cart,
                        tenders = intent.tenders,
                        cashGiven = intent.cashGiven,
                        contacts = intent.contacts,
                        customerName = state.customer?.displayName,
                        vehicleLabel = state.vehicle?.label,
                        operatorName = state.operator?.displayName,
                    ),
                ),
            )
        }
    }

    is PosSaleIntent.RequestEcoCash -> {
        val msisdn = intent.msisdn.filter { it.isDigit() || it == '+' }
        if (msisdn.length < 9 || state.cart.cartId.isEmpty()) {
            Reduction(state.copy(feedback = failure(PosError.Input("phone", "msisdn"))))
        } else {
            Reduction(state, listOf(PosSaleEffect.EcoCash(msisdn, intent.amount, "POS-${state.cart.cartId.take(8)}")))
        }
    }

    PosSaleIntent.NewSale -> Reduction(
        state.copy(
            receipt = null,
            paymentOpen = false,
            customer = null,
            garage = emptyList(),
            vehicle = null,
            cascade = VehicleCascade(models = state.cascade.models),
            destination = PosDestination.Home,
        ),
    )

    is PosSaleIntent.SearchCustomers -> {
        val q = intent.query.trim()
        if (q.length < 2) Reduction(state.copy(customerResults = null, customerSearching = false))
        else Reduction(state.copy(customerSearching = true), listOf(PosSaleEffect.SearchCustomers(q)))
    }

    is PosSaleIntent.SelectCustomer -> Reduction(
        state.copy(customer = intent.customer, garage = emptyList(), garagePrompt = false),
        listOfNotNull(
            PosSaleEffect.LoadGarage(intent.customer.id),
            state.cart.cartId.takeIf { it.isNotEmpty() }?.let { PosSaleEffect.AttachCustomer(it, intent.customer.id) },
        ),
    )

    PosSaleIntent.ClearCustomer -> Reduction(
        state.copy(customer = null, garage = emptyList(), garagePrompt = false),
        listOfNotNull(state.cart.cartId.takeIf { it.isNotEmpty() }?.let { PosSaleEffect.AttachCustomer(it, null) }),
    )

    is PosSaleIntent.CreateCustomer -> validateDraft(state, intent.draft)
        ?: Reduction(state, listOf(PosSaleEffect.SaveCustomer(null, intent.draft)))

    is PosSaleIntent.UpdateCustomer -> validateDraft(state, intent.draft)
        ?: Reduction(state, listOf(PosSaleEffect.SaveCustomer(intent.customerId, intent.draft)))

    is PosSaleIntent.ChooseGarageVehicle -> {
        val selection = intent.vehicle?.selection()
        if (selection == null) {
            Reduction(state.copy(garagePrompt = false))
        } else {
            val base = reduce(state.copy(garagePrompt = false), PosIntent.ClearVehicle).state
            val cascade = base.cascade.copy(
                model = base.cascade.models.firstOrNull { it.slug == selection.modelSlug }
                    ?: co.zw.nissangtr.pos.domain.model.VehicleModel(selection.modelSlug, selection.modelName),
                generation = co.zw.nissangtr.pos.domain.model.VehicleGeneration(selection.chassisCode, selection.generation),
                engine = selection.engineCode,
            )
            Reduction(
                base.copy(cascade = cascade, vehicle = selection),
                listOfNotNull(state.cart.cartId.takeIf { it.isNotEmpty() }?.let { PosEffect.SetCartVehicle(it, selection) }),
            )
        }
    }

    is PosSaleIntent.SaveVehicleToGarage -> {
        val customer = state.customer
        val vehicle = state.vehicle
        if (customer == null || vehicle == null) Reduction(state)
        else Reduction(state, listOf(PosSaleEffect.SaveToGarage(customer.id, vehicle, intent.primary)))
    }

    is PosSaleIntent.RequestApproval -> when {
        intent.request !is ApprovalRequest.Refund && state.cart.isEmpty -> Reduction(state)
        intent.request is ApprovalRequest.Discount && intent.request.percent !in 0.01..100.0 ->
            Reduction(state.copy(feedback = failure(PosError.Input("discount", "percent"))))
        intent.request is ApprovalRequest.PriceOverride && intent.request.unitPrice < 0 ->
            Reduction(state.copy(feedback = failure(PosError.Input("price", "unit_price"))))
        else -> Reduction(state.copy(approval = intent.request))
    }

    PosSaleIntent.CancelApproval -> Reduction(state.copy(approval = if (state.approving) state.approval else null))

    is PosSaleIntent.SubmitApproval -> {
        val request = state.approval
        when {
            request == null || state.approving -> Reduction(state)
            intent.credentials.identifier.isBlank() || intent.credentials.password.isEmpty() ->
                Reduction(state.copy(feedback = failure(PosError.Input("manager", "credentials"))))
            else -> Reduction(
                state.copy(approving = true),
                listOf(PosSaleEffect.Approve(intent.credentials, request, state.cart.cartId)),
            )
        }
    }

    PosSaleIntent.Park -> if (state.cart.isEmpty) Reduction(state) else Reduction(state, listOf(PosSaleEffect.Park(state.cart.cartId)))

    is PosSaleIntent.Resume -> if (!state.cart.isEmpty) {
        Reduction(state.copy(feedback = failure(PosError.BusinessRule("cart_not_empty", ""))))
    } else {
        Reduction(state, listOf(PosSaleEffect.Resume(intent.sale.id)))
    }

    PosSaleIntent.LoadOrders -> Reduction(state, listOf(PosSaleEffect.LoadOrders))

    is PosSaleIntent.CreateQuotation -> if (state.cart.isEmpty) Reduction(state)
    else Reduction(state, listOf(PosSaleEffect.CreateQuotation(state.cart.cartId, intent.validUntil, intent.notes)))

    is PosSaleIntent.SendQuotation -> Reduction(state, listOf(PosSaleEffect.SendQuotation(intent.quotation.id, intent.channel, intent.contact)))

    is PosSaleIntent.ConvertQuotation -> if (!state.cart.isEmpty) {
        Reduction(state.copy(feedback = failure(PosError.BusinessRule("cart_not_empty", ""))))
    } else {
        Reduction(state, listOf(PosSaleEffect.ConvertQuotation(intent.quotation.id)))
    }

    is PosSaleIntent.LoadInvoices -> Reduction(
        state.copy(invoiceQuery = intent.query),
        listOf(PosSaleEffect.LoadInvoices(intent.query.trim())),
    )

    is PosSaleIntent.EpcPickModel -> Reduction(
        state.copy(epc = EpcBrowse(model = intent.model, loading = true)),
        listOf(PosSaleEffect.EpcVariants(intent.model)),
    )

    is PosSaleIntent.EpcPickVariant -> state.epc.model?.let { model ->
        Reduction(
            state.copy(epc = state.epc.copy(variant = intent.variant, sections = null, section = null, diagrams = null, detail = null, loading = true)),
            listOf(PosSaleEffect.EpcSections(model, intent.variant)),
        )
    } ?: Reduction(state)

    is PosSaleIntent.EpcPickSection -> {
        val model = state.epc.model
        val variant = state.epc.variant
        if (model == null || variant == null) Reduction(state)
        else Reduction(
            state.copy(epc = state.epc.copy(section = intent.section, diagrams = null, detail = null, loading = true)),
            listOf(PosSaleEffect.EpcDiagrams(model, variant, intent.section)),
        )
    }

    is PosSaleIntent.EpcPickDiagram -> {
        val model = state.epc.model
        val variant = state.epc.variant
        val section = state.epc.section
        if (model == null || variant == null || section == null) Reduction(state)
        else Reduction(
            state.copy(epc = state.epc.copy(detail = null, image = null, activeOem = null, loading = true)),
            listOf(PosSaleEffect.EpcDetail(model, variant, section, intent.diagram)),
        )
    }

    PosSaleIntent.EpcBack -> Reduction(
        state.copy(
            epc = with(state.epc) {
                when {
                    detail != null -> copy(detail = null, image = null, activeOem = null)
                    section != null -> copy(section = null, diagrams = null)
                    variant != null -> copy(variant = null, sections = null)
                    else -> EpcBrowse()
                }.copy(loading = false)
            },
        ),
    )

    is PosSaleIntent.EpcSelect -> Reduction(
        state.copy(epc = state.epc.copy(activeOem = intent.oemPartNumber.takeUnless { it.equals(state.epc.activeOem, ignoreCase = true) })),
    )

    is PosSaleIntent.EpcAdd -> if (intent.part.oemPartNumber.isBlank()) Reduction(state)
    else Reduction(
        state.copy(epc = state.epc.copy(activeOem = intent.part.oemPartNumber)),
        listOf(PosSaleEffect.EpcResolve(intent.part.oemPartNumber)),
    )

    is PosSaleIntent.SetHaptics -> Reduction(state.copy(hapticsEnabled = intent.enabled))
}

private fun validateDraft(state: PosState, draft: CustomerDraft): Reduction? = when {
    draft.displayName.isBlank() -> Reduction(state.copy(feedback = failure(PosError.Input("name", "required"))))
    draft.email != null && draft.email.isNotBlank() && !draft.email.contains('@') ->
        Reduction(state.copy(feedback = failure(PosError.Input("email", "format"))))
    else -> null
}

internal fun reduceSaleEvent(state: PosState, event: PosSaleEvent): Reduction = when (event) {
    is PosSaleEvent.CustomersLoaded -> Reduction(state.copy(customerResults = event.customers, customerSearching = false))

    is PosSaleEvent.CustomerSaved -> reduceSaleIntent(
        state.copy(feedback = notice(PosNotice.CustomerSaved), customerResults = listOf(event.customer)),
        PosSaleIntent.SelectCustomer(event.customer),
    )

    is PosSaleEvent.GarageLoaded -> if (state.customer?.id != event.customerId) {
        Reduction(state)
    } else {
        val usable = event.vehicles.filter { it.selection() != null }
        when {
            state.vehicle != null || usable.isEmpty() -> Reduction(state.copy(garage = event.vehicles))
            usable.size == 1 -> reduceSaleIntent(state.copy(garage = event.vehicles), PosSaleIntent.ChooseGarageVehicle(usable.single()))
            else -> Reduction(state.copy(garage = event.vehicles, garagePrompt = true))
        }
    }

    PosSaleEvent.VehicleSaved -> Reduction(
        state.copy(feedback = notice(PosNotice.VehicleSaved)),
        listOfNotNull(state.customer?.let { PosSaleEffect.LoadGarage(it.id) }),
    )

    is PosSaleEvent.CheckoutDone -> Reduction(
        state.copy(
            paying = false,
            receipt = event.receipt,
            cart = CartProjection.empty(state.currency),
            feedback = null,
        ),
        listOf(PosEffect.LoadPopular),
    )

    is PosSaleEvent.PaymentFailed -> Reduction(state.copy(paying = false, feedback = failure(event.error)))

    is PosSaleEvent.EcoCashSent -> Reduction(state.copy(ecoCashReference = event.reference, feedback = notice(PosNotice.EcoCashSent)))

    is PosSaleEvent.Approved -> {
        val cart = event.cart ?: state.cart
        val next = state.copy(approval = null, approving = false, cart = cart, feedback = notice(
            if (event.request is ApprovalRequest.Refund) PosNotice.Refunded else PosNotice.Approved,
        ))
        if (event.request is ApprovalRequest.Refund) Reduction(next, listOf(PosSaleEffect.LoadInvoices(state.invoiceQuery.trim())))
        else Reduction(next)
    }

    is PosSaleEvent.ApprovalFailed -> Reduction(state.copy(approving = false, feedback = failure(event.error)))

    is PosSaleEvent.OrdersLoaded -> Reduction(
        state.copy(parked = event.parked ?: state.parked, quotations = event.quotations ?: state.quotations),
    )

    PosSaleEvent.SaleParked -> Reduction(
        state.copy(
            cart = CartProjection.empty(state.currency),
            customer = null,
            garage = emptyList(),
            vehicle = null,
            cascade = VehicleCascade(models = state.cascade.models),
            feedback = notice(PosNotice.SaleParked),
        ),
        listOf(PosSaleEffect.LoadOrders),
    )

    is PosSaleEvent.CartReplaced -> Reduction(
        state.copy(cart = event.cart, currency = event.cart.currency, destination = PosDestination.Home, feedback = notice(event.notice)),
        listOf(PosSaleEffect.LoadOrders),
    )

    PosSaleEvent.QuoteCreated -> Reduction(state.copy(feedback = notice(PosNotice.QuoteCreated)), listOf(PosSaleEffect.LoadOrders))
    PosSaleEvent.QuoteSent -> Reduction(state.copy(feedback = notice(PosNotice.QuoteSent)), listOf(PosSaleEffect.LoadOrders))

    is PosSaleEvent.InvoicesLoaded -> if (event.query != state.invoiceQuery.trim()) Reduction(state)
    else Reduction(state.copy(invoices = event.invoices))

    is PosSaleEvent.EpcVariantsLoaded -> if (state.epc.model != event.model) Reduction(state)
    else Reduction(state.copy(epc = state.epc.copy(variants = event.variants, loading = false)))

    is PosSaleEvent.EpcSectionsLoaded -> if (state.epc.variant != event.variant) Reduction(state)
    else Reduction(state.copy(epc = state.epc.copy(sections = event.sections, loading = false)))

    is PosSaleEvent.EpcDiagramsLoaded -> if (state.epc.section != event.section) Reduction(state)
    else {
        val listed = state.copy(epc = state.epc.copy(diagrams = event.diagrams, loading = false))
        // A section with a single diagram opens it straight away (same as web).
        event.diagrams.singleOrNull()?.let { reduceSaleIntent(listed, PosSaleIntent.EpcPickDiagram(it)) } ?: Reduction(listed)
    }

    is PosSaleEvent.EpcDetailLoaded -> if (state.epc.section == null) Reduction(state)
    else Reduction(
        state.copy(epc = state.epc.copy(detail = event.detail, image = null, activeOem = null, loading = false)),
        listOfNotNull(event.detail.imageUrl?.let { PosSaleEffect.EpcLoadImage(it) }),
    )

    is PosSaleEvent.EpcImageLoaded -> if (state.epc.detail?.imageUrl != event.url) Reduction(state)
    else Reduction(state.copy(epc = state.epc.copy(image = EpcImage(event.url, event.bytes))))

    is PosSaleEvent.EpcResolved -> event.part
        ?.takeIf { it.stockItemId != null && it.oemKey == event.oemPartNumber.trim().uppercase() }
        ?.let { reduce(state, PosIntent.AddPart(it)) }
        ?: reduce(state, PosIntent.SearchFor(event.oemPartNumber))
}
