package co.zw.nissangtr.pos.domain.state

import co.zw.nissangtr.pos.domain.error.PosError
import co.zw.nissangtr.pos.domain.model.PinKind
import co.zw.nissangtr.pos.domain.model.VehicleCascade
import co.zw.nissangtr.pos.domain.model.VehicleSelection

/**
 * Pure, total reducer (Blueprint §10.3, ARCH-07): same state + message ⇒ same reduction.
 * No I/O here; gateway work is returned as [PosEffect]s.
 */
fun reduce(state: PosState, msg: PosMsg): Reduction = companionFollowsCart(
    when (msg) {
        is PosIntent -> reduceIntent(state, msg)
        is PosEvent -> reduceEvent(state, msg)
    },
)

private fun reduceIntent(state: PosState, intent: PosIntent): Reduction =
    if (state.checkout != null && intent.editsReservedSale()) checkoutLocked(state) else reduceIntentUnlocked(state, intent)

/** Changes a reserved sale cannot take (§10.5): lines, parking and the customer. */
private fun PosIntent.editsReservedSale(): Boolean = this is PosIntent.AddPart || this is PosIntent.SetQuantity ||
    this is PosIntent.RemoveLine || this == PosSaleIntent.Park || this is PosSaleIntent.SelectCustomer ||
    this == PosSaleIntent.ClearCustomer || this is PosSaleIntent.Resume || this is PosSaleIntent.ConvertQuotation

private fun reduceIntentUnlocked(state: PosState, intent: PosIntent): Reduction = when (intent) {
    is PosSaleIntent -> reduceSaleIntent(state, intent)

    PosIntent.Start -> Reduction(
        state,
        // Online: replay anything queued last time and refresh the offline snapshot; offline: just count.
        listOf(
            PosEffect.LoadOperator, PosEffect.LoadModels, PosEffect.LoadPopular, TillEffect.Load, GovernanceEffect.LoadSelf, CheckoutEffect.Init, TerminalEffect.LoadSetup,
            if (state.online) PosSaleEffect.SyncOffline else PosSaleEffect.LoadOfflineStatus,
        ),
    )

    is PosIntent.Navigate -> Reduction(
        state.copy(destination = intent.destination),
        when (intent.destination) {
            PosDestination.Orders -> listOfNotNull(PosSaleEffect.LoadOrders, CheckoutEffect.LoadPickups(null).takeIf { state.reserveCheckout && state.online })
            PosDestination.Returns -> listOf(PosSaleEffect.LoadInvoices(state.invoiceQuery.trim()))
            PosDestination.Till -> if (state.online) listOf(TillEffect.Load, TillEffect.LoadHistory) else emptyList()
            else -> emptyList()
        },
    )

    is PosIntent.EditSearch -> Reduction(state.copy(searchQuery = intent.query))

    PosIntent.SubmitSearch -> search(state, state.searchQuery)

    is PosIntent.SearchFor -> search(state.copy(searchQuery = intent.query), intent.query)

    is PosIntent.OpenCategory -> search(state.copy(searchQuery = intent.category.query), intent.category.query)

    PosIntent.ClearRecentSearches -> Reduction(state.copy(recentSearches = emptyList()))

    is PosIntent.PickModel -> {
        val cascade = state.cascade.copy(
            model = intent.model,
            generation = null,
            engine = null,
            generations = emptyList(),
            engines = emptyList(),
        )
        Reduction(
            state.copy(cascade = cascade, vehicle = null),
            listOf(PosEffect.LoadGenerations(intent.model)) + cartVehicleEffect(state, null),
        )
    }

    is PosIntent.PickGeneration -> {
        val model = state.cascade.model
        if (model == null) {
            Reduction(state)
        } else {
            val cascade = state.cascade.copy(generation = intent.generation, engine = null, engines = emptyList())
            Reduction(
                state.copy(cascade = cascade, vehicle = null),
                listOf(PosEffect.LoadEngines(model, intent.generation)) + cartVehicleEffect(state, null),
            )
        }
    }

    is PosIntent.PickEngine -> {
        val cascade = state.cascade.copy(engine = intent.engine)
        val vehicle = cascade.selection()
        val next = state.copy(cascade = cascade, vehicle = vehicle)
        val effects = cartVehicleEffect(state, vehicle) + researchEffect(next)
        Reduction(next, effects)
    }

    PosIntent.ClearVehicle -> {
        val next = state.copy(cascade = VehicleCascade(models = state.cascade.models), vehicle = null)
        Reduction(next, cartVehicleEffect(state, null) + researchEffect(next))
    }

    is PosIntent.AddPart -> when {
        !state.online || state.cart.isLocal && !state.cart.isEmpty -> if (state.online) {
            // Back online with an unpaid local cart still moving to the server: wait for it.
            Reduction(state)
        } else {
            localAdd(state, intent.part)
        }
        !state.till.canSell -> tillRequired(state)
        !intent.part.canAdd -> Reduction(
            state.copy(
                feedback = PosFeedback.Failure(
                    PosError.BusinessRule(if (intent.part.outOfStock) "out_of_stock" else "part_not_sellable", intent.part.oemPartNumber),
                ),
            ),
        )
        else -> Reduction(
            state.copy(cartBusy = state.cartBusy + 1),
            listOf(PosEffect.AddToCart(state.cart.cartId, intent.part, 1.0)),
        )
    }

    is PosIntent.SetQuantity -> when {
        state.cart.cartId.isEmpty() -> Reduction(state)
        state.cart.isLocal -> localSetQuantity(state, intent.lineId, intent.qty)
        !state.online -> offlineRefusal(state, "server_cart")
        intent.qty <= 0.0 -> Reduction(
            state.copy(cartBusy = state.cartBusy + 1),
            listOf(PosEffect.RemoveLine(state.cart.cartId, intent.lineId)),
        )
        else -> Reduction(
            state.copy(cartBusy = state.cartBusy + 1),
            listOf(PosEffect.SetQuantity(state.cart.cartId, intent.lineId, intent.qty)),
        )
    }

    is PosIntent.RemoveLine -> if (state.cart.cartId.isEmpty()) {
        Reduction(state)
    } else if (state.cart.isLocal) {
        localSetQuantity(state, intent.lineId, 0.0)
    } else if (!state.online) {
        offlineRefusal(state, "server_cart")
    } else {
        Reduction(
            state.copy(cartBusy = state.cartBusy + 1),
            listOf(PosEffect.RemoveLine(state.cart.cartId, intent.lineId)),
        )
    }

    // Pins are applied optimistically and rolled back if the server refuses (§7.4: never swallowed).
    is PosIntent.Pin -> if (state.isPinned(intent.pin)) {
        Reduction(state)
    } else {
        Reduction(state.copy(pins = listOf(intent.pin) + state.pins), listOf(PosEffect.PersistPin(intent.pin)))
    }

    is PosIntent.Unpin -> {
        val index = state.pins.indexOfFirst { it.stableKey == intent.pin.stableKey }
        if (index < 0) {
            Reduction(state)
        } else {
            Reduction(
                state.copy(pins = state.pins.filterIndexed { i, _ -> i != index }),
                listOf(PosEffect.PersistUnpin(state.pins[index], index)),
            )
        }
    }

    is PosIntent.HideBestSeller -> {
        val id = intent.part.stockItemId
        if (id == null || id in state.hiddenBestSellers) {
            Reduction(state)
        } else {
            Reduction(state.copy(hiddenBestSellers = state.hiddenBestSellers + id), listOf(PosEffect.PersistHide(id)))
        }
    }

    is PosIntent.ActivatePin -> when (intent.pin.kind) {
        PinKind.PART, PinKind.CATEGORY, PinKind.SUBCATEGORY, PinKind.MODEL ->
            search(state.copy(searchQuery = intent.pin.searchQuery), intent.pin.searchQuery)
    }

    PosIntent.DismissFeedback -> Reduction(state.copy(feedback = null))

    is PosIntent.ConnectivityChanged -> reduceConnectivity(state, intent.online)
}

private fun reduceEvent(state: PosState, event: PosEvent): Reduction = when (event) {
    is PosSaleEvent -> reduceSaleEvent(state, event)

    is PosEvent.OperatorLoaded -> Reduction(state.copy(operator = event.operator))

    is PosEvent.ModelsLoaded -> Reduction(state.copy(cascade = state.cascade.copy(models = event.models)))

    // Late responses for a level the operator has already changed are dropped.
    is PosEvent.GenerationsLoaded -> if (state.cascade.model != event.model) {
        Reduction(state)
    } else {
        Reduction(state.copy(cascade = state.cascade.copy(generations = event.generations)))
    }

    is PosEvent.EnginesLoaded -> when {
        state.cascade.generation != event.generation -> Reduction(state)
        // Catalogue vehicles listed by chassis only (no engine code): the pick completes here.
        event.engines.isEmpty() -> reduce(state.copy(cascade = state.cascade.copy(engines = emptyList())), PosIntent.PickEngine(""))
        else -> Reduction(state.copy(cascade = state.cascade.copy(engines = event.engines)))
    }

    is PosEvent.SearchLoaded -> if (event.query != state.searchQuery.trim()) {
        Reduction(state)
    } else {
        Reduction(state.copy(searchResults = event.results, searching = false))
    }

    is PosEvent.PinsLoaded -> Reduction(state.copy(pins = event.pins))
    is PosEvent.BestSellersLoaded -> Reduction(state.copy(bestSellers = event.parts))
    is PosEvent.HiddenLoaded -> Reduction(state.copy(hiddenBestSellers = event.stockItemIds))

    is PosEvent.CartUpdated -> Reduction(state.copy(cart = event.cart, currency = event.cart.currency))

    PosEvent.CartMutationFinished -> Reduction(state.copy(cartBusy = (state.cartBusy - 1).coerceAtLeast(0)))

    is PosEvent.PinPersisted -> Reduction(state.copy(feedback = PosFeedback.Notice(PosNotice.Pinned)))
    is PosEvent.UnpinPersisted -> Reduction(state.copy(feedback = PosFeedback.Notice(PosNotice.Unpinned)))
    is PosEvent.HidePersisted -> Reduction(state.copy(feedback = PosFeedback.Notice(PosNotice.BestSellerHidden)))

    is PosEvent.Failed -> {
        val rolledBack = when (val r = event.rollback) {
            null -> state
            is Rollback.RemovePinAdded -> state.copy(pins = state.pins.filterNot { it.stableKey == r.pin.stableKey })
            is Rollback.RestorePinRemoved -> {
                if (state.isPinned(r.pin)) {
                    state
                } else {
                    val at = r.index.coerceIn(0, state.pins.size)
                    state.copy(pins = state.pins.take(at) + r.pin + state.pins.drop(at))
                }
            }
            is Rollback.UnhideBestSeller -> state.copy(hiddenBestSellers = state.hiddenBestSellers - r.stockItemId)
        }
        Reduction(
            rolledBack.copy(
                searching = false,
                customerSearching = false,
                epc = rolledBack.epc.copy(loading = false),
                feedback = PosFeedback.Failure(event.error),
            ),
        )
    }
}

private fun search(state: PosState, raw: String): Reduction {
    val query = raw.trim()
    if (query.isEmpty() && state.vehicle == null) {
        return Reduction(state.copy(searchResults = null, searching = false))
    }
    val recent = if (query.isEmpty()) {
        state.recentSearches
    } else {
        (listOf(query) + state.recentSearches.filterNot { it.equals(query, ignoreCase = true) })
            .take(PosState.RECENT_SEARCH_LIMIT)
    }
    return Reduction(
        state.copy(
            destination = PosDestination.SearchSpares,
            searching = true,
            recentSearches = recent,
        ),
        listOf(PosEffect.Search(query, state.vehicle)),
    )
}

/** Re-run the visible search when the fitment context changes, so results always match the chip. */
private fun researchEffect(state: PosState): List<PosEffect> =
    if (state.destination == PosDestination.SearchSpares && (state.searchQuery.isNotBlank() || state.vehicle != null)) {
        listOf(PosEffect.Search(state.searchQuery.trim(), state.vehicle))
    } else {
        emptyList()
    }

private fun cartVehicleEffect(state: PosState, vehicle: VehicleSelection?): List<PosEffect> =
    if (state.cart.serverCartId != null && state.vehicle != vehicle) {
        listOf(PosEffect.SetCartVehicle(state.cart.serverCartId!!, vehicle))
    } else {
        emptyList()
    }
