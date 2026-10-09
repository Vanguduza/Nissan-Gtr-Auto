package co.zw.nissangtr.pos.domain.state

import co.zw.nissangtr.pos.domain.error.PosError
import co.zw.nissangtr.pos.domain.model.CatalogPart
import co.zw.nissangtr.pos.domain.model.sellable
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
    /** An arrived back-order: put its part on the current sale; it is handed over once that sale is paid. */
    data class Sell(val request: FulfillmentRequest) : FulfillmentIntent
}

sealed interface FulfillmentEvent : PosSaleEvent {
    data class Created(val kind: FulfillmentKind) : FulfillmentEvent
    data class Loaded(val requests: List<FulfillmentRequest>) : FulfillmentEvent
    data class Stepped(val step: FulfillmentStep, val kind: FulfillmentKind) : FulfillmentEvent
    data class Failed(val error: PosError) : FulfillmentEvent
    data class PartFound(val request: FulfillmentRequest, val part: CatalogPart?) : FulfillmentEvent
    data class Attached(val request: FulfillmentRequest) : FulfillmentEvent
}

sealed interface FulfillmentEffect : PosSaleEffect {
    data class Create(val draft: FulfillmentDraft) : FulfillmentEffect
    data class Load(val status: String?, val query: String?) : FulfillmentEffect
    data class Step(val requestId: String, val step: FulfillmentStep, val kind: FulfillmentKind) : FulfillmentEffect
    /** Looks the back-ordered part up so it can be sold. */
    data class FindPart(val request: FulfillmentRequest) : FulfillmentEffect
    data class Attach(val request: FulfillmentRequest, val cartId: String) : FulfillmentEffect
}

/** The back-order is tied to the open sale and its part is on it. */
fun PosState.backorderOnSale(request: FulfillmentRequest): Boolean =
    request.cartId != null && request.cartId == cart.serverCartId && cart.lines.any { it.stockItemId == request.stockItemId }

/**
 * After every reduction: once the back-order's part is on the server sale, tie the request to it
 * (`attach_pos_fulfillment_to_cart`); a refused add drops the pending back-order.
 */
internal fun backorderFollowsCart(reduction: Reduction): Reduction {
    val s = reduction.state
    val pending = s.sellingBackorder ?: return reduction
    if (s.feedback is PosFeedback.Failure) return Reduction(s.copy(sellingBackorder = null), reduction.effects)
    val cartId = s.cart.serverCartId ?: return reduction
    if (s.cart.lines.none { it.stockItemId == pending.stockItemId }) return reduction
    return Reduction(s.copy(sellingBackorder = null, fulfillmentBusy = true), reduction.effects + FulfillmentEffect.Attach(pending, cartId))
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

    is FulfillmentIntent.Sell -> when {
        state.fulfillmentBusy || state.sellingBackorder != null || !intent.request.sellable -> Reduction(state)
        else -> Reduction(state.copy(fulfillmentBusy = true, feedback = null), listOf(FulfillmentEffect.FindPart(intent.request)))
    }

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
        is FulfillmentEvent.Failed -> Reduction(state.copy(fulfillmentBusy = false, sellingBackorder = null, feedback = PosFeedback.Failure(event.error)))
        is FulfillmentEvent.PartFound -> when (val part = event.part) {
            null -> failWith(state, PosError.BusinessRule("part_not_sellable", event.request.oemPartNumber))
            // The add goes through the normal sale rules (till open, price, stock); [backorderFollowsCart] ties it after.
            else -> reduce(state.copy(fulfillmentBusy = false, sellingBackorder = event.request), PosIntent.AddPart(part))
        }
        is FulfillmentEvent.Attached -> {
            val tied = state.copy(
                fulfillmentBusy = false,
                fulfillment = state.fulfillment?.map { if (it.id == event.request.id) it.copy(cartId = state.cart.cartId) else it },
                feedback = PosFeedback.Notice(PosNotice.BackorderOnSale),
            )
            // The whole back-ordered quantity goes on the sale.
            val line = state.cart.lines.firstOrNull { it.stockItemId == event.request.stockItemId }
            if (line != null && line.qty < event.request.qty) {
                val r = reduce(tied, PosIntent.SetQuantity(line.lineId, event.request.qty))
                Reduction(r.state.copy(feedback = tied.feedback), r.effects)
            } else {
                Reduction(tied)
            }
        }
    }
}
