package co.zw.nissangtr.pos.domain

import co.zw.nissangtr.pos.domain.error.PosError
import co.zw.nissangtr.pos.domain.model.CartLine
import co.zw.nissangtr.pos.domain.model.CartProjection
import co.zw.nissangtr.pos.domain.model.CurrencyCode
import co.zw.nissangtr.pos.domain.model.Money
import co.zw.nissangtr.pos.domain.model.PaymentStatus
import co.zw.nissangtr.pos.domain.model.ReceiptContacts
import co.zw.nissangtr.pos.domain.model.RefundFeePolicy
import co.zw.nissangtr.pos.domain.model.SplitLeg
import co.zw.nissangtr.pos.domain.model.SplitRefund
import co.zw.nissangtr.pos.domain.model.SplitSession
import co.zw.nissangtr.pos.domain.model.SplitTender
import co.zw.nissangtr.pos.domain.model.Tender
import co.zw.nissangtr.pos.domain.model.TenderLine
import co.zw.nissangtr.pos.domain.model.TenderOutcome
import co.zw.nissangtr.pos.domain.state.CheckoutEffect
import co.zw.nissangtr.pos.domain.state.CheckoutEvent
import co.zw.nissangtr.pos.domain.state.CheckoutIntent
import co.zw.nissangtr.pos.domain.state.PosFeedback
import co.zw.nissangtr.pos.domain.state.PosNotice
import co.zw.nissangtr.pos.domain.state.PosSaleIntent
import co.zw.nissangtr.pos.domain.state.PosState
import co.zw.nissangtr.pos.domain.state.SplitEffect
import co.zw.nissangtr.pos.domain.state.SplitEvent
import co.zw.nissangtr.pos.domain.state.SplitIntent
import co.zw.nissangtr.pos.domain.state.reduce
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PosSplitTest {
    private fun usd(v: Double) = Money.ofMajor(v, CurrencyCode.USD)
    private val line = CartLine("l1", "si", "OEM-1", "Brake pads", 1.0, usd(100.0), usd(100.0), false, null)
    private val cart = CartProjection("cart-1", CurrencyCode.USD, listOf(line), usd(100.0), usd(0.0), usd(100.0))
    private val status = PaymentStatus("o1", "awaiting_payment", usd(100.0), null, null, null, null, null, null, null, null)

    private fun session(
        received: Double = 0.0,
        status: String = "open",
        invoice: String? = null,
        legs: List<SplitLeg> = emptyList(),
        refunds: List<SplitRefund> = emptyList(),
    ) = SplitSession(
        "s1", "o1", status, usd(100.0), usd(received), usd(0.0), usd(100.0 - received), usd(100.0 - received), invoice, null, legs, refunds,
    )

    private fun reserved(): PosState {
        val opening = reduce(PosState(cart = cart, reserveCheckout = true), PosSaleIntent.OpenPayment).state
        val prepared = reduce(opening, CheckoutEvent.Prepared("o1", "cart-1", "req-1", status))
        assertTrue(SplitEffect.Find("o1") in prepared.effects)
        return prepared.state
    }

    private fun splitting(received: Double = 0.0) =
        reduce(reserved(), SplitEvent.Loaded(session(received, if (received > 0) "partially_captured" else "open"))).state

    @Test
    fun `starting part payments needs a reserved order and asks the server`() {
        val r = reduce(reserved(), SplitIntent.Start)
        assertEquals(SplitEffect.Start("o1"), r.effects.single())
        assertTrue(r.state.splitBusy)
        assertTrue(reduce(PosState(cart = cart), SplitIntent.Start).effects.isEmpty())
    }

    @Test
    fun `a part must fit the server's balance and card parts need a reference`() {
        val s = splitting()
        val tooMuch = reduce(s, SplitIntent.AddPart(SplitTender.Cash, usd(120.0), null))
        assertTrue(tooMuch.effects.isEmpty())
        val noRef = reduce(s, SplitIntent.AddPart(SplitTender.Bank, usd(40.0), " "))
        assertEquals(PosError.Input("reference", "required"), (noRef.state.feedback as PosFeedback.Failure).error)
        val noCustomer = reduce(s, SplitIntent.AddPart(SplitTender.StoreCredit, usd(40.0), null))
        assertTrue(noCustomer.effects.isEmpty())
        val ok = reduce(s, SplitIntent.AddPart(SplitTender.Bank, usd(40.0), " SLIP-1 "))
        assertEquals(SplitEffect.AddPart("s1", SplitTender.Bank, usd(40.0), null, "SLIP-1"), ok.effects.single())
    }

    @Test
    fun `a dropped answer keeps the part key so the retry cannot take it twice`() {
        val busy = reduce(splitting(), SplitIntent.AddPart(SplitTender.Cash, usd(40.0), null)).state
        val dropped = reduce(busy, SplitEvent.PartFailed(PosError.Transient(true), "k1", network = true)).state
        assertEquals("k1", dropped.splitPartKey)
        val retry = reduce(dropped, SplitIntent.AddPart(SplitTender.Cash, usd(40.0), null))
        assertEquals("k1", (retry.effects.single() as SplitEffect.AddPart).requestId)
        val refused = reduce(busy, SplitEvent.PartFailed(PosError.BusinessRule("x", ""), "k1", network = false)).state
        assertNull(refused.splitPartKey)
    }

    @Test
    fun `whole-sale tenders are closed while paying in parts`() {
        val s = splitting(received = 30.0)
        assertTrue(reduce(s, CheckoutIntent.PayManual(listOf(TenderLine(Tender.Cash, usd(100.0))), null, ReceiptContacts(null, null))).effects.isEmpty())
        assertTrue(reduce(s, CheckoutIntent.PayOnAccount(ReceiptContacts(null, null))).effects.isEmpty())
    }

    @Test
    fun `money received blocks going back, nothing received cancels the parts quietly`() {
        assertTrue(reduce(splitting(received = 30.0), PosSaleIntent.ClosePayment).effects.isEmpty())
        val empty = reduce(splitting(), PosSaleIntent.ClosePayment)
        val cancel = empty.effects.single() as SplitEffect.Cancel
        assertEquals(RefundFeePolicy.ManualReview, cancel.feePolicy)
        val done = reduce(empty.state, SplitEvent.Cancelled(session(status = "cancelled"))).state
        assertNull(done.checkout)
        assertNull(done.split)
        assertFalse(done.paymentOpen)
        assertEquals(PosFeedback.Notice(PosNotice.SplitCancelled), done.feedback)
    }

    @Test
    fun `cancelling with money received needs a reason and leaves refunds`() {
        val s = splitting(received = 30.0)
        assertTrue(reduce(s, SplitIntent.Cancel(" ", RefundFeePolicy.ManualReview)).effects.isEmpty())
        val r = reduce(s, SplitIntent.Cancel("Changed mind", RefundFeePolicy.BusinessAbsorbs))
        assertEquals(SplitEffect.Cancel("s1", "Changed mind", RefundFeePolicy.BusinessAbsorbs, fromRecovery = false), r.effects.single())
        val refund = SplitRefund("r1", "leg1", "review", usd(30.0), "business_absorbs", null, null, null, "Changed mind")
        val done = reduce(r.state, SplitEvent.Cancelled(session(30.0, "refund_review", refunds = listOf(refund)))).state
        assertEquals(PosFeedback.Notice(PosNotice.SplitCancelledRefund), done.feedback)
    }

    @Test
    fun `posted part payments finish the sale once`() {
        val s = splitting(received = 30.0)
        val leg = SplitLeg("leg1", 1, "cash", usd(100.0), "allocated", null, null, usd(100.0), null)
        val posted = reduce(s, SplitEvent.Loaded(session(100.0, "settled", invoice = "inv-1", legs = listOf(leg))))
        assertTrue(posted.effects.single() is SplitEffect.Finish)
        assertEquals(TenderOutcome.Approved, posted.state.checkout?.outcome)
    }

    @Test
    fun `paid in full but not posted is Unknown and goes to recovery`() {
        val s = splitting(received = 30.0)
        val unposted = reduce(s, SplitEvent.Loaded(session(100.0, "finalization_failed"))).state
        assertEquals(TenderOutcome.Unknown, unposted.checkout?.outcome)
        val retry = reduce(unposted, SplitIntent.Retry("s1"))
        assertEquals(SplitEffect.Retry("s1"), retry.effects.single())
    }

    @Test
    fun `the reduced basket needs money received and the customer's consent`() {
        val none = reduce(splitting(), SplitIntent.ReduceBasket(listOf("l1" to 1.0), true, null))
        assertTrue(none.effects.isEmpty())
        val s = splitting(received = 30.0)
        assertTrue(reduce(s, SplitIntent.ReduceBasket(listOf("l1" to 1.0), false, null)).effects.isEmpty())
        val ok = reduce(s, SplitIntent.ReduceBasket(listOf("l1" to 1.0), true, " "))
        assertEquals(SplitEffect.ReduceBasket("s1", listOf("l1" to 1.0), null), ok.effects.single())
    }

    @Test
    fun `recovery loads part payments with the order`() {
        val r = reduce(PosState(), CheckoutIntent.OpenRecovery("o1"))
        assertEquals(listOf(CheckoutEffect.LoadRecoveryStatus("o1"), SplitEffect.LoadRecoverySession("o1")), r.effects)
        val all = reduce(PosState(), CheckoutIntent.OpenRecovery(null))
        assertTrue(SplitEffect.LoadRecovery in all.effects)
        val loaded = reduce(r.state, SplitEvent.RecoverySessionLoaded("o1", session(30.0, "partially_captured"))).state
        assertEquals("s1", loaded.recoverySplit?.sessionId)
        // A late answer for another order is ignored.
        assertNull(reduce(r.state, SplitEvent.RecoverySessionLoaded("o2", session())).state.recoverySplit)
    }
}
