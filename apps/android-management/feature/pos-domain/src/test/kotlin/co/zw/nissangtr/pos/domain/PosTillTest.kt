package co.zw.nissangtr.pos.domain

import co.zw.nissangtr.pos.domain.error.PosError
import co.zw.nissangtr.pos.domain.model.ApprovalRequest
import co.zw.nissangtr.pos.domain.model.CashMovementKind
import co.zw.nissangtr.pos.domain.model.CatalogPart
import co.zw.nissangtr.pos.domain.model.CurrencyCode
import co.zw.nissangtr.pos.domain.model.DenominationCount
import co.zw.nissangtr.pos.domain.model.HandoverOperator
import co.zw.nissangtr.pos.domain.model.Money
import co.zw.nissangtr.pos.domain.model.ReasonCode
import co.zw.nissangtr.pos.domain.model.TillCloseResult
import co.zw.nissangtr.pos.domain.model.TillSession
import co.zw.nissangtr.pos.domain.model.TillStatus
import co.zw.nissangtr.pos.domain.model.denominationsFor
import co.zw.nissangtr.pos.domain.state.PosDestination
import co.zw.nissangtr.pos.domain.state.PosEffect
import co.zw.nissangtr.pos.domain.state.PosFeedback
import co.zw.nissangtr.pos.domain.state.PosIntent
import co.zw.nissangtr.pos.domain.state.PosSaleEvent
import co.zw.nissangtr.pos.domain.state.PosSaleIntent
import co.zw.nissangtr.pos.domain.state.PosState
import co.zw.nissangtr.pos.domain.state.TillDialog
import co.zw.nissangtr.pos.domain.state.TillEffect
import co.zw.nissangtr.pos.domain.state.TillEvent
import co.zw.nissangtr.pos.domain.state.TillIntent
import co.zw.nissangtr.pos.domain.state.TillPanel
import co.zw.nissangtr.pos.domain.state.reduce
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PosTillTest {
    private fun usd(v: Double) = Money.ofMajor(v, CurrencyCode.USD)
    private fun till(status: TillStatus = TillStatus.Open, variance: Money? = null) = TillSession(
        id = "t1", warehouseId = "w1", currency = CurrencyCode.USD, operatorUserId = "u1",
        openingFloat = usd(50.0), status = status, expectedCash = null, countedCash = null,
        variance = variance, varianceReasonCode = null, openedAtIso = "2026-10-03T08:00:00Z", closedAtIso = null,
    )
    private val part = CatalogPart("si-1", "15208-65F0A", "Oil filter", usd(12.5), 4.0, null)
    private val enforcedNoTill = PosState(till = TillPanel(enforced = true, loaded = true))
    private val open = PosState(till = TillPanel(enforced = true, loaded = true, session = till()))
    private val petty = ReasonCode("petty_cash", "Petty cash", false)
    private val short = ReasonCode("count_error", "Count error", false)

    @Test
    fun `selling without an open till goes to the Till screen`() {
        val r = reduce(enforcedNoTill, PosIntent.AddPart(part))
        assertEquals(PosDestination.Till, r.state.destination)
        assertEquals(PosError.BusinessRule("till_required", ""), (r.state.feedback as PosFeedback.Failure).error)
        assertTrue(r.effects.none { it is PosEffect.AddToCart })
        assertTrue(reduce(open, PosIntent.AddPart(part)).effects.single() is PosEffect.AddToCart)
    }

    @Test
    fun `no till backend or still loading never blocks a sale`() {
        assertTrue(reduce(PosState(), PosIntent.AddPart(part)).effects.single() is PosEffect.AddToCart)
        val loading = PosState(till = TillPanel(enforced = true, loaded = false))
        assertTrue(reduce(loading, PosIntent.AddPart(part)).effects.single() is PosEffect.AddToCart)
    }

    @Test
    fun `a till waiting on a variance blocks selling`() {
        val pending = PosState(till = TillPanel(enforced = true, loaded = true, session = till(TillStatus.VariancePending)))
        assertEquals(PosDestination.Till, reduce(pending, PosIntent.AddPart(part)).state.destination)
    }

    @Test
    fun `opening a till sends the float and reloads history`() {
        val r = reduce(enforcedNoTill, TillIntent.Open(usd(50.0)))
        assertTrue(r.state.till.busy)
        assertEquals(TillEffect.Open(usd(50.0)), r.effects.single())
        val opened = reduce(r.state, TillEvent.Opened(till()))
        assertTrue(opened.state.till.isOpen)
        assertTrue(opened.effects.contains(TillEffect.LoadHistory))
    }

    @Test
    fun `cash in goes straight to the server, cash out asks for a manager first`() {
        val cashIn = reduce(open, TillIntent.CashIn(usd(20.0), ReasonCode("float_top_up", "Float top-up", false), null))
        assertEquals(TillEffect.CashIn("t1", usd(20.0), "float_top_up", null), cashIn.effects.single())

        val cashOut = reduce(open.copy(till = open.till.copy(dialog = TillDialog.CashOut)), TillIntent.CashOut(usd(10.0), petty, " milk "))
        assertTrue(cashOut.effects.isEmpty())
        assertNull(cashOut.state.till.dialog)
        val request = cashOut.state.approval as ApprovalRequest.CashOut
        assertEquals(CashMovementKind.PettyCash, request.kind)
        assertEquals("milk", request.notes)
    }

    @Test
    fun `a reason that needs notes is refused without them`() {
        val r = reduce(open, TillIntent.CashOut(usd(10.0), ReasonCode("other", "Other", true), " "))
        assertNull(r.state.approval)
        assertEquals(PosError.Input("notes", "required"), (r.state.feedback as PosFeedback.Failure).error)
    }

    @Test
    fun `manager approval of a till action needs no sale and refreshes the till`() {
        val asked = reduce(open, TillIntent.Handover(HandoverOperator("u2", "E002", "Rudo"))).state
        assertTrue(asked.approval is ApprovalRequest.Handover)
        val approved = reduce(asked.copy(approving = true), PosSaleEvent.Approved(asked.approval!!, null))
        assertNull(approved.state.approval)
        assertTrue(approved.effects.contains(TillEffect.Load))
    }

    @Test
    fun `a blind count that is out asks for a reason, then the variance waits for a manager`() {
        val counts = denominationsFor(CurrencyCode.USD).map { DenominationCount(it, if (it == 5000L) 1 else if (it == 1000L) 2 else 0) }
        val sent = reduce(open, TillIntent.Close(counts, null, null))
        val effect = sent.effects.single() as TillEffect.Close
        assertEquals(listOf(DenominationCount(5000, 1), DenominationCount(1000, 2)), effect.counts)

        val needs = reduce(sent.state, TillEvent.Failed(PosError.BusinessRule("variance_reason_required", "")))
        assertTrue(needs.state.till.needsVarianceReason)
        assertNull(needs.state.feedback)

        val result = TillCloseResult("t1", usd(72.5), usd(70.0), usd(-2.5), TillStatus.VariancePending)
        val closed = reduce(needs.state, TillEvent.Closed(result))
        assertEquals(result, closed.state.till.closeResult)
        val loaded = reduce(closed.state, TillEvent.Loaded(till(TillStatus.VariancePending, usd(-2.5)), enforced = true)).state
        val approval = reduce(loaded, TillIntent.ApproveVariance(short)).state.approval as ApprovalRequest.TillVariance
        assertEquals(usd(-2.5), approval.variance)
    }

    @Test
    fun `an empty drawer is still counted`() {
        val zero = denominationsFor(CurrencyCode.USD).map { DenominationCount(it, 0) }
        val effect = reduce(open, TillIntent.Close(zero, null, null)).effects.single() as TillEffect.Close
        assertEquals(listOf(DenominationCount(10000, 0)), effect.counts)
    }

    @Test
    fun `till actions are refused offline`() {
        val r = reduce(open.copy(online = false), TillIntent.CashIn(usd(5.0), petty, null))
        assertTrue(r.effects.isEmpty())
        assertTrue((r.state.feedback as PosFeedback.Failure).error is PosError.OfflineRestricted)
    }

    @Test
    fun `resuming a parked sale needs an open till too`() {
        val r = reduce(enforcedNoTill, PosSaleIntent.LoadOrders)
        assertTrue(r.effects.isNotEmpty())
        assertEquals(PosDestination.Till, reduce(enforcedNoTill, PosSaleIntent.Resume(co.zw.nissangtr.pos.domain.model.ParkedSale("c1", null, null, usd(1.0), 1))).state.destination)
    }
}
