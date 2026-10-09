package co.zw.nissangtr.pos.domain.state

import co.zw.nissangtr.pos.domain.error.PosError
import co.zw.nissangtr.pos.domain.model.ApprovalRequest
import co.zw.nissangtr.pos.domain.model.BranchStock
import co.zw.nissangtr.pos.domain.model.CatalogPart
import co.zw.nissangtr.pos.domain.model.CoreReturnInput
import co.zw.nissangtr.pos.domain.model.InvoiceDetail
import co.zw.nissangtr.pos.domain.model.InvoiceSummary
import co.zw.nissangtr.pos.domain.model.Money
import co.zw.nissangtr.pos.domain.model.ReasonCode
import co.zw.nissangtr.pos.domain.model.ReturnDraft
import co.zw.nissangtr.pos.domain.model.ReturnResolution
import co.zw.nissangtr.pos.domain.model.TerminalAttempt
import co.zw.nissangtr.pos.domain.model.WarrantyClaim
import co.zw.nissangtr.pos.domain.model.WarrantyDecision
import co.zw.nissangtr.pos.domain.model.WarrantySerial
import co.zw.nissangtr.pos.domain.model.blockedFor

/*
 * Returns (Blueprint §10, phase 6): open the posted sale, take back what can still come back, and
 * give the customer the right outcome. A sales person drafts the return; an approver posts it through
 * the approval dialog (own sign-in, password for this one call, or badge). A draft that was not posted
 * is reused when posting again, so a retry never drafts twice. Old cores, warranty claims, card-machine
 * refunds of whole card sales and stock by branch live here too.
 */

sealed interface ReturnsIntent : PosSaleIntent {
    data class Open(val invoice: InvoiceSummary) : ReturnsIntent
    data object Close : ReturnsIntent
    /** Draft (or reuse the draft) and ask an approver to post it. [amount] is what the operator was shown. */
    data class Submit(val draft: ReturnDraft, val amount: Money) : ReturnsIntent
    data class Core(val input: CoreReturnInput, val amount: Money) : ReturnsIntent
    data class CheckSerial(val serial: String) : ReturnsIntent
    data class OpenClaim(val invoiceLineId: String, val serialId: String?, val notes: String) : ReturnsIntent
    data class LoadClaims(val status: String?, val query: String?) : ReturnsIntent
    data class Decide(val claim: WarrantyClaim, val decision: WarrantyDecision) : ReturnsIntent
    data class CloseClaim(val claimId: String) : ReturnsIntent
    data class ShowStock(val part: CatalogPart) : ReturnsIntent
    data object HideStock : ReturnsIntent
    /** Give a whole card-machine sale back on this tablet's card machine. */
    data object CardRefund : ReturnsIntent
}

sealed interface ReturnsEvent : PosSaleEvent {
    data class Loaded(val invoice: InvoiceDetail) : ReturnsEvent
    data class Drafted(val key: String, val caseId: String, val draft: ReturnDraft, val amount: Money) : ReturnsEvent
    data class SerialChecked(val serial: String, val matches: List<WarrantySerial>) : ReturnsEvent
    data object ClaimOpened : ReturnsEvent
    data class ClaimsLoaded(val claims: List<WarrantyClaim>) : ReturnsEvent
    data object ClaimClosed : ReturnsEvent
    data class StockLoaded(val stockItemId: String, val rows: List<BranchStock>) : ReturnsEvent
    data class Failed(val error: PosError) : ReturnsEvent
    data class ReasonsLoaded(val action: String, val reasons: List<ReasonCode>) : ReturnsEvent
    /** An approver started the card refund: the machine is running it now. */
    data class CardRefundStarted(val attempt: TerminalAttempt) : ReturnsEvent
    /** The card machine answered a refund ([attempt] status approved, declined, unknown…). */
    data class CardRefundRan(val attempt: TerminalAttempt) : ReturnsEvent
}

sealed interface ReturnsEffect : PosSaleEffect {
    data class LoadInvoice(val invoiceId: String) : ReturnsEffect
    data class Draft(val key: String, val draft: ReturnDraft, val amount: Money) : ReturnsEffect
    data class FindSerial(val serial: String) : ReturnsEffect
    data class OpenClaim(val invoiceId: String, val invoiceLineId: String, val serialId: String?, val notes: String?) : ReturnsEffect
    data class LoadClaims(val status: String?, val query: String?) : ReturnsEffect
    data class CloseClaim(val claimId: String) : ReturnsEffect
    data class LoadStock(val stockItemId: String) : ReturnsEffect
    /** Configured reasons for `return_post` and `core_return`, chosen in the form before the approver. */
    data class LoadReasons(val actions: List<String>) : ReturnsEffect
}

/** Reason lists the returns form chooses from. */
val RETURN_REASON_ACTIONS = listOf("return_post", "core_return")

/** The serial-number check for a warranty claim: null until asked. */
data class SerialLookup(val serial: String, val match: WarrantySerial?)

private fun fail(state: PosState, error: PosError) = Reduction(state.copy(returnsBusy = false, feedback = PosFeedback.Failure(error)))

internal fun reduceReturnsIntent(state: PosState, intent: ReturnsIntent): Reduction {
    val inv = state.returnInvoice
    return when (intent) {
        is ReturnsIntent.Open -> Reduction(
            state.copy(returnSale = intent.invoice, returnInvoice = null, returnDraft = null, serialLookup = null, feedback = null),
            listOfNotNull(
                ReturnsEffect.LoadInvoice(intent.invoice.id),
                ReturnsEffect.LoadReasons(RETURN_REASON_ACTIONS).takeIf { RETURN_REASON_ACTIONS.any { it !in state.returnReasons } },
            ),
        )

        ReturnsIntent.Close -> Reduction(state.copy(returnSale = null, returnInvoice = null, returnDraft = null, serialLookup = null))

        is ReturnsIntent.Submit -> {
            val d = intent.draft
            val lines = inv?.parts.orEmpty()
            when {
                inv == null || inv.id != d.invoiceId || state.returnsBusy -> Reduction(state)
                d.lines.isEmpty() || d.lines.any { it.qty <= 0.0 } -> fail(state, PosError.Input("items", "required"))
                d.lines.any { l -> l.qty > (lines.firstOrNull { it.id == l.invoiceLineId }?.returnableQty ?: 0.0) } -> fail(state, PosError.Input("qty", "returnable"))
                d.reasonCode.isBlank() -> fail(state, PosError.Input("reason", "required"))
                d.resolution.blockedFor(inv, d.lines.size, state.till.canSell) != null ->
                    fail(state, PosError.BusinessRule("return_${d.resolution.blockedFor(inv, d.lines.size, state.till.canSell)}", ""))
                else -> {
                    val key = d.toString()
                    val drafted = state.returnDraft?.takeIf { it.first == key }?.second
                    if (drafted != null) openApproval(state, ApprovalRequest.ReturnPost(drafted, inv.id, inv.documentNumber, d.resolution, intent.amount))
                    else Reduction(state.copy(returnsBusy = true, feedback = null), listOf(ReturnsEffect.Draft(key, d, intent.amount)))
                }
            }
        }

        is ReturnsIntent.Core -> when {
            inv == null || inv.id != intent.input.invoiceId -> Reduction(state)
            intent.input.reasonCode.isBlank() -> fail(state, PosError.Input("reason", "required"))
            intent.input.qty <= 0.0 || intent.input.qty > (inv.cores.firstOrNull { it.id == intent.input.coreLineId }?.returnableQty ?: 0.0) ->
                fail(state, PosError.Input("qty", "returnable"))
            else -> openApproval(state, ApprovalRequest.CoreReturn(intent.input, inv.documentNumber, intent.amount))
        }

        is ReturnsIntent.CheckSerial -> if (intent.serial.isBlank()) Reduction(state.copy(serialLookup = null))
        else Reduction(state, listOf(ReturnsEffect.FindSerial(intent.serial.trim())))

        is ReturnsIntent.OpenClaim -> when {
            inv == null || state.returnsBusy -> Reduction(state)
            intent.notes.isBlank() -> fail(state, PosError.Input("notes", "required"))
            else -> Reduction(
                state.copy(returnsBusy = true, feedback = null),
                listOf(ReturnsEffect.OpenClaim(inv.id, intent.invoiceLineId, intent.serialId, intent.notes.trim())),
            )
        }

        is ReturnsIntent.LoadClaims -> Reduction(
            state.copy(warrantyStatus = intent.status, warrantyQuery = intent.query.orEmpty()),
            listOf(ReturnsEffect.LoadClaims(intent.status, intent.query?.trim()?.ifEmpty { null })),
        )

        is ReturnsIntent.Decide -> when {
            intent.claim.status != "open" -> Reduction(state)
            intent.decision is WarrantyDecision.Reject && intent.decision.reason.isBlank() -> fail(state, PosError.Input("reason", "required"))
            else -> openApproval(state, ApprovalRequest.WarrantyDecide(intent.claim, intent.decision))
        }

        is ReturnsIntent.CloseClaim -> Reduction(state, listOf(ReturnsEffect.CloseClaim(intent.claimId)))

        is ReturnsIntent.ShowStock -> {
            val id = intent.part.stockItemId
            if (id == null) Reduction(state)
            else Reduction(state.copy(stockPart = intent.part, branchStock = null), listOf(ReturnsEffect.LoadStock(id)))
        }

        ReturnsIntent.HideStock -> Reduction(state.copy(stockPart = null, branchStock = null))

        ReturnsIntent.CardRefund -> {
            val machine = state.terminalSetup?.selected
            when {
                inv == null || state.returnsBusy || state.cardRefund != null -> Reduction(state)
                state.terminalReason() != null -> fail(state, PosError.BusinessRule(state.terminalReason() ?: "terminal_unavailable", ""))
                !inv.untouched -> fail(state, PosError.BusinessRule("return_partly_returned", ""))
                machine == null -> fail(state, PosError.BusinessRule("terminal_not_set", ""))
                else -> openApproval(state, ApprovalRequest.CardRefund(inv.id, inv.documentNumber, inv.total, machine.id, null))
            }
        }
    }
}

internal fun reduceReturnsEvent(state: PosState, event: ReturnsEvent): Reduction = when (event) {
    is ReturnsEvent.Loaded -> if (state.returnSale?.id != event.invoice.id) Reduction(state)
    else Reduction(state.copy(returnInvoice = event.invoice, returnsBusy = false))

    is ReturnsEvent.Drafted -> openApproval(
        state.copy(returnsBusy = false, returnDraft = event.key to event.caseId),
        ApprovalRequest.ReturnPost(event.caseId, event.draft.invoiceId, state.returnInvoice?.documentNumber, event.draft.resolution, event.amount),
    )

    is ReturnsEvent.SerialChecked -> Reduction(state.copy(serialLookup = SerialLookup(event.serial, event.matches.firstOrNull())))

    ReturnsEvent.ClaimOpened -> Reduction(
        state.copy(returnsBusy = false, serialLookup = null, feedback = PosFeedback.Notice(PosNotice.ClaimOpened)),
        listOf(ReturnsEffect.LoadClaims(state.warrantyStatus, state.warrantyQuery.ifBlank { null })),
    )

    is ReturnsEvent.ClaimsLoaded -> Reduction(state.copy(warrantyClaims = event.claims))

    ReturnsEvent.ClaimClosed -> Reduction(
        state.copy(feedback = PosFeedback.Notice(PosNotice.ClaimClosed)),
        listOf(ReturnsEffect.LoadClaims(state.warrantyStatus, state.warrantyQuery.ifBlank { null })),
    )

    is ReturnsEvent.StockLoaded -> if (state.stockPart?.stockItemId != event.stockItemId) Reduction(state)
    else Reduction(state.copy(branchStock = event.rows))

    is ReturnsEvent.Failed -> Reduction(state.copy(returnsBusy = false, feedback = PosFeedback.Failure(event.error)))

    is ReturnsEvent.ReasonsLoaded -> Reduction(state.copy(returnReasons = state.returnReasons + (event.action to event.reasons)))

    is ReturnsEvent.CardRefundStarted -> Reduction(state.copy(cardRefund = event.attempt))

    is ReturnsEvent.CardRefundRan -> {
        val a = event.attempt
        val base = state.copy(cardRefund = null)
        when (a.status) {
            // The money went back on the machine: an approver posts the refund now.
            "approved" -> openApproval(base, ApprovalRequest.CardRefundFinish(a.attemptId, a.amount))
            "settled" -> Reduction(base.copy(feedback = PosFeedback.Notice(PosNotice.CardRefunded)), returnsReload(base))
            "declined", "cancelled", "failed" -> Reduction(base.copy(feedback = PosFeedback.Failure(PosError.BusinessRule("card_refund_${a.status}", a.responseMessage ?: ""))))
            // No clear answer: the refund may have gone through. It waits on the recovery screen.
            else -> Reduction(base.copy(feedback = PosFeedback.Failure(PosError.PaymentUnknown(a.attemptId))), listOf(TerminalEffect.LoadRecovery))
        }
    }
}

/** After a return, core or card refund posted: the sale's returnable quantities and the sales list. */
internal fun returnsReload(state: PosState): List<PosEffect> = listOfNotNull(
    state.returnSale?.let { ReturnsEffect.LoadInvoice(it.id) },
    PosSaleEffect.LoadInvoices(state.invoiceQuery.trim()),
)

/** The notice for a posted return: a cash refund tells the operator to pay out from the till. */
internal fun returnPostedNotice(resolution: ReturnResolution): PosNotice = when (resolution) {
    ReturnResolution.CashRefund -> PosNotice.ReturnCashOut
    ReturnResolution.Replacement -> PosNotice.ReturnSwap
    ReturnResolution.Warranty -> PosNotice.ReturnWarranty
    else -> PosNotice.ReturnPosted
}
