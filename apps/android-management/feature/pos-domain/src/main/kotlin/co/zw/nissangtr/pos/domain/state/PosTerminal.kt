package co.zw.nissangtr.pos.domain.state

import co.zw.nissangtr.pos.domain.error.PosError
import co.zw.nissangtr.pos.domain.model.ManagerCredentials
import co.zw.nissangtr.pos.domain.model.Money
import co.zw.nissangtr.pos.domain.model.TenderOutcome
import co.zw.nissangtr.pos.domain.model.TerminalAttempt
import co.zw.nissangtr.pos.domain.model.TerminalRecoveryItem
import co.zw.nissangtr.pos.domain.model.TerminalSetup

/*
 * Card machine (ECR, Blueprint §10.7). The reserved order is charged on the acquirer's terminal app;
 * its answer is signed by this paired tablet and recorded by the server, then the sale is posted.
 * Every attempt ends as one outcome. A charge whose answer is lost is Unknown: it blocks a second
 * charge and goes to recovery, where the machine is asked again, the sale is finished, or the
 * charge is reversed on the machine. Never charged twice: the request key is kept until a definite answer.
 */

sealed interface TerminalIntent : PosSaleIntent {
    data object LoadSetup : TerminalIntent
    data class Select(val terminalId: String) : TerminalIntent
    /** An admin pairs this tablet with the selected machine; [admin] null runs as the signed-in user. */
    data class Pair(val admin: ManagerCredentials?) : TerminalIntent
    /** Charge the whole reserved order on the card machine. */
    data object Pay : TerminalIntent
    /** Take one part of a split payment on the card machine. */
    data class PayPart(val amount: Money) : TerminalIntent
    /** Ask the machine again about the attempt whose answer was lost (current sale or recovery). */
    data class CheckOnMachine(val attempt: TerminalAttempt) : TerminalIntent
    /** Approved on the machine but not posted: post the sale against it. */
    data class Finish(val attemptId: String) : TerminalIntent
    /** Approved on the machine but the sale cannot be posted: give the money back on the machine. */
    data class Reverse(val attemptId: String) : TerminalIntent
    data class OpenAttempt(val attemptId: String) : TerminalIntent
}

sealed interface TerminalEvent : PosSaleEvent {
    data class SetupLoaded(val setup: TerminalSetup) : TerminalEvent
    data class Paired(val setup: TerminalSetup) : TerminalEvent
    /** Started on the server; the machine runs next. */
    data class Started(val attempt: TerminalAttempt) : TerminalEvent
    /** The machine's signed answer is recorded. */
    data class Answered(val attempt: TerminalAttempt) : TerminalEvent
    data class Finalized(val attempt: TerminalAttempt) : TerminalEvent
    /** A reversal ran; [purchase] is the purchase it undid (reloaded). */
    data class Reversed(val reversal: TerminalAttempt) : TerminalEvent
    data class AttemptLoaded(val attempt: TerminalAttempt) : TerminalEvent
    /** [requestId] is kept after a dropped answer so starting again returns the same attempt. */
    data class Failed(val error: PosError, val requestId: String? = null, val network: Boolean = false) : TerminalEvent
    data class RecoveryLoaded(val items: List<TerminalRecoveryItem>) : TerminalEvent
}

sealed interface TerminalEffect : PosSaleEffect {
    data object LoadSetup : TerminalEffect
    data class Select(val terminalId: String) : TerminalEffect
    data class Pair(val terminalId: String, val admin: ManagerCredentials?) : TerminalEffect
    /** [requestId] null: the store makes a new key. */
    data class Purchase(val orderId: String, val terminalId: String, val requestId: String?) : TerminalEffect
    data class SplitPart(val sessionId: String, val amount: Money, val terminalId: String, val requestId: String?) : TerminalEffect
    data class Run(val attempt: TerminalAttempt, val statusOnly: Boolean) : TerminalEffect
    data class Finalize(val attemptId: String) : TerminalEffect
    data class Reverse(val purchaseAttemptId: String) : TerminalEffect
    data class LoadAttempt(val attemptId: String) : TerminalEffect
    data object LoadRecovery : TerminalEffect
    /** Posted whole-order card payment: build the receipt from the reserved sale. */
    data class Receipt(val attempt: TerminalAttempt, val customerName: String?, val vehicleLabel: String?, val operatorName: String?) : TerminalEffect
}

private fun refuse(state: PosState, rule: String) = Reduction(state.copy(feedback = PosFeedback.Failure(PosError.BusinessRule(rule, ""))))

/** Why the card machine cannot be used now (null: it can). */
fun PosState.terminalReason(): String? {
    val setup = terminalSetup ?: return "terminal_loading"
    return when {
        !online -> "online_only"
        setup.selected == null -> "terminal_not_set"
        !setup.appInstalled -> "terminal_app_missing"
        !setup.paired -> "terminal_not_paired"
        else -> null
    }
}

internal fun reduceTerminalIntent(state: PosState, intent: TerminalIntent): Reduction {
    val co = state.checkout
    return when (intent) {
        TerminalIntent.LoadSetup -> Reduction(state, listOf(TerminalEffect.LoadSetup))

        is TerminalIntent.Select -> if (!state.online) offlineRefusal(state, "online_only")
        else Reduction(state, listOf(TerminalEffect.Select(intent.terminalId)))

        is TerminalIntent.Pair -> {
            val selected = state.terminalSetup?.selected
            when {
                selected == null -> refuse(state, "terminal_not_set")
                !state.online -> offlineRefusal(state, "online_only")
                state.terminalPairing -> Reduction(state)
                else -> Reduction(state.copy(terminalPairing = true, feedback = null), listOf(TerminalEffect.Pair(selected.id, intent.admin)))
            }
        }

        TerminalIntent.Pay -> {
            val reason = state.terminalReason()
            when {
                co == null || state.split != null || co.busy || co.inFlight || state.terminalBusy ||
                    co.outcome == TenderOutcome.Unknown || co.outcome == TenderOutcome.Approved -> Reduction(state)
                reason != null -> refuse(state, reason)
                else -> Reduction(
                    state.copy(checkout = co.copy(busy = true, outcome = null, message = null), terminalBusy = true, feedback = null),
                    listOf(TerminalEffect.Purchase(co.orderId, state.terminalSetup!!.selected!!.id, state.terminalKey)),
                )
            }
        }

        is TerminalIntent.PayPart -> {
            val sp = state.split
            val reason = state.terminalReason()
            when {
                sp == null || !sp.open || state.splitBusy || state.terminalBusy -> Reduction(state)
                reason != null -> refuse(state, reason)
                intent.amount.minor <= 0 || intent.amount.minor > sp.availableToAllocate.minor ->
                    Reduction(state.copy(feedback = PosFeedback.Failure(PosError.Input("amount", "split_part"))))
                else -> Reduction(
                    state.copy(terminalBusy = true, splitBusy = true, feedback = null),
                    listOf(TerminalEffect.SplitPart(sp.sessionId, intent.amount, state.terminalSetup!!.selected!!.id, state.terminalKey)),
                )
            }
        }

        is TerminalIntent.CheckOnMachine -> when {
            state.terminalBusy -> Reduction(state)
            state.terminalReason() != null -> refuse(state, state.terminalReason()!!)
            else -> Reduction(state.copy(terminalBusy = true, feedback = null), listOf(TerminalEffect.Run(intent.attempt, statusOnly = true)))
        }

        is TerminalIntent.Finish -> if (!state.online) offlineRefusal(state, "online_only")
        else Reduction(state.copy(terminalBusy = true, feedback = null), listOf(TerminalEffect.Finalize(intent.attemptId)))

        is TerminalIntent.Reverse -> when {
            state.terminalBusy -> Reduction(state)
            state.terminalReason() != null -> refuse(state, state.terminalReason()!!)
            else -> Reduction(state.copy(terminalBusy = true, feedback = null), listOf(TerminalEffect.Reverse(intent.attemptId)))
        }

        is TerminalIntent.OpenAttempt -> Reduction(state, listOf(TerminalEffect.LoadAttempt(intent.attemptId)))
    }
}

internal fun reduceTerminalEvent(state: PosState, event: TerminalEvent): Reduction = when (event) {
    is TerminalEvent.SetupLoaded -> Reduction(state.copy(terminalSetup = event.setup))

    is TerminalEvent.Paired -> Reduction(
        state.copy(terminalSetup = event.setup, terminalPairing = false, feedback = PosFeedback.Notice(PosNotice.TerminalPaired)),
    )

    is TerminalEvent.Started -> Reduction(
        state.copy(terminalAttempt = event.attempt, terminalKey = null),
        listOf(TerminalEffect.Run(event.attempt, statusOnly = false)),
    )

    is TerminalEvent.Answered -> answered(state, event.attempt)

    is TerminalEvent.Finalized -> finalized(state, event.attempt)

    is TerminalEvent.Reversed -> Reduction(
        state.copy(
            terminalBusy = false,
            terminalAttempt = state.terminalAttempt?.takeUnless { event.reversal.status == "settled" },
            feedback = if (event.reversal.status == "settled") PosFeedback.Notice(PosNotice.TerminalReversed) else PosFeedback.Failure(PosError.PaymentUnknown(event.reversal.attemptId)),
        ),
        listOfNotNull(TerminalEffect.LoadRecovery, state.recoveryOrderId?.let { CheckoutEffect.LoadRecoveryStatus(it) }),
    )

    is TerminalEvent.AttemptLoaded -> Reduction(
        state.copy(terminalAttempt = if (state.terminalAttempt?.attemptId == event.attempt.attemptId) event.attempt else state.terminalAttempt, recoveryTerminal = event.attempt),
    )

    is TerminalEvent.Failed -> {
        val co = state.checkout
        Reduction(
            state.copy(
                terminalBusy = false,
                terminalPairing = false,
                splitBusy = false,
                terminalKey = if (event.network) event.requestId else null,
                checkout = co?.copy(busy = false),
                feedback = PosFeedback.Failure(event.error),
            ),
        )
    }

    is TerminalEvent.RecoveryLoaded -> Reduction(state.copy(terminalRecovery = event.items))
}

/** The machine's answer, as exactly one outcome (§10.7). */
private fun answered(state: PosState, a: TerminalAttempt): Reduction {
    val inSale = state.terminalAttempt?.attemptId == a.attemptId || state.checkout?.orderId == a.orderId && a.orderId != null
    val co = state.checkout?.takeIf { inSale }
    val forRecovery = state.recoveryTerminal?.attemptId == a.attemptId
    val base = state.copy(
        terminalAttempt = if (inSale) a else state.terminalAttempt,
        recoveryTerminal = if (forRecovery) a else state.recoveryTerminal,
    )
    val reloadSplit = listOfNotNull(a.orderId?.takeIf { a.splitLegId != null }?.let { SplitEffect.Find(it) })
    return when (a.status) {
        // Charged: post the sale (or the part) now; it is never charged again.
        "approved" -> Reduction(base, listOf(TerminalEffect.Finalize(a.attemptId)))
        "settled" -> finalized(base, a)
        "declined", "cancelled", "failed" -> Reduction(
            base.copy(
                terminalBusy = false,
                splitBusy = false,
                terminalAttempt = if (inSale) null else base.terminalAttempt,
                checkout = co?.copy(
                    busy = false,
                    outcome = when (a.status) {
                        "declined" -> TenderOutcome.Declined
                        "cancelled" -> TenderOutcome.Cancelled
                        else -> TenderOutcome.Error
                    },
                    message = a.responseMessage,
                ) ?: base.checkout,
            ),
            reloadSplit + listOfNotNull(TerminalEffect.LoadRecovery.takeIf { forRecovery }),
        )
        // initiated / unknown: no proof either way — block a second charge and resolve from recovery.
        else -> Reduction(
            base.copy(
                terminalBusy = false,
                splitBusy = false,
                checkout = co?.copy(busy = false, outcome = TenderOutcome.Unknown, message = "terminal_unknown") ?: base.checkout,
            ),
            reloadSplit + listOfNotNull(TerminalEffect.LoadRecovery.takeIf { forRecovery }),
        )
    }
}

private fun finalized(state: PosState, a: TerminalAttempt): Reduction {
    val inSale = state.terminalAttempt?.attemptId == a.attemptId
    val co = state.checkout?.takeIf { inSale }
    val recovery = listOfNotNull(TerminalEffect.LoadRecovery, state.recoveryOrderId?.let { CheckoutEffect.LoadRecoveryStatus(it) })
    val base = state.copy(
        terminalAttempt = if (inSale) a else state.terminalAttempt,
        recoveryTerminal = if (state.recoveryTerminal?.attemptId == a.attemptId) a else state.recoveryTerminal,
    )
    return when {
        a.status == "settled" && a.splitLegId != null -> Reduction(
            // The split reducer posts the receipt once the parts cover the sale.
            base.copy(terminalBusy = false, splitBusy = false, terminalAttempt = null),
            listOfNotNull(a.orderId?.let { SplitEffect.Find(it) }) + if (inSale) emptyList() else recovery,
        )
        a.status == "settled" && co != null -> Reduction(
            base.copy(checkout = co.copy(outcome = TenderOutcome.Approved, busy = true)),
            listOf(TerminalEffect.Receipt(a, state.customer?.displayName, state.vehicle?.label, state.operator?.displayName)),
        )
        a.status == "settled" -> Reduction(base.copy(terminalBusy = false, feedback = PosFeedback.Notice(PosNotice.TerminalFinished)), recovery)
        // Charged but not posted: never charge again; finish it later or reverse it on the machine.
        else -> Reduction(
            base.copy(
                terminalBusy = false,
                splitBusy = false,
                checkout = co?.copy(busy = false, outcome = TenderOutcome.Unknown, message = "terminal_unposted") ?: base.checkout,
                feedback = if (co == null) a.finalizationError?.let { PosFeedback.Failure(PosError.BusinessRule("terminal_unposted", it)) } ?: base.feedback else base.feedback,
            ),
            if (inSale) emptyList() else recovery,
        )
    }
}
