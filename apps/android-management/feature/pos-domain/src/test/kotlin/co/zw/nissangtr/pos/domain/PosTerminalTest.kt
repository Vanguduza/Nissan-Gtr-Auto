package co.zw.nissangtr.pos.domain

import co.zw.nissangtr.pos.domain.error.PosError
import co.zw.nissangtr.pos.domain.model.CardTerminal
import co.zw.nissangtr.pos.domain.model.CartLine
import co.zw.nissangtr.pos.domain.model.CartProjection
import co.zw.nissangtr.pos.domain.model.CurrencyCode
import co.zw.nissangtr.pos.domain.model.Money
import co.zw.nissangtr.pos.domain.model.PaymentStatus
import co.zw.nissangtr.pos.domain.model.ReceiptContacts
import co.zw.nissangtr.pos.domain.model.Tender
import co.zw.nissangtr.pos.domain.model.TenderLine
import co.zw.nissangtr.pos.domain.model.TenderOutcome
import co.zw.nissangtr.pos.domain.model.TerminalAttempt
import co.zw.nissangtr.pos.domain.model.TerminalSetup
import co.zw.nissangtr.pos.domain.state.CheckoutEvent
import co.zw.nissangtr.pos.domain.state.CheckoutIntent
import co.zw.nissangtr.pos.domain.state.PosFeedback
import co.zw.nissangtr.pos.domain.state.PosSaleIntent
import co.zw.nissangtr.pos.domain.state.PosState
import co.zw.nissangtr.pos.domain.state.TerminalEffect
import co.zw.nissangtr.pos.domain.state.TerminalEvent
import co.zw.nissangtr.pos.domain.state.TerminalIntent
import co.zw.nissangtr.pos.domain.state.reduce
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PosTerminalTest {
    private fun usd(v: Double) = Money.ofMajor(v, CurrencyCode.USD)
    private val line = CartLine("l1", "si", "OEM-1", "Brake pads", 1.0, usd(100.0), usd(100.0), false, null)
    private val cart = CartProjection("cart-1", CurrencyCode.USD, listOf(line), usd(100.0), usd(0.0), usd(100.0))
    private val status = PaymentStatus("o1", "awaiting_payment", usd(100.0), null, null, null, null, null, null, null, null)
    private val machine = CardTerminal("t1", "Counter card machine", "Bank", mapOf("package_name" to "p", "purchase_action" to "a"))
    private val ready = TerminalSetup(listOf(machine), machine, appInstalled = true, paired = true)

    private fun attempt(st: String, legId: String? = null) =
        TerminalAttempt("a1", "purchase", st, usd(100.0), "Counter card machine", "4242", "VISA", "T1", null, "o1", legId, null, null)

    private fun reserved(setup: TerminalSetup? = ready): PosState {
        val opening = reduce(PosState(cart = cart, reserveCheckout = true, terminalSetup = setup), PosSaleIntent.OpenPayment).state
        return reduce(opening, CheckoutEvent.Prepared("o1", "cart-1", "req-1", status)).state
    }

    @Test
    fun `the card machine needs to be chosen, installed and paired`() {
        fun reason(setup: TerminalSetup) = (reduce(reserved(setup), TerminalIntent.Pay).state.feedback as PosFeedback.Failure).error
        assertEquals(PosError.BusinessRule("terminal_not_set", ""), reason(ready.copy(selected = null)))
        assertEquals(PosError.BusinessRule("terminal_app_missing", ""), reason(ready.copy(appInstalled = false)))
        assertEquals(PosError.BusinessRule("terminal_not_paired", ""), reason(ready.copy(paired = false)))
    }

    @Test
    fun `an approved charge is posted once and finishes with a receipt`() {
        val paying = reduce(reserved(), TerminalIntent.Pay)
        assertEquals(TerminalEffect.Purchase("o1", "t1", null), paying.effects.single())
        assertTrue(paying.state.checkout!!.busy)
        // A second tap while the machine runs does nothing.
        assertTrue(reduce(paying.state, TerminalIntent.Pay).effects.isEmpty())

        val started = reduce(paying.state, TerminalEvent.Started(attempt("initiated")))
        assertEquals(TerminalEffect.Run(attempt("initiated"), statusOnly = false), started.effects.single())
        val approved = reduce(started.state, TerminalEvent.Answered(attempt("approved")))
        assertEquals(TerminalEffect.Finalize("a1"), approved.effects.single())
        val posted = reduce(approved.state, TerminalEvent.Finalized(attempt("settled").copy(invoiceId = "inv-1")))
        assertTrue(posted.effects.single() is TerminalEffect.Receipt)
        assertEquals(TenderOutcome.Approved, posted.state.checkout?.outcome)
    }

    @Test
    fun `a declined card frees the sale for another try`() {
        val s = reduce(reduce(reserved(), TerminalIntent.Pay).state, TerminalEvent.Started(attempt("initiated"))).state
        val declined = reduce(s, TerminalEvent.Answered(attempt("declined").copy(responseMessage = "Insufficient funds"))).state
        assertEquals(TenderOutcome.Declined, declined.checkout?.outcome)
        assertFalse(declined.checkout!!.busy)
        assertNull(declined.terminalAttempt)
        assertEquals(1, reduce(declined, TerminalIntent.Pay).effects.size)
    }

    @Test
    fun `a lost answer is Unknown and blocks every other way to pay`() {
        val s = reduce(reduce(reserved(), TerminalIntent.Pay).state, TerminalEvent.Started(attempt("initiated"))).state
        val unknown = reduce(s, TerminalEvent.Answered(attempt("unknown"))).state
        assertEquals(TenderOutcome.Unknown, unknown.checkout?.outcome)
        assertTrue(reduce(unknown, TerminalIntent.Pay).effects.isEmpty())
        assertTrue(reduce(unknown, CheckoutIntent.PayManual(listOf(TenderLine(Tender.Cash, usd(100.0))), null, ReceiptContacts(null, null))).effects.isEmpty())
        assertTrue(reduce(unknown, PosSaleIntent.ClosePayment).effects.isEmpty())
        // Asking the machine again is the safe next step.
        assertEquals(TerminalEffect.Run(attempt("unknown"), statusOnly = true), reduce(unknown, TerminalIntent.CheckOnMachine(attempt("unknown"))).effects.single())
    }

    @Test
    fun `charged but not posted is Unknown, and can be finished or reversed`() {
        val s = reduce(reduce(reserved(), TerminalIntent.Pay).state, TerminalEvent.Started(attempt("initiated"))).state
        val unposted = reduce(s, TerminalEvent.Finalized(attempt("recovery_required").copy(finalizationError = "insufficient FIFO batch qty"))).state
        assertEquals(TenderOutcome.Unknown, unposted.checkout?.outcome)
        assertEquals("terminal_unposted", unposted.checkout?.message)
        assertEquals(TerminalEffect.Finalize("a1"), reduce(unposted, TerminalIntent.Finish("a1")).effects.single())
        assertEquals(TerminalEffect.Reverse("a1"), reduce(unposted, TerminalIntent.Reverse("a1")).effects.single())
    }

    @Test
    fun `a lost start keeps its key so starting again cannot open a second charge`() {
        val paying = reduce(reserved(), TerminalIntent.Pay).state
        val dropped = reduce(paying, TerminalEvent.Failed(PosError.Transient(true), "k1", network = true)).state
        assertEquals("k1", dropped.terminalKey)
        assertEquals(TerminalEffect.Purchase("o1", "t1", "k1"), reduce(dropped, TerminalIntent.Pay).effects.single())
    }

    @Test
    fun `pairing needs a chosen machine and runs once`() {
        val s = PosState(terminalSetup = ready.copy(paired = false))
        val r = reduce(s, TerminalIntent.Pair(null))
        assertEquals(TerminalEffect.Pair("t1", null), r.effects.single())
        assertTrue(reduce(r.state, TerminalIntent.Pair(null)).effects.isEmpty())
        val done = reduce(r.state, TerminalEvent.Paired(ready)).state
        assertTrue(done.terminalSetup!!.paired)
        assertFalse(done.terminalPairing)
    }
}
