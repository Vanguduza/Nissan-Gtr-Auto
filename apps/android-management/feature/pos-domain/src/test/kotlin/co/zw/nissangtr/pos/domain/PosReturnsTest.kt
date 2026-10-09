package co.zw.nissangtr.pos.domain

import co.zw.nissangtr.pos.domain.error.PosError
import co.zw.nissangtr.pos.domain.model.ApprovalRequest
import co.zw.nissangtr.pos.domain.model.CoreResolution
import co.zw.nissangtr.pos.domain.model.CoreReturnInput
import co.zw.nissangtr.pos.domain.model.CurrencyCode
import co.zw.nissangtr.pos.domain.model.InvoiceDetail
import co.zw.nissangtr.pos.domain.model.InvoiceLine
import co.zw.nissangtr.pos.domain.model.InvoiceSummary
import co.zw.nissangtr.pos.domain.model.Money
import co.zw.nissangtr.pos.domain.model.ReturnCondition
import co.zw.nissangtr.pos.domain.model.ReturnDraft
import co.zw.nissangtr.pos.domain.model.ReturnLine
import co.zw.nissangtr.pos.domain.model.ReturnResolution
import co.zw.nissangtr.pos.domain.model.TerminalAttempt
import co.zw.nissangtr.pos.domain.model.WarrantyClaim
import co.zw.nissangtr.pos.domain.model.WarrantyDecision
import co.zw.nissangtr.pos.domain.state.PosFeedback
import co.zw.nissangtr.pos.domain.state.PosNotice
import co.zw.nissangtr.pos.domain.state.PosSaleEvent
import co.zw.nissangtr.pos.domain.state.PosState
import co.zw.nissangtr.pos.domain.state.ReturnsEffect
import co.zw.nissangtr.pos.domain.state.ReturnsEvent
import co.zw.nissangtr.pos.domain.state.ReturnsIntent
import co.zw.nissangtr.pos.domain.state.reduce
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PosReturnsTest {
    private fun usd(v: Double) = Money.ofMajor(v, CurrencyCode.USD)
    private val summary = InvoiceSummary("inv-1", "SINV-1", "Tendai", usd(110.0), null, null)
    private val pads = InvoiceLine("l-pad", "si-pad", "PAD-1", "Pads", "ea", 2.0, usd(45.0), usd(80.0), false, 2.0)
    private val core = InvoiceLine("l-core", "si-pad", "PAD-1", "Core", "ea", 1.0, usd(20.0), usd(20.0), true, 1.0)
    private fun detail(customer: String? = "c1", padsBack: Double = 2.0) =
        InvoiceDetail("inv-1", "SINV-1", customer, usd(100.0), usd(100.0), null, null, listOf(pads.copy(returnableQty = padsBack), core))

    private fun opened(customer: String? = "c1", padsBack: Double = 2.0): PosState {
        val r = reduce(PosState(), ReturnsIntent.Open(summary))
        assertTrue(ReturnsEffect.LoadInvoice("inv-1") in r.effects)
        return reduce(r.state, ReturnsEvent.Loaded(detail(customer, padsBack))).state
    }

    private fun draft(res: ReturnResolution, qty: Double = 1.0) =
        ReturnDraft("inv-1", res, "wrong_part", null, listOf(ReturnLine("l-pad", qty, ReturnCondition.Sealed)), null)

    @Test fun valueIsProRataOfTheDiscountedLine() {
        assertEquals(4000L, pads.valueOf(1.0).minor)
    }

    @Test fun submitDraftsOnceThenAsksApprover() {
        val r = reduce(opened(), ReturnsIntent.Submit(draft(ReturnResolution.CreditNote), usd(40.0)))
        val eff = r.effects.single() as ReturnsEffect.Draft
        val drafted = reduce(r.state, ReturnsEvent.Drafted(eff.key, "rc-1", eff.draft, usd(40.0))).state
        assertEquals("rc-1", (drafted.approval as ApprovalRequest.ReturnPost).caseId)
        // Approval failed and was cancelled: posting again reuses the draft, no second case.
        val again = reduce(drafted.copy(approval = null), ReturnsIntent.Submit(draft(ReturnResolution.CreditNote), usd(40.0)))
        assertTrue(again.effects.none { it is ReturnsEffect.Draft })
        assertEquals("rc-1", (again.state.approval as ApprovalRequest.ReturnPost).caseId)
    }

    @Test fun creditNeedsNamedCustomerAndQtyIsCapped() {
        val walkIn = opened(customer = null)
        val r = reduce(walkIn, ReturnsIntent.Submit(draft(ReturnResolution.StoreCredit), usd(40.0)))
        assertEquals("return_named_customer", ((r.state.feedback as PosFeedback.Failure).error as PosError.BusinessRule).rule)
        val tooMany = reduce(opened(padsBack = 1.0), ReturnsIntent.Submit(draft(ReturnResolution.CashRefund, qty = 2.0), usd(80.0)))
        assertTrue(tooMany.effects.isEmpty())
    }

    @Test fun postedReturnReloadsTheSaleAndClearsTheDraft() {
        val req = ApprovalRequest.ReturnPost("rc-1", "inv-1", "SINV-1", ReturnResolution.CashRefund, usd(40.0))
        val r = reduce(opened().copy(returnDraft = "k" to "rc-1", approval = req), PosSaleEvent.Approved(req, null))
        assertNull(r.state.returnDraft)
        assertEquals(PosFeedback.Notice(PosNotice.ReturnCashOut), r.state.feedback)
        assertTrue(ReturnsEffect.LoadInvoice("inv-1") in r.effects)
    }

    @Test fun coreReturnAsksApproverWithReason() {
        val input = CoreReturnInput("inv-1", "l-core", 1.0, CoreResolution.AccountCredit, "", null, null)
        assertNull(reduce(opened(), ReturnsIntent.Core(input, usd(20.0))).state.approval)
        val ok = reduce(opened(), ReturnsIntent.Core(input.copy(reasonCode = "eligible_core"), usd(20.0))).state
        assertTrue(ok.approval is ApprovalRequest.CoreReturn)
    }

    @Test fun rejectNeedsReason() {
        val claim = WarrantyClaim("wc", "WAR-1", "open", null, "inv-1", "SINV-1", "si-pad", "PAD-1", null, null, null, "", null)
        assertNull(reduce(PosState(), ReturnsIntent.Decide(claim, WarrantyDecision.Reject(" "))).state.approval)
        assertTrue(reduce(PosState(), ReturnsIntent.Decide(claim, WarrantyDecision.TakeBack)).state.approval is ApprovalRequest.WarrantyDecide)
    }

    @Test fun cardRefundApprovedOnMachineAsksToPost() {
        val a = TerminalAttempt("att", "refund", "approved", usd(100.0), "Machine", null, null, "T1", null, null, null, "inv-1", null)
        val r = reduce(opened().copy(cardRefund = a), ReturnsEvent.CardRefundRan(a)).state
        assertNull(r.cardRefund)
        assertEquals("att", (r.approval as ApprovalRequest.CardRefundFinish).attemptId)
        val unknown = reduce(opened(), ReturnsEvent.CardRefundRan(a.copy(status = "unknown"))).state
        assertTrue((unknown.feedback as PosFeedback.Failure).error is PosError.PaymentUnknown)
        assertFalse(opened(padsBack = 1.0).returnInvoice!!.untouched)
    }
}
