package co.zw.nissangtr.pos.domain.state

import co.zw.nissangtr.pos.domain.error.PosError
import co.zw.nissangtr.pos.domain.model.Money
import co.zw.nissangtr.pos.domain.model.RefundFeePolicy
import co.zw.nissangtr.pos.domain.model.SplitRecoveryItem
import co.zw.nissangtr.pos.domain.model.SplitSession
import co.zw.nissangtr.pos.domain.model.SplitTender
import co.zw.nissangtr.pos.domain.model.TenderOutcome

/*
 * Part payments (staged split, Blueprint §10.5, §10.8, §9.4). On a reserved order the customer pays
 * part by part: each part is its own idempotent step and the remaining balance is always the
 * server's. The sale posts on its own when the parts cover it. When the customer cannot pay the rest,
 * the governed reduced basket keeps only what is paid for — the server works out the legal total.
 * Money received cannot be undone by going back: cancelling turns it into refunds for a manager.
 */

sealed interface SplitIntent : PosSaleIntent {
    data object Start : SplitIntent
    data class AddPart(val tender: SplitTender, val amount: Money, val reference: String?) : SplitIntent
    /** The customer keeps only [items] (cart line id → qty) and confirmed it. */
    data class ReduceBasket(val items: List<Pair<String, Double>>, val customerConfirmed: Boolean, val notes: String?) : SplitIntent
    data class Cancel(val reason: String, val feePolicy: RefundFeePolicy) : SplitIntent
    /** Fully paid but the invoice did not post: post again (current sale or from recovery). */
    data class Retry(val sessionId: String) : SplitIntent
    /** Cancel a part-paid sale from the recovery screen. */
    data class CancelFromRecovery(val sessionId: String, val reason: String, val feePolicy: RefundFeePolicy) : SplitIntent
}

sealed interface SplitEvent : PosSaleEvent {
    data class Loaded(val session: SplitSession?) : SplitEvent
    /** [requestId] is kept after a dropped answer so the retry returns the same part. */
    data class PartFailed(val error: PosError, val requestId: String, val network: Boolean) : SplitEvent
    data class Cancelled(val session: SplitSession) : SplitEvent
    data class RecoveryLoaded(val items: List<SplitRecoveryItem>) : SplitEvent
    data class RecoverySessionLoaded(val orderId: String, val session: SplitSession?) : SplitEvent
    data class Failed(val error: PosError) : SplitEvent
    /** Posted with money owed back (reduced basket surplus): tell the operator a refund is waiting. */
    data class RefundOwed(val amount: Money) : SplitEvent
}

sealed interface SplitEffect : PosSaleEffect {
    data class Find(val orderId: String) : SplitEffect
    data class Start(val orderId: String) : SplitEffect
    /** [requestId] null: the store makes a new key for this part. */
    data class AddPart(val sessionId: String, val tender: SplitTender, val amount: Money, val requestId: String?, val reference: String?) : SplitEffect
    data class ReduceBasket(val sessionId: String, val items: List<Pair<String, Double>>, val notes: String?) : SplitEffect
    data class Cancel(val sessionId: String, val reason: String, val feePolicy: RefundFeePolicy, val fromRecovery: Boolean) : SplitEffect
    data class Retry(val sessionId: String) : SplitEffect
    data object LoadRecovery : SplitEffect
    data class LoadRecoverySession(val orderId: String) : SplitEffect
    /** Posted: build the receipt from the reserved sale and the parts applied to it. */
    data class Finish(val session: SplitSession, val customerName: String?, val vehicleLabel: String?, val operatorName: String?) : SplitEffect
}

private fun failWith(state: PosState, error: PosError) = Reduction(state.copy(feedback = PosFeedback.Failure(error)))

/** A checkout taking part payments only takes parts: no whole-sale tender, no going back with money in. */
internal val PosState.splitActive: Boolean get() = split != null

internal fun reduceSplitIntent(state: PosState, intent: SplitIntent): Reduction {
    val co = state.checkout
    val sp = state.split
    return when (intent) {
        SplitIntent.Start -> when {
            co == null || sp != null || co.busy || co.inFlight || co.outcome == TenderOutcome.Unknown -> Reduction(state)
            !state.online -> offlineRefusal(state, "online_only")
            else -> Reduction(state.copy(splitBusy = true, feedback = null), listOf(SplitEffect.Start(co.orderId)))
        }

        is SplitIntent.AddPart -> when {
            // A card part runs on the card machine (TerminalIntent.PayPart), never as a plain part.
            sp == null || state.splitBusy || !sp.open || intent.tender == SplitTender.CardTerminal -> Reduction(state)
            !state.online -> offlineRefusal(state, "online_only")
            intent.amount.minor <= 0 || intent.amount.minor > sp.availableToAllocate.minor -> failWith(state, PosError.Input("amount", "split_part"))
            intent.tender == SplitTender.Bank && intent.reference.isNullOrBlank() -> failWith(state, PosError.Input("reference", "required"))
            intent.tender == SplitTender.StoreCredit && state.customer == null -> failWith(state, PosError.BusinessRule("account_customer_required", ""))
            else -> Reduction(
                state.copy(splitBusy = true, feedback = null),
                listOf(SplitEffect.AddPart(sp.sessionId, intent.tender, intent.amount, state.splitPartKey, intent.reference?.trim()?.ifEmpty { null })),
            )
        }

        is SplitIntent.ReduceBasket -> when {
            sp == null || state.splitBusy || !sp.open -> Reduction(state)
            !state.online -> offlineRefusal(state, "online_only")
            sp.received.minor <= 0 -> failWith(state, PosError.BusinessRule("split_nothing_received", ""))
            !intent.customerConfirmed -> failWith(state, PosError.Input("confirmation", "required"))
            intent.items.isEmpty() || intent.items.any { it.second <= 0.0 } -> failWith(state, PosError.Input("items", "required"))
            else -> Reduction(
                state.copy(splitBusy = true, feedback = null),
                listOf(SplitEffect.ReduceBasket(sp.sessionId, intent.items, intent.notes?.trim()?.ifEmpty { null })),
            )
        }

        is SplitIntent.Cancel -> when {
            sp == null || state.splitBusy || !sp.cancellable -> Reduction(state)
            intent.reason.isBlank() -> failWith(state, PosError.Input("reason", "required"))
            else -> Reduction(state.copy(splitBusy = true), listOf(SplitEffect.Cancel(sp.sessionId, intent.reason.trim(), intent.feePolicy, fromRecovery = false)))
        }

        is SplitIntent.CancelFromRecovery -> when {
            intent.reason.isBlank() -> failWith(state, PosError.Input("reason", "required"))
            !state.online -> offlineRefusal(state, "online_only")
            else -> Reduction(state, listOf(SplitEffect.Cancel(intent.sessionId, intent.reason.trim(), intent.feePolicy, fromRecovery = true)))
        }

        is SplitIntent.Retry -> if (!state.online) offlineRefusal(state, "online_only")
        else Reduction(state.copy(splitBusy = sp?.sessionId == intent.sessionId || state.splitBusy), listOf(SplitEffect.Retry(intent.sessionId)))
    }
}

internal fun reduceSplitEvent(state: PosState, event: SplitEvent): Reduction = when (event) {
    is SplitEvent.Loaded -> loadedSplit(state.copy(splitBusy = false, splitPartKey = null), event.session)

    is SplitEvent.PartFailed -> Reduction(
        state.copy(splitBusy = false, splitPartKey = if (event.network) event.requestId else null, feedback = PosFeedback.Failure(event.error)),
    )

    is SplitEvent.Cancelled -> {
        val current = state.split?.sessionId == event.session.sessionId
        val next = state.copy(
            split = if (current) null else state.split,
            checkout = if (current) null else state.checkout,
            paymentOpen = if (current) false else state.paymentOpen,
            splitBusy = false,
            splitPartKey = null,
            recoverySplit = if (state.recoverySplit?.sessionId == event.session.sessionId) event.session else state.recoverySplit,
            feedback = PosFeedback.Notice(if (event.session.refunds.isNotEmpty()) PosNotice.SplitCancelledRefund else PosNotice.SplitCancelled),
        )
        Reduction(next, listOf(SplitEffect.LoadRecovery))
    }

    is SplitEvent.RecoveryLoaded -> Reduction(state.copy(splitRecovery = event.items))

    is SplitEvent.RecoverySessionLoaded -> if (event.orderId != state.recoveryOrderId) Reduction(state)
    else Reduction(state.copy(recoverySplit = event.session))

    is SplitEvent.Failed -> Reduction(state.copy(splitBusy = false, feedback = PosFeedback.Failure(event.error)))

    is SplitEvent.RefundOwed -> Reduction(state.copy(feedback = PosFeedback.Notice(PosNotice.SplitRefundOwed)))
}

/** A fresh server view of the part-paid sale: posted → receipt; paid but not posted → Unknown (recovery). */
private fun loadedSplit(state: PosState, session: SplitSession?): Reduction {
    if (session == null) return Reduction(state)
    val forCheckout = state.checkout?.orderId == session.orderId
    val withRecovery = if (state.recoverySplit?.sessionId == session.sessionId) state.copy(recoverySplit = session) else state
    if (!forCheckout) return Reduction(withRecovery)
    val next = withRecovery.copy(split = session)
    return when {
        session.posted -> Reduction(
            next.copy(splitBusy = true, checkout = next.checkout?.copy(outcome = TenderOutcome.Approved, busy = true)),
            listOf(SplitEffect.Finish(session, state.customer?.displayName, state.vehicle?.label, state.operator?.displayName)),
        )
        session.unposted -> Reduction(next.copy(checkout = next.checkout?.copy(outcome = TenderOutcome.Unknown, message = "split_unposted")))
        else -> Reduction(next)
    }
}
