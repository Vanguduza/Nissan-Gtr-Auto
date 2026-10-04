package co.zw.nissangtr.pos.domain.state

import co.zw.nissangtr.pos.domain.error.PosError
import co.zw.nissangtr.pos.domain.model.FulfillmentDraft
import co.zw.nissangtr.pos.domain.model.FulfillmentKind
import co.zw.nissangtr.pos.domain.model.FulfillmentRequest
import co.zw.nissangtr.pos.domain.model.FulfillmentStep

/*
 * Fulfilment (phase 7): from Stock by branch the counter holds a part here or at another branch, asks
 * for a branch transfer, or back-orders it; Orders → Collections & transfers takes each request to its
 * next step. A hold must go with the current sale (it is paid with it), so it needs the part in the sale.
 */

sealed interface FulfillmentIntent : PosSaleIntent {
    data class Create(val draft: FulfillmentDraft) : FulfillmentIntent
    data class Load(val status: String?, val query: String?) : FulfillmentIntent
    data class Step(val request: FulfillmentRequest, val step: FulfillmentStep) : FulfillmentIntent
}

sealed interface FulfillmentEvent : PosSaleEvent {
    data class Created(val kind: FulfillmentKind) : FulfillmentEvent
    data class Loaded(val requests: List<FulfillmentRequest>) : FulfillmentEvent
    data class Stepped(val step: FulfillmentStep, val kind: FulfillmentKind) : FulfillmentEvent
    data class Failed(val error: PosError) : FulfillmentEvent
}

sealed interface FulfillmentEffect : PosSaleEffect {
    data class Create(val draft: FulfillmentDraft) : FulfillmentEffect
    data class Load(val status: String?, val query: String?) : FulfillmentEffect
    data class Step(val requestId: String, val step: FulfillmentStep, val kind: FulfillmentKind) : FulfillmentEffect
}

/** Why the current sale cannot take a hold of [stockItemId], or null. */
fun PosState.holdBlocked(stockItemId: String): String? = when {
    cart.isEmpty || cart.isLocal -> "fulfillment_no_sale"
    cart.lines.none { it.stockItemId == stockItemId } -> "fulfillment_not_in_sale"
    else -> null
}

private fun failWith(state: PosState, error: PosError) = Reduction(state.copy(fulfillmentBusy = false, feedback = PosFeedback.Failure(error)))

internal fun reduceFulfillmentIntent(state: PosState, intent: FulfillmentIntent): Reduction = when (intent) {
    is FulfillmentIntent.Create -> {
        val d = intent.draft
        val hold = d.kind == FulfillmentKind.CustomerCollection || d.kind == FulfillmentKind.AlternatePickup
        when {
            state.fulfillmentBusy -> Reduction(state)
            d.qty <= 0.0 -> failWith(state, PosError.Input("qty", "positive"))
            hold && state.holdBlocked(d.stockItemId) != null -> failWith(state, PosError.BusinessRule(state.holdBlocked(d.stockItemId)!!, ""))
            d.kind == FulfillmentKind.BranchTransfer && (d.sourceWarehouseId == null || d.destinationWarehouseId == null || d.sourceWarehouseId == d.destinationWarehouseId) ->
                failWith(state, PosError.BusinessRule("fulfillment_branch", ""))
            else -> Reduction(
                state.copy(fulfillmentBusy = true, feedback = null),
                listOf(FulfillmentEffect.Create(if (hold) d.copy(cartId = state.cart.cartId, customerId = state.customer?.id) else d.copy(customerId = state.customer?.id))),
            )
        }
    }

    is FulfillmentIntent.Load -> Reduction(
        state.copy(fulfillmentStatus = intent.status, fulfillmentQuery = intent.query.orEmpty()),
        listOf(FulfillmentEffect.Load(intent.status, intent.query?.trim()?.ifEmpty { null })),
    )

    is FulfillmentIntent.Step -> when {
        state.fulfillmentBusy || intent.step !in intent.request.steps -> Reduction(state)
        else -> Reduction(state.copy(fulfillmentBusy = true, feedback = null), listOf(FulfillmentEffect.Step(intent.request.id, intent.step, intent.request.kind)))
    }
}

internal fun reduceFulfillmentEvent(state: PosState, event: FulfillmentEvent): Reduction {
    val reload = FulfillmentEffect.Load(state.fulfillmentStatus, state.fulfillmentQuery.ifBlank { null })
    return when (event) {
        is FulfillmentEvent.Created -> Reduction(
            state.copy(
                fulfillmentBusy = false,
                stockPart = null,
                branchStock = null,
                feedback = PosFeedback.Notice(
                    when (event.kind) {
                        FulfillmentKind.CustomerCollection -> PosNotice.HeldHere
                        FulfillmentKind.AlternatePickup -> PosNotice.HeldElsewhere
                        FulfillmentKind.BranchTransfer -> PosNotice.TransferRequested
                        FulfillmentKind.Backorder -> PosNotice.Backordered
                    },
                ),
            ),
            listOf(reload),
        )
        is FulfillmentEvent.Loaded -> Reduction(state.copy(fulfillment = event.requests))
        is FulfillmentEvent.Stepped -> Reduction(
            state.copy(
                fulfillmentBusy = false,
                feedback = PosFeedback.Notice(
                    when (event.step) {
                        FulfillmentStep.SendTransfer -> PosNotice.TransferSent
                        FulfillmentStep.MarkReady -> PosNotice.FulfillmentReady
                        FulfillmentStep.HandOver -> if (event.kind == FulfillmentKind.BranchTransfer) PosNotice.TransferReceived else PosNotice.HandedOver
                        FulfillmentStep.Release -> PosNotice.FulfillmentReleased
                    },
                ),
            ),
            listOf(reload),
        )
        is FulfillmentEvent.Failed -> Reduction(state.copy(fulfillmentBusy = false, feedback = PosFeedback.Failure(event.error)))
    }
}
