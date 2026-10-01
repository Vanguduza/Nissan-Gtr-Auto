package co.zw.nissangtr.pos.ui.store

import co.zw.nissangtr.pos.domain.gateway.CartGateway
import co.zw.nissangtr.pos.domain.gateway.CatalogGateway
import co.zw.nissangtr.pos.domain.gateway.FitmentGateway
import co.zw.nissangtr.pos.domain.gateway.PinGateway
import co.zw.nissangtr.pos.domain.gateway.SessionGateway
import co.zw.nissangtr.pos.domain.model.CartProjection
import co.zw.nissangtr.pos.domain.result.PosResult
import co.zw.nissangtr.pos.domain.state.PosEffect
import co.zw.nissangtr.pos.domain.state.PosEvent
import co.zw.nissangtr.pos.domain.state.PosIntent
import co.zw.nissangtr.pos.domain.state.PosMsg
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class PosGateways(
    val session: SessionGateway,
    val catalog: CatalogGateway,
    val fitment: FitmentGateway,
    val cart: CartGateway,
    val pins: PinGateway,
)

/**
 * Single store over the pure reducer (Blueprint §10.3). Screens read [state] and call [dispatch];
 * gateway calls happen only here, and their outcomes come back through the reducer as events.
 */
class PosStore(
    private val scope: CoroutineScope,
    private val gateways: PosGateways,
    initial: PosState = PosState(),
) {
    private val _state = MutableStateFlow(initial)
    val state: StateFlow<PosState> = _state.asStateFlow()

    /** Cart mutations run one at a time so a line never races its own quantity change. */
    private val cartLock = Mutex()
    private var searchJob: Job? = null

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
                    gateways.catalog.search(effect.query, effect.vehicle)
                        .onOk { apply(PosEvent.SearchLoaded(effect.query, it)) }
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
