package co.zw.nissangtr.pos.domain

import co.zw.nissangtr.pos.domain.error.PosError
import co.zw.nissangtr.pos.domain.model.CartLine
import co.zw.nissangtr.pos.domain.model.CartProjection
import co.zw.nissangtr.pos.domain.model.CurrencyCode
import co.zw.nissangtr.pos.domain.model.DigitalProvider
import co.zw.nissangtr.pos.domain.model.Money
import co.zw.nissangtr.pos.domain.model.PaymentStatus
import co.zw.nissangtr.pos.domain.model.ProviderStart
import co.zw.nissangtr.pos.domain.model.ReceiptContacts
import co.zw.nissangtr.pos.domain.model.Tender
import co.zw.nissangtr.pos.domain.model.TenderLine
import co.zw.nissangtr.pos.domain.model.TenderOutcome
import co.zw.nissangtr.pos.domain.state.CheckoutEffect
import co.zw.nissangtr.pos.domain.state.CheckoutEvent
import co.zw.nissangtr.pos.domain.state.CheckoutIntent
import co.zw.nissangtr.pos.domain.state.PROVIDER_WAIT_MS
import co.zw.nissangtr.pos.domain.state.PosDestination
import co.zw.nissangtr.pos.domain.state.PosFeedback
import co.zw.nissangtr.pos.domain.state.PosIntent
import co.zw.nissangtr.pos.domain.state.PosNotice
import co.zw.nissangtr.pos.domain.state.PosSaleIntent
import co.zw.nissangtr.pos.domain.state.PosState
import co.zw.nissangtr.pos.domain.state.reduce
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PosCheckoutTest {
    private fun usd(v: Double) = Money.ofMajor(v, CurrencyCode.USD)
    private val line = CartLine("l1", "si", "OEM-1", "Brake pads", 1.0, usd(80.0), usd(80.0), false, null)
    private val cart = CartProjection("cart-1", CurrencyCode.USD, listOf(line), usd(80.0), usd(0.0), usd(80.0))
    private val selling = PosState(cart = cart, reserveCheckout = true)
    private val none = ReceiptContacts(null, null)

    private fun status(state: String = "awaiting_payment", invoice: String? = null, failure: String? = null) = PaymentStatus(
        orderId = "o1", state = state, total = usd(80.0), reservationExpiresAtIso = null, activeProvider = null,
        providerStatus = null, providerFailure = failure, settledProvider = null, reference = null,
        salesInvoiceId = invoice, paymentException = null,
    )

    private fun reserved(): PosState {
        val opening = reduce(selling, PosSaleIntent.OpenPayment)
        assertEquals(CheckoutEffect.Prepare("cart-1", null, none), opening.effects.single())
        assertTrue(opening.state.reserving)
        val r = reduce(opening.state, CheckoutEvent.Prepared("o1", "cart-1", "req-1", status()))
        assertEquals(listOf(CheckoutEffect.LoadProviders, CheckoutEffect.Watch("o1"), co.zw.nissangtr.pos.domain.state.SplitEffect.Find("o1")), r.effects)
        return r.state
    }

    @Test
    fun `opening payment reserves the sale first, and only when reserve-first is on`() {
        val s = reserved()
        assertTrue(s.paymentOpen)
        assertFalse(s.reserving)
        assertEquals("o1", s.checkout?.orderId)
        // Legacy build: the dialog just opens.
        val legacy = reduce(selling.copy(reserveCheckout = false), PosSaleIntent.OpenPayment)
        assertTrue(legacy.effects.isEmpty())
        assertTrue(legacy.state.paymentOpen)
    }

    @Test
    fun `a reserved sale cannot change until the operator goes back`() {
        val s = reserved()
        for (intent in listOf(PosIntent.RemoveLine("l1"), PosIntent.SetQuantity("l1", 2.0), PosSaleIntent.Park, PosSaleIntent.ClearCustomer)) {
            val r = reduce(s, intent)
            assertTrue(r.effects.isEmpty())
            assertEquals(PosError.BusinessRule("cart_reserved", ""), (r.state.feedback as PosFeedback.Failure).error)
        }
        val back = reduce(s, PosSaleIntent.ClosePayment)
        assertEquals(CheckoutEffect.Cancel("o1", "Operator returned to the sale"), back.effects.single())
        val released = reduce(back.state, CheckoutEvent.Cancelled).state
        assertNull(released.checkout)
        assertFalse(released.paymentOpen)
        assertEquals(1, reduce(released, PosIntent.RemoveLine("l1")).effects.size)
    }

    @Test
    fun `manual tenders must equal the server total and settle in one call`() {
        val s = reserved()
        val short = reduce(s, CheckoutIntent.PayManual(listOf(TenderLine(Tender.Cash, usd(50.0))), null, none))
        assertTrue(short.effects.isEmpty())
        val digital = reduce(s, CheckoutIntent.PayManual(listOf(TenderLine(Tender.EcoCash, usd(80.0))), null, none))
        assertTrue(digital.effects.isEmpty())
        val ok = reduce(s, CheckoutIntent.PayManual(listOf(TenderLine(Tender.Cash, usd(30.0)), TenderLine(Tender.Bank, usd(50.0))), usd(40.0), none))
        val settle = ok.effects.single() as CheckoutEffect.Settle
        assertEquals("o1", settle.session.orderId)
        assertTrue(ok.state.checkout!!.busy)
        // A second tap while it settles does nothing.
        assertTrue(reduce(ok.state, CheckoutIntent.PayManual(settle.tenders, null, none)).effects.isEmpty())
    }

    @Test
    fun `a dropped settle answer keeps its key so the retry cannot charge twice`() {
        val s = reduce(reserved(), CheckoutIntent.PayManual(listOf(TenderLine(Tender.Cash, usd(80.0))), null, none)).state
        val dropped = reduce(s, CheckoutEvent.SettleFailed(PosError.Transient(true), "pay-1", network = true)).state
        assertEquals("pay-1", dropped.checkout?.paymentRequestId)
        assertFalse(dropped.checkout!!.busy)
        val refused = reduce(s, CheckoutEvent.SettleFailed(PosError.BusinessRule("x", ""), "pay-1", network = false)).state
        assertNull(refused.checkout?.paymentRequestId)
    }

    @Test
    fun `an unavailable provider is refused with its reason`() {
        val s = reduce(reserved(), CheckoutEvent.ProvidersLoaded(mapOf(DigitalProvider.EcoCash to null, DigitalProvider.ContiPay to "not_configured"))).state
        val r = reduce(s, CheckoutIntent.PayProvider(DigitalProvider.ContiPay, "0771234567", null, none))
        assertTrue(r.effects.isEmpty())
        assertEquals(PosError.BusinessRule("provider_unavailable", "not_configured"), (r.state.feedback as PosFeedback.Failure).error)
        val eco = reduce(s, CheckoutIntent.PayProvider(DigitalProvider.EcoCash, "0771234567", null, none))
        assertTrue(eco.effects.single() is CheckoutEffect.StartProvider)
    }

    @Test
    fun `a provider attempt ends as exactly one outcome`() {
        val s = reduce(reserved(), CheckoutEvent.ProvidersLoaded(mapOf(DigitalProvider.EcoCash to null))).state
        val started = reduce(
            reduce(s, CheckoutIntent.PayProvider(DigitalProvider.EcoCash, "0771234567", null, none)).state,
            CheckoutEvent.ProviderStarted(DigitalProvider.EcoCash, ProviderStart("i1", null, null), atMs = 1_000),
        ).state
        assertTrue(started.checkout!!.inFlight)
        // Money in flight: going back is refused.
        assertTrue(reduce(started, PosSaleIntent.ClosePayment).effects.isEmpty())

        val declined = reduce(started, CheckoutEvent.Polled(status("payment_failed", failure = "Insufficient funds"), 2_000, false)).state
        assertEquals(TenderOutcome.Declined, declined.checkout?.outcome)
        assertNull(declined.checkout?.attempt)

        val cancelled = reduce(started, CheckoutEvent.Polled(status("payment_failed", failure = "Cancelled by customer"), 2_000, false)).state
        assertEquals(TenderOutcome.Cancelled, cancelled.checkout?.outcome)

        val silent = reduce(started, CheckoutEvent.Polled(status("payment_processing"), 1_000 + PROVIDER_WAIT_MS + 1, false)).state
        assertEquals(TenderOutcome.Unknown, silent.checkout?.outcome)
        // Unknown blocks a second charge and the way back.
        assertTrue(reduce(silent, CheckoutIntent.PayManual(listOf(TenderLine(Tender.Cash, usd(80.0))), null, none)).effects.isEmpty())
        assertTrue(reduce(silent, PosSaleIntent.ClosePayment).effects.isEmpty())

        val paid = reduce(started, CheckoutEvent.Polled(status("paid", invoice = "inv-1"), 2_000, false))
        assertEquals(TenderOutcome.Approved, paid.state.checkout?.outcome)
        assertEquals("inv-1", (paid.effects.single() as CheckoutEffect.FinishProvider).invoiceId)
    }

    @Test
    fun `money captured without a sale goes to recovery`() {
        val s = reserved()
        val captured = reduce(s, CheckoutEvent.Polled(status("allocation_pending"), 1, false)).state
        assertEquals(TenderOutcome.Unknown, captured.checkout?.outcome)
        val recovery = reduce(captured, CheckoutIntent.OpenRecovery("o1"))
        assertEquals(PosDestination.Recovery, recovery.state.destination)
        assertFalse(recovery.state.paymentOpen)
        assertEquals(CheckoutEffect.LoadRecoveryStatus("o1"), recovery.effects.first())
    }

    @Test
    fun `an expired reservation closes payment and says so`() {
        val s = reduce(reserved(), CheckoutEvent.Polled(status(), 1, reservationExpired = true)).state
        assertNull(s.checkout)
        assertFalse(s.paymentOpen)
        assertEquals(PosFeedback.Notice(PosNotice.ReservationExpired), s.feedback)
    }

    @Test
    fun `a late reservation for a closed payment is released`() {
        val opening = reduce(selling, PosSaleIntent.OpenPayment).state
        val closed = reduce(opening, PosSaleIntent.ClosePayment).state
        assertFalse(closed.reserving)
        val late = reduce(closed, CheckoutEvent.Prepared("o1", "cart-1", "req-1", status()))
        assertNull(late.state.checkout)
        assertTrue(late.effects.single() is CheckoutEffect.Cancel)
    }

    @Test
    fun `on account needs a customer`() {
        val r = reduce(reserved(), CheckoutIntent.PayOnAccount(none))
        assertTrue(r.effects.isEmpty())
        assertEquals(PosError.BusinessRule("account_customer_required", ""), (r.state.feedback as PosFeedback.Failure).error)
    }

    @Test
    fun `collecting from the receipt can start the next sale`() {
        val s = reserved().copy(receiptOrderId = "o1")
        val r = reduce(s, CheckoutIntent.Collect("o1", newSale = true))
        assertEquals(CheckoutEffect.Collect("o1", true), r.effects.single())
        val done = reduce(r.state, CheckoutEvent.Collected("o1", newSale = true)).state
        assertNull(done.receiptOrderId)
        assertEquals(PosDestination.Home, done.destination)
    }
}
