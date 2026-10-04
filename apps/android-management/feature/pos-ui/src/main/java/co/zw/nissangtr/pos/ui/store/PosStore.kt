package co.zw.nissangtr.pos.ui.store

import co.zw.nissangtr.pos.domain.model.ApprovalRequest
import co.zw.nissangtr.pos.domain.model.ReturnResolution
import co.zw.nissangtr.pos.domain.state.ReturnsEvent
import co.zw.nissangtr.pos.domain.state.ReturnsEffect
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
import co.zw.nissangtr.pos.domain.model.TenderLine
import co.zw.nissangtr.pos.domain.model.DigitalProvider
import co.zw.nissangtr.pos.domain.error.PosError
import co.zw.nissangtr.pos.domain.gateway.ReserveCheckoutGateway
import co.zw.nissangtr.pos.domain.state.CheckoutEffect
import co.zw.nissangtr.pos.domain.state.CheckoutEvent
import co.zw.nissangtr.pos.domain.state.SplitEffect
import co.zw.nissangtr.pos.domain.state.TerminalEffect
import co.zw.nissangtr.pos.domain.state.TerminalEvent
import co.zw.nissangtr.pos.domain.state.SplitEvent
import co.zw.nissangtr.pos.domain.gateway.SplitPaymentGateway
import co.zw.nissangtr.pos.domain.model.SplitTender
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
import co.zw.nissangtr.pos.domain.gateway.TillGateway
import co.zw.nissangtr.pos.domain.gateway.GovernanceGateway
import co.zw.nissangtr.pos.domain.state.GovernanceEffect
import co.zw.nissangtr.pos.domain.state.GovernanceEvent
import co.zw.nissangtr.pos.domain.state.TillEffect
import co.zw.nissangtr.pos.domain.state.TillEvent
import co.zw.nissangtr.pos.domain.state.serverCartId
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
    val till: TillGateway = TillGateway.None,
    val governance: GovernanceGateway = GovernanceGateway.None,
    /** Front-camera reader for manager ID badges (QR bridge). */
    val badgeScanner: co.zw.nissangtr.pos.domain.gateway.BadgeScanner = co.zw.nissangtr.pos.domain.gateway.BadgeScanner.None,
    /** Reserve-first checkout (§10.6); [ReserveCheckoutGateway.None] keeps the one-step checkout. */
    val reserve: ReserveCheckoutGateway = ReserveCheckoutGateway.None,
    /** Part payments (staged split) on the reserved order. */
    val split: SplitPaymentGateway = SplitPaymentGateway.None,
    /** Card machine (ECR) through the card-terminal bridge. */
    val terminal: co.zw.nissangtr.pos.domain.gateway.CardTerminalGateway = co.zw.nissangtr.pos.domain.gateway.CardTerminalGateway.None,
    /** Returns, old cores, warranty claims and stock by branch. */
    val returns: co.zw.nissangtr.pos.domain.gateway.ReturnsGateway = co.zw.nissangtr.pos.domain.gateway.ReturnsGateway.None,
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
    /** Reserved-order poll interval while payment is open (provider answer, expiry). */
    private val checkoutPollMs: Long = 3_000,
    private val nowMs: () -> Long = System::currentTimeMillis,
    /** Idempotency keys for reservations and settlements (§10.6). */
    private val newKey: () -> String = { java.util.UUID.randomUUID().toString() },
) {
    private val _state = MutableStateFlow(initial)
    val state: StateFlow<PosState> = _state.asStateFlow()

    /** Cart mutations run one at a time so a line never races its own quantity change. */
    private val cartLock = Mutex()
    private var searchJob: Job? = null
    private var companionJob: Job? = null
    private var checkoutJob: Job? = null

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
                    else gateways.offline.searchLocal(effect.query, effect.vehicle)
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
                            attachToTill(opened.value.cartId)
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
            is PosSaleEffect.Approve -> if (effect.request is ApprovalRequest.CardRefund) runCardRefund(effect) else launch {
                when (val r = gateways.sales.approve(effect.credentials, effect.request, effect.cartId, effect.reason, effect.notes, effect.badge)) {
                    is PosResult.Ok -> apply(PosSaleEvent.Approved(effect.request, r.value))
                    is PosResult.Err -> apply(PosSaleEvent.ApprovalFailed(r.error))
                }
            }
            is PosSaleEffect.Park -> launch {
                gateways.sales.park(effect.cartId).onOk { apply(PosSaleEvent.SaleParked) }
            }
            is PosSaleEffect.Resume -> launch {
                gateways.sales.resume(effect.cartId).onOk {
                    attachToTill(it.cartId)
                    apply(PosSaleEvent.CartReplaced(it, PosNotice.SaleResumed))
                }
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
                gateways.sales.convertQuotation(effect.quotationId).onOk {
                    attachToTill(it.cartId)
                    apply(PosSaleEvent.CartReplaced(it, PosNotice.QuoteConverted))
                }
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
            is TillEffect -> runTill(effect)
            is GovernanceEffect -> runGovernance(effect)
            is CheckoutEffect -> runCheckout(effect)
            is SplitEffect -> runSplit(effect)
            is TerminalEffect -> runTerminal(effect)
            is ReturnsEffect -> runReturns(effect)
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
                attachToTill(opened.value.cartId)
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
                if (opened != null) attachToTill(cartId)
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

    private fun runTill(effect: TillEffect) {
        val till = gateways.till
        when (effect) {
            TillEffect.Load -> launch {
                when (val r = till.current()) {
                    is PosResult.Ok -> apply(TillEvent.Loaded(r.value, enforced = till !== TillGateway.None))
                    is PosResult.Err -> apply(TillEvent.Failed(r.error))
                }
            }
            is TillEffect.Open -> launch {
                when (val r = till.open(effect.openingFloat)) {
                    is PosResult.Ok -> {
                        apply(TillEvent.Opened(r.value))
                        // A sale started before the till was open joins it now.
                        state.value.cart.serverCartId?.let { attachToTill(it) }
                    }
                    is PosResult.Err -> apply(TillEvent.Failed(r.error))
                }
            }
            TillEffect.LoadHistory -> launch { till.recent().tillOk { apply(TillEvent.HistoryLoaded(it)) } }
            is TillEffect.LoadReasons -> launch { till.reasons(effect.action).tillOk { apply(TillEvent.ReasonsLoaded(effect.action, it)) } }
            TillEffect.LoadOperators -> launch { till.handoverOperators().tillOk { apply(TillEvent.OperatorsLoaded(it)) } }
            is TillEffect.CashIn -> launch {
                till.cashIn(effect.sessionId, effect.amount, effect.reasonCode, effect.notes).tillOk { apply(TillEvent.CashRecorded) }
            }
            is TillEffect.Close -> launch {
                till.close(effect.sessionId, effect.counts, effect.varianceReasonCode, effect.notes).tillOk { apply(TillEvent.Closed(it)) }
            }
        }
    }

    private fun runGovernance(effect: GovernanceEffect) {
        val g = gateways.governance
        when (effect) {
            is GovernanceEffect.LoadContext -> launch {
                val reasons = (g.reasons(effect.action) as? PosResult.Ok)?.value.orEmpty()
                val needsManager = (g.requiresManager(effect.action, effect.value) as? PosResult.Ok)?.value ?: true
                apply(GovernanceEvent.ContextLoaded(effect.request, reasons, needsManager))
            }
            GovernanceEffect.LoadPolicies -> launch {
                when (val r = g.policies()) {
                    is PosResult.Ok -> apply(GovernanceEvent.PoliciesLoaded(r.value))
                    is PosResult.Err -> apply(GovernanceEvent.Failed(r.error))
                }
            }
            GovernanceEffect.LoadSelf -> launch {
                (g.selfApprover() as? PosResult.Ok)?.let { apply(GovernanceEvent.SelfLoaded(it.value)) }
            }
            is GovernanceEffect.ScanBadge -> launch {
                when (val r = gateways.badgeScanner.scan()) {
                    is PosResult.Ok -> apply(GovernanceEvent.BadgeScanned(r.value, effect.reason, effect.notes))
                    is PosResult.Err -> apply(GovernanceEvent.BadgeScanFailed(r.error))
                }
            }
            is GovernanceEffect.SavePolicy -> launch {
                when (val r = g.setPolicy(effect.policy)) {
                    is PosResult.Ok -> apply(GovernanceEvent.PolicySaved)
                    is PosResult.Err -> apply(GovernanceEvent.Failed(r.error))
                }
            }
        }
    }

    private fun receipt(
        cart: CartProjection,
        invoiceId: String,
        documentNumber: String?,
        tenders: List<TenderLine>,
        cashGiven: Money?,
        customerName: String?,
        vehicleLabel: String?,
        operatorName: String?,
    ): Receipt {
        val change = cashGiven?.let { given ->
            val cash = tenders.filter { it.tender == Tender.Cash }.sumOf { it.amount.minor }
            Money((given.minor - cash).coerceAtLeast(0), given.currency)
        }
        return Receipt(
            invoiceId = invoiceId,
            documentNumber = documentNumber,
            lines = cart.lines,
            subtotal = cart.subtotal,
            discount = cart.discount,
            total = cart.total,
            tenders = tenders,
            cashGiven = cashGiven,
            change = change,
            customerName = customerName,
            vehicleLabel = vehicleLabel,
            operatorName = operatorName,
            issuedAtIso = clock(),
        )
    }

    private fun finish(orderId: String?, receipt: Receipt) {
        apply(CheckoutEvent.Finished(orderId))
        apply(PosSaleEvent.CheckoutDone(receipt))
    }

    private fun runCheckout(effect: CheckoutEffect) {
        val r = gateways.reserve
        // The sale on screen when the effect was issued: the receipt lists exactly what was reserved.
        val cart = state.value.cart
        when (effect) {
            CheckoutEffect.Init -> apply(CheckoutEvent.Enabled(r.enabled))
            is CheckoutEffect.Prepare -> launch {
                // The reservation needs the sale on this operator's open till.
                attachToTill(effect.cartId)
                val requestId = effect.requestId ?: newKey()
                val prepared = r.prepare(effect.cartId, requestId, effect.contacts)
                if (prepared is PosResult.Err) return@launch apply(CheckoutEvent.PrepareFailed(prepared.error))
                val orderId = (prepared as PosResult.Ok).value
                when (val st = r.status(orderId)) {
                    is PosResult.Ok -> apply(CheckoutEvent.Prepared(orderId, effect.cartId, requestId, st.value))
                    is PosResult.Err -> {
                        // Never leave stock held for an order the till cannot show.
                        r.cancel(orderId, "Status unavailable after reservation")
                        apply(CheckoutEvent.PrepareFailed(st.error))
                    }
                }
            }
            CheckoutEffect.LoadProviders -> launch {
                (r.providerAvailability() as? PosResult.Ok)?.let { apply(CheckoutEvent.ProvidersLoaded(it.value)) }
            }
            is CheckoutEffect.Watch -> {
                checkoutJob?.cancel()
                checkoutJob = scope.launch {
                    while (state.value.checkout?.orderId == effect.orderId) {
                        val st = (r.status(effect.orderId) as? PosResult.Ok)?.value
                        if (st != null) {
                            val now = nowMs()
                            val expires = st.reservationExpiresAtIso?.let { runCatching { java.time.OffsetDateTime.parse(it).toInstant().toEpochMilli() }.getOrNull() }
                            apply(CheckoutEvent.Polled(st, now, reservationExpired = expires != null && expires <= now))
                        }
                        delay(checkoutPollMs)
                    }
                }
            }
            is CheckoutEffect.Settle -> launch {
                val key = effect.session.paymentRequestId ?: newKey()
                when (val res = r.settle(effect.session.orderId, key, effect.tenders)) {
                    is PosResult.Ok -> finish(
                        effect.session.orderId,
                        receipt(cart, res.value.invoiceId, res.value.documentNumber, effect.tenders, effect.cashGiven, effect.customerName, effect.vehicleLabel, effect.operatorName),
                    )
                    is PosResult.Err -> apply(CheckoutEvent.SettleFailed(res.error, key, network = res.error is PosError.Transient))
                }
            }
            is CheckoutEffect.StartProvider -> launch {
                when (val res = r.startProvider(effect.session.orderId, effect.provider, effect.msisdn, effect.method)) {
                    is PosResult.Ok -> apply(CheckoutEvent.ProviderStarted(effect.provider, res.value, nowMs()))
                    is PosResult.Err -> apply(CheckoutEvent.ProviderFailed(res.error))
                }
            }
            is CheckoutEffect.FinishProvider -> launch {
                val tender = when (effect.provider) {
                    DigitalProvider.Paynow.rpcValue -> Tender.Paynow
                    DigitalProvider.ContiPay.rpcValue -> Tender.ContiPay
                    else -> Tender.EcoCash
                }
                finish(
                    effect.orderId,
                    receipt(cart, effect.invoiceId, r.documentNumber(effect.invoiceId), listOf(TenderLine(tender, effect.total)), null, effect.customerName, effect.vehicleLabel, effect.operatorName),
                )
            }
            is CheckoutEffect.Cancel -> launch {
                when (val res = r.cancel(effect.orderId, effect.reason)) {
                    is PosResult.Ok -> if (state.value.checkout?.orderId == effect.orderId) apply(CheckoutEvent.Cancelled)
                    is PosResult.Err -> if (state.value.checkout?.orderId == effect.orderId) apply(CheckoutEvent.CancelFailed(res.error))
                }
            }
            is CheckoutEffect.OnAccount -> launch {
                // On account posts the cart directly; release the reservation first so stock is not held twice.
                val reserved = effect.reservedOrderId
                if (reserved != null) {
                    val released = r.cancel(reserved, "Charged to account")
                    if (released is PosResult.Err) return@launch apply(CheckoutEvent.Failed(released.error))
                }
                when (val res = r.onAccount(effect.cartId, effect.contacts)) {
                    is PosResult.Ok -> finish(
                        null,
                        receipt(cart, res.value.invoiceId, res.value.documentNumber, listOf(TenderLine(Tender.OnAccount, cart.total)), null, effect.customerName, effect.vehicleLabel, effect.operatorName),
                    )
                    is PosResult.Err -> {
                        // The reservation is gone: the sale is editable again; say why the account refused.
                        if (effect.reservedOrderId != null) apply(CheckoutEvent.Cancelled)
                        apply(CheckoutEvent.Failed(res.error))
                    }
                }
            }
            CheckoutEffect.LoadRecovery -> launch {
                when (val res = r.recovery()) {
                    is PosResult.Ok -> apply(CheckoutEvent.RecoveryLoaded(res.value))
                    is PosResult.Err -> apply(CheckoutEvent.Failed(res.error))
                }
            }
            is CheckoutEffect.LoadRecoveryStatus -> launch {
                when (val res = r.status(effect.orderId)) {
                    is PosResult.Ok -> apply(CheckoutEvent.RecoveryStatusLoaded(res.value))
                    is PosResult.Err -> apply(CheckoutEvent.Failed(res.error))
                }
            }
            is CheckoutEffect.Release -> launch {
                when (val res = r.cancel(effect.orderId, "Released from payment recovery")) {
                    is PosResult.Ok -> apply(CheckoutEvent.Released(effect.orderId))
                    is PosResult.Err -> apply(CheckoutEvent.Failed(res.error))
                }
            }
            is CheckoutEffect.LoadPickups -> launch {
                when (val res = r.pickups(effect.query)) {
                    is PosResult.Ok -> apply(CheckoutEvent.PickupsLoaded(res.value))
                    is PosResult.Err -> apply(CheckoutEvent.Failed(res.error))
                }
            }
            is CheckoutEffect.Collect -> launch {
                when (val res = r.collect(effect.orderId)) {
                    is PosResult.Ok -> apply(CheckoutEvent.Collected(effect.orderId, effect.newSale))
                    is PosResult.Err -> apply(CheckoutEvent.Failed(res.error))
                }
            }
        }
    }

    private fun runSplit(effect: SplitEffect) {
        val g = gateways.split
        when (effect) {
            is SplitEffect.Find -> launch { (g.find(effect.orderId) as? PosResult.Ok)?.let { apply(SplitEvent.Loaded(it.value)) } }
            is SplitEffect.Start -> launch { splitResult(g.start(effect.orderId)) }
            is SplitEffect.AddPart -> launch {
                val key = effect.requestId ?: newKey()
                when (val r = g.addPart(effect.sessionId, effect.tender, effect.amount, key, effect.reference)) {
                    is PosResult.Ok -> apply(SplitEvent.Loaded(r.value))
                    is PosResult.Err -> apply(SplitEvent.PartFailed(r.error, key, network = r.error is PosError.Transient))
                }
            }
            is SplitEffect.ReduceBasket -> launch {
                when (val r = g.reduceBasket(effect.sessionId, effect.items, effect.notes)) {
                    is PosResult.Ok -> {
                        // The server changed the sale's lines: show (and later print) what was kept.
                        state.value.cart.serverCartId?.let { id -> (g.cart(id) as? PosResult.Ok)?.let { apply(PosEvent.CartUpdated(it.value)) } }
                        apply(SplitEvent.Loaded(r.value))
                    }
                    is PosResult.Err -> apply(SplitEvent.Failed(r.error))
                }
            }
            is SplitEffect.Cancel -> launch {
                when (val r = g.cancel(effect.sessionId, effect.reason, effect.feePolicy)) {
                    is PosResult.Ok -> apply(SplitEvent.Cancelled(r.value))
                    is PosResult.Err -> apply(SplitEvent.Failed(r.error))
                }
            }
            is SplitEffect.Retry -> launch { splitResult(g.retryFinalization(effect.sessionId)) }
            SplitEffect.LoadRecovery -> launch {
                when (val r = g.recovery()) {
                    is PosResult.Ok -> apply(SplitEvent.RecoveryLoaded(r.value))
                    is PosResult.Err -> apply(SplitEvent.Failed(r.error))
                }
            }
            is SplitEffect.LoadRecoverySession -> launch {
                (g.find(effect.orderId) as? PosResult.Ok)?.let { apply(SplitEvent.RecoverySessionLoaded(effect.orderId, it.value)) }
            }
            is SplitEffect.Finish -> {
                val cart = state.value.cart
                launch {
                    val s = effect.session
                    val invoiceId = s.finalInvoiceId ?: return@launch
                    // Each part as applied to the invoice; anything over the total is a refund, not a tender.
                    val tenders = s.legs.mapNotNull { l ->
                        val amount = l.applied ?: l.amount
                        val tender = SplitTender.entries.firstOrNull { it.rpcValue == l.tender }?.receipt ?: return@mapNotNull null
                        TenderLine(tender, amount).takeIf { amount.minor > 0 && l.status in setOf("captured", "allocated", "refund_review", "refund_pending") }
                    }
                    finish(
                        s.orderId,
                        receipt(cart, invoiceId, gateways.reserve.documentNumber(invoiceId), tenders, null, effect.customerName, effect.vehicleLabel, effect.operatorName),
                    )
                    if (s.owedBack.minor > 0) apply(SplitEvent.RefundOwed(s.owedBack))
                }
            }
        }
    }

    /**
     * Card refund of a whole card-machine sale: the approver starts it (badge, password or own sign-in),
     * the machine gives the money back, then an approver posts it (CardRefundFinish).
     */
    private fun runCardRefund(effect: PosSaleEffect.Approve) {
        val request = effect.request as ApprovalRequest.CardRefund
        val t = gateways.terminal
        launch {
            when (val begun = t.beginRefund(request.invoiceId, request.terminalId, request.requestId ?: newKey(), effect.credentials, effect.badge)) {
                is PosResult.Ok -> {
                    apply(PosSaleEvent.Approved(request, null))
                    apply(ReturnsEvent.CardRefundStarted(begun.value))
                    when (val ran = t.run(begun.value)) {
                        is PosResult.Ok -> apply(ReturnsEvent.CardRefundRan(ran.value))
                        // The machine may have paid out even though its answer was not recorded: Unknown, never a retry.
                        is PosResult.Err -> apply(ReturnsEvent.CardRefundRan(begun.value.copy(status = "unknown")))
                    }
                }
                is PosResult.Err -> apply(PosSaleEvent.ApprovalFailed(begun.error))
            }
        }
    }

    private fun runReturns(effect: ReturnsEffect) {
        val r = gateways.returns
        when (effect) {
            is ReturnsEffect.LoadInvoice -> launch {
                when (val res = r.invoice(effect.invoiceId)) {
                    is PosResult.Ok -> apply(ReturnsEvent.Loaded(res.value))
                    is PosResult.Err -> apply(ReturnsEvent.Failed(res.error))
                }
            }
            is ReturnsEffect.Draft -> launch {
                when (val res = r.draft(effect.draft, replacement = effect.draft.resolution == ReturnResolution.Replacement)) {
                    is PosResult.Ok -> apply(ReturnsEvent.Drafted(effect.key, res.value, effect.draft, effect.amount))
                    is PosResult.Err -> apply(ReturnsEvent.Failed(res.error))
                }
            }
            is ReturnsEffect.FindSerial -> launch {
                when (val res = r.findSerial(effect.serial)) {
                    is PosResult.Ok -> apply(ReturnsEvent.SerialChecked(effect.serial, res.value))
                    is PosResult.Err -> apply(ReturnsEvent.Failed(res.error))
                }
            }
            is ReturnsEffect.OpenClaim -> launch {
                when (val res = r.openClaim(effect.invoiceId, effect.invoiceLineId, effect.serialId, effect.notes)) {
                    is PosResult.Ok -> apply(ReturnsEvent.ClaimOpened)
                    is PosResult.Err -> apply(ReturnsEvent.Failed(res.error))
                }
            }
            is ReturnsEffect.LoadClaims -> launch {
                when (val res = r.claims(effect.query, effect.status)) {
                    is PosResult.Ok -> apply(ReturnsEvent.ClaimsLoaded(res.value))
                    is PosResult.Err -> apply(ReturnsEvent.Failed(res.error))
                }
            }
            is ReturnsEffect.CloseClaim -> launch {
                when (val res = r.closeClaim(effect.claimId)) {
                    is PosResult.Ok -> apply(ReturnsEvent.ClaimClosed)
                    is PosResult.Err -> apply(ReturnsEvent.Failed(res.error))
                }
            }
            is ReturnsEffect.LoadReasons -> effect.actions.forEach { action ->
                launch { (gateways.governance.reasons(action) as? PosResult.Ok)?.let { apply(ReturnsEvent.ReasonsLoaded(action, it.value)) } }
            }
            is ReturnsEffect.LoadStock -> launch {
                when (val res = r.stock(effect.stockItemId)) {
                    is PosResult.Ok -> apply(ReturnsEvent.StockLoaded(effect.stockItemId, res.value))
                    is PosResult.Err -> apply(ReturnsEvent.Failed(res.error))
                }
            }
        }
    }

    private fun runTerminal(effect: TerminalEffect) {
        val t = gateways.terminal
        when (effect) {
            TerminalEffect.LoadSetup -> launch {
                // No machine set up is not an error at start-up: the card option just says why.
                val setup = (t.setup() as? PosResult.Ok)?.value ?: co.zw.nissangtr.pos.domain.model.TerminalSetup(emptyList(), null, appInstalled = false, paired = false)
                apply(TerminalEvent.SetupLoaded(setup))
            }
            is TerminalEffect.Select -> launch {
                when (val r = t.select(effect.terminalId)) {
                    is PosResult.Ok -> apply(TerminalEvent.SetupLoaded(r.value))
                    is PosResult.Err -> apply(TerminalEvent.Failed(r.error))
                }
            }
            is TerminalEffect.Pair -> launch {
                when (val r = t.pair(effect.terminalId, effect.admin)) {
                    is PosResult.Ok -> apply(TerminalEvent.Paired(r.value))
                    is PosResult.Err -> apply(TerminalEvent.Failed(r.error))
                }
            }
            is TerminalEffect.Purchase -> launch {
                val key = effect.requestId ?: newKey()
                when (val r = t.beginPurchase(effect.orderId, effect.terminalId, key)) {
                    is PosResult.Ok -> apply(TerminalEvent.Started(r.value))
                    is PosResult.Err -> apply(TerminalEvent.Failed(r.error, key, network = r.error is PosError.Transient))
                }
            }
            is TerminalEffect.SplitPart -> launch {
                val key = effect.requestId ?: newKey()
                when (val r = t.beginSplitPart(effect.sessionId, effect.amount, effect.terminalId, key)) {
                    is PosResult.Ok -> apply(TerminalEvent.Started(r.value))
                    is PosResult.Err -> apply(TerminalEvent.Failed(r.error, key, network = r.error is PosError.Transient))
                }
            }
            is TerminalEffect.Run -> launch {
                when (val r = t.run(effect.attempt, effect.statusOnly)) {
                    is PosResult.Ok -> apply(if (r.value.operation == "reversal") TerminalEvent.Reversed(r.value) else TerminalEvent.Answered(r.value))
                    is PosResult.Err -> {
                        // The machine may have charged even though its answer was not recorded: Unknown, never a retry.
                        apply(TerminalEvent.Failed(r.error))
                        if (effect.attempt.operation != "reversal") {
                            apply(TerminalEvent.Answered(effect.attempt.copy(status = "unknown")))
                        }
                    }
                }
            }
            is TerminalEffect.Finalize -> launch {
                when (val r = t.finalize(effect.attemptId)) {
                    is PosResult.Ok -> apply(TerminalEvent.Finalized(r.value))
                    is PosResult.Err -> {
                        val a = (t.attempt(effect.attemptId) as? PosResult.Ok)?.value
                        if (a != null) apply(TerminalEvent.Finalized(a.copy(finalizationError = (r.error as? PosError.BusinessRule)?.detail)))
                        else apply(TerminalEvent.Failed(r.error))
                    }
                }
            }
            is TerminalEffect.Reverse -> launch {
                when (val begun = t.beginReversal(effect.purchaseAttemptId, newKey())) {
                    is PosResult.Ok -> when (val ran = t.run(begun.value)) {
                        is PosResult.Ok -> apply(TerminalEvent.Reversed(ran.value))
                        is PosResult.Err -> apply(TerminalEvent.Reversed(begun.value.copy(status = "unknown")))
                    }
                    is PosResult.Err -> apply(TerminalEvent.Failed(begun.error))
                }
            }
            is TerminalEffect.LoadAttempt -> launch {
                when (val r = t.attempt(effect.attemptId)) {
                    is PosResult.Ok -> apply(TerminalEvent.AttemptLoaded(r.value))
                    is PosResult.Err -> apply(TerminalEvent.Failed(r.error))
                }
            }
            TerminalEffect.LoadRecovery -> launch {
                (t.recovery() as? PosResult.Ok)?.let { apply(TerminalEvent.RecoveryLoaded(it.value)) }
            }
            is TerminalEffect.Receipt -> {
                val cart = state.value.cart
                launch {
                    val a = effect.attempt
                    val invoiceId = a.invoiceId ?: return@launch
                    finish(
                        a.orderId,
                        receipt(cart, invoiceId, gateways.reserve.documentNumber(invoiceId), listOf(TenderLine(Tender.Bank, a.amount)), null, effect.customerName, effect.vehicleLabel, effect.operatorName),
                    )
                }
            }
        }
    }

    private fun splitResult(r: PosResult<co.zw.nissangtr.pos.domain.model.SplitSession>) = when (r) {
        is PosResult.Ok -> apply(SplitEvent.Loaded(r.value))
        is PosResult.Err -> apply(SplitEvent.Failed(r.error))
    }

    /** Cash from this sale counts towards the open till (checkout needs `cart.till_session_id`). */
    private suspend fun attachToTill(cartId: String) {
        val session = state.value.till.session?.takeIf { state.value.till.isOpen } ?: return
        gateways.till.attachCart(cartId, session.id).onOk { }
    }

    private inline fun <T> PosResult<T>.tillOk(action: (T) -> Unit) {
        when (this) {
            is PosResult.Ok -> action(value)
            is PosResult.Err -> apply(TillEvent.Failed(error))
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
