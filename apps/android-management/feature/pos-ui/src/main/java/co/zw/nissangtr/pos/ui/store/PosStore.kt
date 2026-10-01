package co.zw.nissangtr.pos.ui.store

import co.zw.nissangtr.pos.domain.gateway.CartGateway
import co.zw.nissangtr.pos.domain.gateway.CheckoutGateway
import co.zw.nissangtr.pos.domain.gateway.CustomerGateway
import co.zw.nissangtr.pos.domain.gateway.EpcGateway
import co.zw.nissangtr.pos.domain.model.CatalogPart
import co.zw.nissangtr.pos.domain.gateway.OfflineSaleGateway
import co.zw.nissangtr.pos.domain.gateway.SalesGateway
import co.zw.nissangtr.pos.domain.gateway.CatalogGateway
import co.zw.nissangtr.pos.domain.gateway.FitmentGateway
import co.zw.nissangtr.pos.domain.gateway.PinGateway
import co.zw.nissangtr.pos.domain.gateway.SessionGateway
import co.zw.nissangtr.pos.domain.model.CartProjection
import co.zw.nissangtr.pos.domain.model.Money
import co.zw.nissangtr.pos.domain.model.Receipt
import co.zw.nissangtr.pos.domain.model.Tender
import co.zw.nissangtr.pos.domain.result.PosResult
import co.zw.nissangtr.pos.domain.state.PosEffect
import co.zw.nissangtr.pos.domain.state.PosEvent
import co.zw.nissangtr.pos.domain.state.PosIntent
import co.zw.nissangtr.pos.domain.state.PosMsg
import co.zw.nissangtr.pos.domain.state.PosNotice
import co.zw.nissangtr.pos.domain.state.PosSaleEffect
import co.zw.nissangtr.pos.domain.state.PosSaleEvent
import co.zw.nissangtr.pos.domain.state.PosState
import co.zw.nissangtr.pos.domain.state.Rollback
import co.zw.nissangtr.pos.domain.state.reduce
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import co.zw.nissangtr.pos.domain.state.CompanionEvent
import co.zw.nissangtr.pos.domain.state.CompanionEffect
import co.zw.nissangtr.pos.domain.gateway.CompanionGateway
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class PosGateways(
    val session: SessionGateway,
    val catalog: CatalogGateway,
    val fitment: FitmentGateway,
    val cart: CartGateway,
    val pins: PinGateway,
    val checkout: CheckoutGateway,
    val customers: CustomerGateway,
    val sales: SalesGateway,
    val epc: EpcGateway,
    val offline: OfflineSaleGateway = OfflineSaleGateway.None,
    val companion: CompanionGateway = CompanionGateway.None,
)

/**
 * Single store over the pure reducer (Blueprint §10.3). Screens read [state] and call [dispatch];
 * gateway calls happen only here, and their outcomes come back through the reducer as events.
 */
class PosStore(
    private val scope: CoroutineScope,
    private val gateways: PosGateways,
    initial: PosState = PosState(),
    /** Receipt timestamp source; injectable so tests and goldens are deterministic. */
    private val clock: () -> String = { java.time.OffsetDateTime.now().withNano(0).toString() },
    /** Companion poll interval (Realtime is web-only; the till polls the session and cart). */
    private val companionPollMs: Long = 3_000,
) {
    private val _state = MutableStateFlow(initial)
    val state: StateFlow<PosState> = _state.asStateFlow()

    /** Cart mutations run one at a time so a line never races its own quantity change. */
    private val cartLock = Mutex()
    private var searchJob: Job? = null
    private var companionJob: Job? = null

    fun dispatch(intent: PosIntent) = apply(intent)

    private fun apply(msg: PosMsg) {
        var effects: List<PosEffect> = emptyList()
        _state.update { current ->
            val reduction = reduce(current, msg)
            effects = reduction.effects
            reduction.state
        }
        effects.forEach(::run)
    }

    private fun run(effect: PosEffect) {
        when (effect) {
            is PosSaleEffect -> runSale(effect)
            PosEffect.LoadOperator -> launch {
                gateways.session.operator().onOk { apply(PosEvent.OperatorLoaded(it)) }
            }
            PosEffect.LoadModels -> launch {
                gateways.fitment.models().onOk { apply(PosEvent.ModelsLoaded(it)) }
            }
            is PosEffect.LoadGenerations -> launch {
                gateways.fitment.generations(effect.model).onOk { apply(PosEvent.GenerationsLoaded(effect.model, it)) }
            }
            is PosEffect.LoadEngines -> launch {
                gateways.fitment.engines(effect.model, effect.generation)
                    .onOk { apply(PosEvent.EnginesLoaded(effect.generation, it)) }
            }
            is PosEffect.Search -> {
                searchJob?.cancel()
                searchJob = scope.launch {
                    // Offline the counter searches the last catalogue snapshot (§10.12).
                    val result = if (state.value.online) gateways.catalog.search(effect.query, effect.vehicle)
                    else gateways.offline.searchLocal(effect.query)
                    result.onOk { apply(PosEvent.SearchLoaded(effect.query, it)) }
                }
            }
            PosEffect.LoadPopular -> {
                launch { gateways.pins.pins().onOk { apply(PosEvent.PinsLoaded(it)) } }
                launch { gateways.catalog.bestSellers().onOk { apply(PosEvent.BestSellersLoaded(it)) } }
                launch { gateways.pins.hiddenBestSellers().onOk { apply(PosEvent.HiddenLoaded(it)) } }
            }
            is PosEffect.AddToCart -> cartMutation {
                val cartId = state.value.cart.cartId.ifEmpty {
                    when (val opened = gateways.cart.open(state.value.currency)) {
                        is PosResult.Ok -> {
                            apply(PosEvent.CartUpdated(opened.value))
                            state.value.vehicle?.let { gateways.cart.setVehicle(opened.value.cartId, it).onOk { } }
                            opened.value.cartId
                        }
                        is PosResult.Err -> return@cartMutation opened
                    }
                }
                gateways.cart.addLine(cartId, effect.part, effect.qty)
            }
            is PosEffect.SetQuantity -> cartMutation {
                gateways.cart.setQuantity(effect.cartId, effect.lineId, effect.qty)
            }
            is PosEffect.RemoveLine -> cartMutation {
                gateways.cart.removeLine(effect.cartId, effect.lineId)
            }
            is PosEffect.SetCartVehicle -> launch {
                gateways.cart.setVehicle(effect.cartId, effect.vehicle).onOk { }
            }
            is PosEffect.PersistPin -> launch {
                outcome(
                    gateways.pins.pin(effect.pin),
                    ok = PosEvent.PinPersisted(effect.pin),
                    rollback = Rollback.RemovePinAdded(effect.pin),
                )
            }
            is PosEffect.PersistUnpin -> launch {
                outcome(
                    gateways.pins.unpin(effect.pin),
                    ok = PosEvent.UnpinPersisted(effect.pin),
                    rollback = Rollback.RestorePinRemoved(effect.pin, effect.index),
                )
            }
            is PosEffect.PersistHide -> launch {
                outcome(
                    gateways.pins.hideBestSeller(effect.stockItemId),
                    ok = PosEvent.HidePersisted(effect.stockItemId),
                    rollback = Rollback.UnhideBestSeller(effect.stockItemId),
                )
            }
        }
    }

    private fun runSale(effect: PosSaleEffect) {
        when (effect) {
            is PosSaleEffect.Checkout -> launch {
                when (val r = gateways.checkout.checkout(effect.cart.cartId, effect.tenders, effect.contacts)) {
                    is PosResult.Ok -> {
                        val change = effect.cashGiven?.let { given ->
                            val cash = effect.tenders.filter { it.tender == Tender.Cash }.sumOf { it.amount.minor }
                            Money((given.minor - cash).coerceAtLeast(0), given.currency)
                        }
                        apply(
                            PosSaleEvent.CheckoutDone(
                                Receipt(
                                    invoiceId = r.value.invoiceId,
                                    documentNumber = r.value.documentNumber,
                                    lines = effect.cart.lines,
                                    subtotal = effect.cart.subtotal,
                                    discount = effect.cart.discount,
                                    total = effect.cart.total,
                                    tenders = effect.tenders,
                                    cashGiven = effect.cashGiven,
                                    change = change,
                                    customerName = effect.customerName,
                                    vehicleLabel = effect.vehicleLabel,
                                    operatorName = effect.operatorName,
                                    issuedAtIso = clock(),
                                ),
                            ),
                        )
                    }
                    is PosResult.Err -> apply(PosSaleEvent.PaymentFailed(r.error))
                }
            }
            is PosSaleEffect.EcoCash -> launch {
                gateways.checkout.requestEcoCash(effect.msisdn, effect.amount, effect.reference)
                    .onOk { apply(PosSaleEvent.EcoCashSent(it)) }
            }
            is PosSaleEffect.SearchCustomers -> launch {
                gateways.customers.search(effect.query).onOk { apply(PosSaleEvent.CustomersLoaded(effect.query, it)) }
            }
            is PosSaleEffect.SaveCustomer -> launch {
                val r = effect.customerId?.let { gateways.customers.update(it, effect.draft) } ?: gateways.customers.create(effect.draft)
                r.onOk { apply(PosSaleEvent.CustomerSaved(it)) }
            }
            is PosSaleEffect.LoadGarage -> launch {
                gateways.customers.garage(effect.customerId).onOk { apply(PosSaleEvent.GarageLoaded(effect.customerId, it)) }
            }
            is PosSaleEffect.AttachCustomer -> launch {
                gateways.customers.attach(effect.cartId, effect.customerId).onOk { }
            }
            is PosSaleEffect.SaveToGarage -> launch {
                gateways.customers.saveToGarage(effect.customerId, effect.vehicle, effect.primary).onOk { apply(PosSaleEvent.VehicleSaved) }
            }
            is PosSaleEffect.Approve -> launch {
                when (val r = gateways.sales.approve(effect.credentials, effect.request, effect.cartId)) {
                    is PosResult.Ok -> apply(PosSaleEvent.Approved(effect.request, r.value))
                    is PosResult.Err -> apply(PosSaleEvent.ApprovalFailed(r.error))
                }
            }
            is PosSaleEffect.Park -> launch {
                gateways.sales.park(effect.cartId).onOk { apply(PosSaleEvent.SaleParked) }
            }
            is PosSaleEffect.Resume -> launch {
                gateways.sales.resume(effect.cartId).onOk { apply(PosSaleEvent.CartReplaced(it, PosNotice.SaleResumed)) }
            }
            PosSaleEffect.LoadOrders -> {
                launch { gateways.sales.parked().onOk { apply(PosSaleEvent.OrdersLoaded(it, null)) } }
                launch { gateways.sales.quotations().onOk { apply(PosSaleEvent.OrdersLoaded(null, it)) } }
            }
            is PosSaleEffect.CreateQuotation -> launch {
                gateways.sales.createQuotation(effect.cartId, effect.validUntil, effect.notes).onOk { apply(PosSaleEvent.QuoteCreated) }
            }
            is PosSaleEffect.SendQuotation -> launch {
                gateways.sales.sendQuotation(effect.quotationId, effect.channel, effect.contact).onOk { apply(PosSaleEvent.QuoteSent) }
            }
            is PosSaleEffect.ConvertQuotation -> launch {
                gateways.sales.convertQuotation(effect.quotationId).onOk { apply(PosSaleEvent.CartReplaced(it, PosNotice.QuoteConverted)) }
            }
            is PosSaleEffect.LoadInvoices -> launch {
                gateways.sales.recentInvoices(effect.query.ifBlank { null }).onOk { apply(PosSaleEvent.InvoicesLoaded(effect.query, it)) }
            }
            is PosSaleEffect.EpcVariants -> launch {
                gateways.epc.variants(effect.model).onOk { apply(PosSaleEvent.EpcVariantsLoaded(effect.model, it)) }
            }
            is PosSaleEffect.EpcSections -> launch {
                gateways.epc.sections(effect.model, effect.variant).onOk { apply(PosSaleEvent.EpcSectionsLoaded(effect.variant, it)) }
            }
            is PosSaleEffect.EpcDiagrams -> launch {
                gateways.epc.diagrams(effect.model, effect.variant, effect.section).onOk { apply(PosSaleEvent.EpcDiagramsLoaded(effect.section, it)) }
            }
            is PosSaleEffect.EpcDetail -> launch {
                gateways.epc.diagram(effect.model, effect.variant, effect.section, effect.diagram).onOk { apply(PosSaleEvent.EpcDetailLoaded(it)) }
            }
            is CompanionEffect -> runCompanion(effect)
            is PosSaleEffect.QueueOfflineSale -> launch {
                when (val r = gateways.offline.queueCashSale(effect.cart, effect.vehicle, effect.contacts)) {
                    is PosResult.Ok -> apply(PosSaleEvent.OfflineSaleQueued(effect, r.value))
                    is PosResult.Err -> apply(PosSaleEvent.PaymentFailed(r.error))
                }
            }
            is PosSaleEffect.PromoteLocalCart -> cartMutation {
                // The reducer already released the local cart; rebuild it line by line on the server.
                val opened = gateways.cart.open(effect.cart.currency)
                if (opened !is PosResult.Ok) return@cartMutation opened
                state.value.vehicle?.let { gateways.cart.setVehicle(opened.value.cartId, it).onOk { } }
                var last: PosResult<CartProjection> = opened
                for (line in effect.cart.lines) {
                    val part = CatalogPart(line.stockItemId, line.oemPartNumber, line.name, line.unitPrice, null, line.imageUrl)
                    last = gateways.cart.addLine(opened.value.cartId, part, line.qty)
                    if (last is PosResult.Err) break
                }
                last
            }
            PosSaleEffect.SyncOffline -> launch {
                when (val r = gateways.offline.sync()) {
                    is PosResult.Ok -> apply(PosSaleEvent.OfflineStatusLoaded(r.value, afterSync = true))
                    is PosResult.Err -> apply(PosSaleEvent.OfflineSyncFailed(r.error))
                }
            }
            PosSaleEffect.LoadOfflineStatus -> launch {
                gateways.offline.status().onOk { apply(PosSaleEvent.OfflineStatusLoaded(it, afterSync = false)) }
            }
            is PosSaleEffect.EpcLoadImage -> launch {
                // A missing image is not an error: the parts list still sells.
                val bytes = (gateways.epc.image(effect.url) as? PosResult.Ok)?.value
                apply(PosSaleEvent.EpcImageLoaded(effect.url, bytes))
            }
            is PosSaleEffect.EpcResolve -> launch {
                when (val r = gateways.catalog.search(effect.oemPartNumber, null)) {
                    is PosResult.Ok -> apply(
                        PosSaleEvent.EpcResolved(effect.oemPartNumber, r.value.firstOrNull { it.oemKey == effect.oemPartNumber.trim().uppercase() }),
                    )
                    is PosResult.Err -> apply(PosSaleEvent.EpcResolved(effect.oemPartNumber, null))
                }
            }
        }
    }

    private fun runCompanion(effect: CompanionEffect) {
        when (effect) {
            is CompanionEffect.Create -> launch {
                var opened: CartProjection? = null
                val cartId = effect.cartId ?: when (val r = gateways.cart.open(state.value.currency)) {
                    is PosResult.Ok -> r.value.also { opened = it }.cartId
                    is PosResult.Err -> return@launch apply(CompanionEvent.Failed(r.error))
                }
                if (opened != null) state.value.vehicle?.let { gateways.cart.setVehicle(cartId, it) }
                when (val r = gateways.companion.create(cartId)) {
                    is PosResult.Ok -> apply(CompanionEvent.Created(r.value, opened))
                    is PosResult.Err -> apply(CompanionEvent.Failed(r.error))
                }
            }
            is CompanionEffect.Revoke -> {
                companionJob?.cancel()
                // Best effort: an unreachable server expires the code on its own.
                launch { gateways.companion.revoke(effect.sessionId) }
            }
            is CompanionEffect.ClaimCode -> launch {
                when (val r = gateways.companion.claim(effect.pairingCode)) {
                    is PosResult.Ok -> apply(CompanionEvent.Claimed(r.value))
                    is PosResult.Err -> apply(CompanionEvent.ScannerFailed(r.error))
                }
            }
            is CompanionEffect.AddFromQr -> launch {
                when (val r = gateways.companion.addFromQr(effect.cartId, effect.payload)) {
                    is PosResult.Ok -> apply(CompanionEvent.ScanAdded(r.value))
                    is PosResult.Err -> apply(CompanionEvent.ScannerFailed(r.error))
                }
            }
            is CompanionEffect.Watch -> {
                companionJob?.cancel()
                companionJob = scope.launch {
                    while (state.value.companion?.let { it.sessionId == effect.sessionId && it.live } == true) {
                        delay(companionPollMs)
                        val status = (gateways.companion.status(effect.sessionId) as? PosResult.Ok)?.value ?: continue
                        val cart = (gateways.companion.cart(effect.cartId) as? PosResult.Ok)?.value
                        apply(CompanionEvent.Polled(effect.sessionId, status, cart))
                    }
                }
            }
        }
    }

    private fun cartMutation(block: suspend () -> PosResult<CartProjection>) {
        scope.launch {
            cartLock.withLock {
                when (val result = block()) {
                    is PosResult.Ok -> apply(PosEvent.CartUpdated(result.value))
                    is PosResult.Err -> apply(PosEvent.Failed(result.error))
                }
                apply(PosEvent.CartMutationFinished)
            }
        }
    }

    /** Reads surface failures as feedback; a load failure is never silently swallowed. */
    private fun launch(block: suspend () -> Unit) {
        scope.launch { block() }
    }

    private inline fun <T> PosResult<T>.onOk(action: (T) -> Unit) {
        when (this) {
            is PosResult.Ok -> action(value)
            is PosResult.Err -> apply(PosEvent.Failed(error))
        }
    }

    /** Optimistic mutation outcome: confirm, or roll back and report (§7.4 — never swallowed). */
    private fun outcome(result: PosResult<Unit>, ok: PosEvent, rollback: Rollback) {
        when (result) {
            is PosResult.Ok -> apply(ok)
            is PosResult.Err -> apply(PosEvent.Failed(result.error, rollback))
        }
    }
}
