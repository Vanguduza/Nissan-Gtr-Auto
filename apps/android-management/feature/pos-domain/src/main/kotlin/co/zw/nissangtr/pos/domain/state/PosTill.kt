package co.zw.nissangtr.pos.domain.state

import co.zw.nissangtr.pos.domain.error.PosError
import co.zw.nissangtr.pos.domain.model.ApprovalRequest
import co.zw.nissangtr.pos.domain.model.CashMovementKind
import co.zw.nissangtr.pos.domain.model.DenominationCount
import co.zw.nissangtr.pos.domain.model.HandoverOperator
import co.zw.nissangtr.pos.domain.model.Money
import co.zw.nissangtr.pos.domain.model.ReasonAction
import co.zw.nissangtr.pos.domain.model.ReasonCode
import co.zw.nissangtr.pos.domain.model.TillCloseResult
import co.zw.nissangtr.pos.domain.model.TillSession
import co.zw.nissangtr.pos.domain.model.TillStatus

/*
 * Till sessions (Blueprint §10.2 Till gateway, delta D-016). A sale needs an open till: the cart is
 * attached to it, so the invoice's cash counts towards the drawer's expected total. Closing is a
 * blind denominated count; the server works out expected cash and the variance, and a variance
 * waits for a manager. Cash leaving the drawer and handovers are manager actions (§10.10: a fresh
 * sign-in per action, never cached).
 */

enum class TillDialog { CashIn, CashOut, Handover, Close }

data class TillPanel(
    /** False when the device has no till backend (previews, tests): selling is not gated. */
    val enforced: Boolean = false,
    val loaded: Boolean = false,
    val session: TillSession? = null,
    val busy: Boolean = false,
    val history: List<TillSession>? = null,
    val reasons: Map<String, List<ReasonCode>> = emptyMap(),
    val operators: List<HandoverOperator>? = null,
    val dialog: TillDialog? = null,
    val closeResult: TillCloseResult? = null,
    /** The count did not match: the server wants a variance reason before it accepts the close. */
    val needsVarianceReason: Boolean = false,
) {
    val isOpen: Boolean get() = session?.status == TillStatus.Open

    /** Online selling is allowed: no till backend, still loading, or an open till. */
    val canSell: Boolean get() = !enforced || !loaded || isOpen
}

sealed interface TillIntent : PosSaleIntent {
    data object Refresh : TillIntent
    data class Open(val openingFloat: Money) : TillIntent
    data class ShowDialog(val dialog: TillDialog?) : TillIntent
    data class CashIn(val amount: Money, val reason: ReasonCode, val notes: String?) : TillIntent
    /** Cash leaving the drawer: asks for a manager first. */
    data class CashOut(val amount: Money, val reason: ReasonCode, val notes: String?) : TillIntent
    data class Handover(val to: HandoverOperator) : TillIntent
    data class Close(val counts: List<DenominationCount>, val varianceReason: ReasonCode?, val notes: String?) : TillIntent
    data class ApproveVariance(val reason: ReasonCode) : TillIntent
    data object DismissCloseResult : TillIntent
}

sealed interface TillEvent : PosSaleEvent {
    data class Loaded(val session: TillSession?, val enforced: Boolean) : TillEvent
    data class Opened(val session: TillSession) : TillEvent
    data class HistoryLoaded(val sessions: List<TillSession>) : TillEvent
    data class ReasonsLoaded(val action: String, val reasons: List<ReasonCode>) : TillEvent
    data class OperatorsLoaded(val operators: List<HandoverOperator>) : TillEvent
    data object CashRecorded : TillEvent
    data class Closed(val result: TillCloseResult) : TillEvent
    data class Failed(val error: PosError) : TillEvent
}

sealed interface TillEffect : PosSaleEffect {
    data object Load : TillEffect
    data class Open(val openingFloat: Money) : TillEffect
    data object LoadHistory : TillEffect
    data class LoadReasons(val action: String) : TillEffect
    data object LoadOperators : TillEffect
    data class CashIn(val sessionId: String, val amount: Money, val reasonCode: String, val notes: String?) : TillEffect
    data class Close(val sessionId: String, val counts: List<DenominationCount>, val varianceReasonCode: String?, val notes: String?) : TillEffect
}

private fun PosState.withTill(transform: TillPanel.() -> TillPanel) = copy(till = till.transform())

private fun tillFailure(state: PosState, error: PosError) =
    Reduction(state.withTill { copy(busy = false) }.copy(feedback = PosFeedback.Failure(error)))

/** Selling is refused until a till is open; the operator is taken to the Till screen. */
internal fun tillRequired(state: PosState): Reduction = Reduction(
    state.copy(
        destination = PosDestination.Till,
        feedback = PosFeedback.Failure(PosError.BusinessRule("till_required", "")),
    ),
    listOf(TillEffect.Load, TillEffect.LoadHistory),
)

internal fun reduceTillIntent(state: PosState, intent: TillIntent): Reduction {
    if (!state.online && intent !is TillIntent.ShowDialog && intent != TillIntent.DismissCloseResult) {
        return offlineRefusal(state, "online_only")
    }
    val session = state.till.session
    return when (intent) {
        TillIntent.Refresh -> Reduction(state, listOf(TillEffect.Load, TillEffect.LoadHistory))

        is TillIntent.Open -> when {
            state.till.busy || state.till.isOpen -> Reduction(state)
            intent.openingFloat.minor < 0 -> Reduction(state.copy(feedback = PosFeedback.Failure(PosError.Input("float", "amount"))))
            else -> Reduction(state.withTill { copy(busy = true) }.copy(feedback = null), listOf(TillEffect.Open(intent.openingFloat)))
        }

        is TillIntent.ShowDialog -> {
            val effects = when (intent.dialog) {
                TillDialog.CashOut -> listOf(TillEffect.LoadReasons(ReasonAction.CASH_OUT))
                TillDialog.Handover -> listOf(TillEffect.LoadOperators)
                TillDialog.Close -> listOf(TillEffect.LoadReasons(ReasonAction.TILL_VARIANCE))
                else -> emptyList()
            }
            if (intent.dialog != null && !state.till.isOpen) Reduction(state)
            else Reduction(
                state.withTill { copy(dialog = intent.dialog, needsVarianceReason = if (intent.dialog == null) false else needsVarianceReason) },
                effects,
            )
        }

        is TillIntent.CashIn -> when {
            session == null || !state.till.isOpen || state.till.busy -> Reduction(state)
            intent.amount.minor <= 0 -> Reduction(state.copy(feedback = PosFeedback.Failure(PosError.Input("amount", "positive"))))
            intent.reason.requiresNotes && intent.notes.isNullOrBlank() ->
                Reduction(state.copy(feedback = PosFeedback.Failure(PosError.Input("notes", "required"))))
            else -> Reduction(
                state.withTill { copy(busy = true) }.copy(feedback = null),
                listOf(TillEffect.CashIn(session.id, intent.amount, intent.reason.code, intent.notes?.trim()?.ifBlank { null })),
            )
        }

        is TillIntent.CashOut -> when {
            session == null || !state.till.isOpen -> Reduction(state)
            intent.amount.minor <= 0 -> Reduction(state.copy(feedback = PosFeedback.Failure(PosError.Input("amount", "positive"))))
            intent.reason.requiresNotes && intent.notes.isNullOrBlank() ->
                Reduction(state.copy(feedback = PosFeedback.Failure(PosError.Input("notes", "required"))))
            else -> Reduction(
                state.withTill { copy(dialog = null) }.copy(
                    approval = ApprovalRequest.CashOut(
                        session.id,
                        CashMovementKind.forCashOutReason(intent.reason.code),
                        intent.amount,
                        intent.reason,
                        intent.notes?.trim()?.ifBlank { null },
                    ),
                ),
            )
        }

        is TillIntent.Handover -> if (session == null || !state.till.isOpen) Reduction(state)
        else Reduction(state.withTill { copy(dialog = null) }.copy(approval = ApprovalRequest.Handover(session.id, intent.to)))

        is TillIntent.Close -> when {
            session == null || !state.till.isOpen || state.till.busy -> Reduction(state)
            intent.counts.isEmpty() -> Reduction(state.copy(feedback = PosFeedback.Failure(PosError.Input("count", "required"))))
            intent.varianceReason?.requiresNotes == true && intent.notes.isNullOrBlank() ->
                Reduction(state.copy(feedback = PosFeedback.Failure(PosError.Input("notes", "required"))))
            else -> Reduction(
                state.withTill { copy(busy = true) }.copy(feedback = null),
                listOf(
                    TillEffect.Close(
                        session.id,
                        // An empty drawer is still a count: send one zero line rather than nothing.
                        intent.counts.filter { it.quantity > 0 }.ifEmpty { intent.counts.take(1).map { it.copy(quantity = 0) } },
                        intent.varianceReason?.code,
                        intent.notes?.trim()?.ifBlank { null },
                    ),
                ),
            )
        }

        is TillIntent.ApproveVariance -> {
            val pending = session?.takeIf { it.status == TillStatus.VariancePending }
            val variance = state.till.closeResult?.variance ?: pending?.variance
            if (pending == null || variance == null) Reduction(state)
            else Reduction(state.copy(approval = ApprovalRequest.TillVariance(pending.id, variance, intent.reason)))
        }

        TillIntent.DismissCloseResult -> Reduction(state.withTill { copy(closeResult = null) })
    }
}

internal fun reduceTillEvent(state: PosState, event: TillEvent): Reduction = when (event) {
    is TillEvent.Loaded -> Reduction(
        state.withTill { copy(enforced = event.enforced, loaded = true, session = event.session, busy = false) },
        // A pending variance needs its reason list for the manager approval.
        if (event.session?.status == TillStatus.VariancePending && state.till.reasons[ReasonAction.TILL_VARIANCE] == null) {
            listOf(TillEffect.LoadReasons(ReasonAction.TILL_VARIANCE))
        } else {
            emptyList()
        },
    )

    is TillEvent.Opened -> Reduction(
        state.withTill { copy(loaded = true, session = event.session, busy = false, closeResult = null) }
            .copy(feedback = PosFeedback.Notice(PosNotice.TillOpened)),
        listOf(TillEffect.LoadHistory),
    )

    is TillEvent.HistoryLoaded -> Reduction(state.withTill { copy(history = event.sessions) })

    is TillEvent.ReasonsLoaded -> Reduction(state.withTill { copy(reasons = reasons + (event.action to event.reasons)) })

    is TillEvent.OperatorsLoaded -> Reduction(state.withTill { copy(operators = event.operators) })

    TillEvent.CashRecorded -> Reduction(
        state.withTill { copy(busy = false, dialog = null) }.copy(feedback = PosFeedback.Notice(PosNotice.CashRecorded)),
        listOf(TillEffect.Load),
    )

    is TillEvent.Closed -> Reduction(
        state.withTill { copy(busy = false, dialog = null, needsVarianceReason = false, closeResult = event.result) }
            .copy(feedback = PosFeedback.Notice(if (event.result.status == TillStatus.Closed) PosNotice.TillClosed else PosNotice.TillVariancePending)),
        listOf(TillEffect.Load, TillEffect.LoadHistory),
    )

    is TillEvent.Failed -> {
        val rule = (event.error as? PosError.BusinessRule)?.rule
        if (rule == "variance_reason_required") {
            // Not an error: the blind count is kept and the dialog asks why it is out.
            Reduction(
                state.withTill { copy(busy = false, needsVarianceReason = true) }.copy(feedback = null),
                if (state.till.reasons[ReasonAction.TILL_VARIANCE] == null) listOf(TillEffect.LoadReasons(ReasonAction.TILL_VARIANCE)) else emptyList(),
            )
        } else {
            tillFailure(state, event.error)
        }
    }
}

/** A till action a manager approved: re-read the till (and its history after a close). */
internal fun tillApproved(state: PosState, request: ApprovalRequest.TillAction): Reduction {
    val notice = when (request) {
        is ApprovalRequest.CashOut -> PosNotice.CashRecorded
        is ApprovalRequest.TillVariance -> PosNotice.TillClosed
        is ApprovalRequest.Handover -> PosNotice.TillHandedOver
    }
    return Reduction(
        state.copy(approval = null, approving = false, feedback = PosFeedback.Notice(notice))
            .withTill { copy(closeResult = if (request is ApprovalRequest.TillVariance) null else closeResult) },
        listOf(TillEffect.Load, TillEffect.LoadHistory),
    )
}
