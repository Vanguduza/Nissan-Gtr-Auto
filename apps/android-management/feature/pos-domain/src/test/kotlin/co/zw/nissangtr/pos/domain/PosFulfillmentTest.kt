package co.zw.nissangtr.pos.domain

import co.zw.nissangtr.pos.domain.error.PosError
import co.zw.nissangtr.pos.domain.model.CartLine
import co.zw.nissangtr.pos.domain.model.CartProjection
import co.zw.nissangtr.pos.domain.model.CurrencyCode
import co.zw.nissangtr.pos.domain.model.FulfillmentDraft
import co.zw.nissangtr.pos.domain.model.FulfillmentKind
import co.zw.nissangtr.pos.domain.model.FulfillmentRequest
import co.zw.nissangtr.pos.domain.model.FulfillmentStep
import co.zw.nissangtr.pos.domain.model.Money
import co.zw.nissangtr.pos.domain.state.FulfillmentEffect
import co.zw.nissangtr.pos.domain.state.FulfillmentEvent
import co.zw.nissangtr.pos.domain.state.FulfillmentIntent
import co.zw.nissangtr.pos.domain.state.PosFeedback
import co.zw.nissangtr.pos.domain.state.PosNotice
import co.zw.nissangtr.pos.domain.state.PosState
import co.zw.nissangtr.pos.domain.state.reduce
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PosFulfillmentTest {
    private fun usd(v: Double) = Money.ofMajor(v, CurrencyCode.USD)
    private val line = CartLine("l1", "si-1", "OEM-1", "Oil filter", 1.0, usd(12.5), usd(12.5), false, null)
    private val withSale = PosState(cart = CartProjection("cart-1", CurrencyCode.USD, listOf(line), usd(12.5), usd(0.0), usd(12.5)))
    private fun hold(kind: FulfillmentKind = FulfillmentKind.CustomerCollection, item: String = "si-1") =
        FulfillmentDraft(kind, item, 1.0, "wh-main", null, null, null)

    private fun req(kind: FulfillmentKind, status: String, invoice: String? = null) =
        FulfillmentRequest("r", "PFR-1", kind, status, "si-1", "OEM-1", null, 1.0, "A", "B", "cart-1", invoice, null, "")

    @Test fun holdGoesWithTheCurrentSale() {
        val r = reduce(withSale, FulfillmentIntent.Create(hold()))
        val eff = r.effects.single() as FulfillmentEffect.Create
        assertEquals("cart-1", eff.draft.cartId)
    }

    @Test fun holdNeedsThePartInTheSale() {
        val none = reduce(PosState(), FulfillmentIntent.Create(hold()))
        assertEquals("fulfillment_no_sale", ((none.state.feedback as PosFeedback.Failure).error as PosError.BusinessRule).rule)
        val other = reduce(withSale, FulfillmentIntent.Create(hold(item = "si-2")))
        assertEquals("fulfillment_not_in_sale", ((other.state.feedback as PosFeedback.Failure).error as PosError.BusinessRule).rule)
        // A transfer or back-order does not need a sale.
        assertTrue(reduce(PosState(), FulfillmentIntent.Create(FulfillmentDraft(FulfillmentKind.Backorder, "si-2", 1.0, null, "wh-main", null, null))).effects.isNotEmpty())
    }

    @Test fun transferNeedsTwoBranches() {
        val same = reduce(PosState(), FulfillmentIntent.Create(FulfillmentDraft(FulfillmentKind.BranchTransfer, "si-1", 1.0, "wh-main", "wh-main", null, null)))
        assertTrue(same.effects.isEmpty())
    }

    @Test fun stepsFollowTheServerRules() {
        assertEquals(listOf(FulfillmentStep.SendTransfer, FulfillmentStep.Release), req(FulfillmentKind.BranchTransfer, "reserved").steps)
        // A hold is not marked ready by hand: its sale's payment makes it ready.
        assertEquals(listOf(FulfillmentStep.Release), req(FulfillmentKind.CustomerCollection, "reserved").steps)
        // Paid and ready: hand over only, never just release.
        assertEquals(listOf(FulfillmentStep.HandOver), req(FulfillmentKind.CustomerCollection, "ready", invoice = "inv").steps)
        assertEquals(listOf(FulfillmentStep.MarkReady, FulfillmentStep.Release), req(FulfillmentKind.Backorder, "requested").steps)
        assertTrue(req(FulfillmentKind.BranchTransfer, "awaiting_transfer_approval").steps.isEmpty())
    }

    @Test fun createdClosesStockAndReloads() {
        val r = reduce(withSale.copy(fulfillmentBusy = true), FulfillmentEvent.Created(FulfillmentKind.BranchTransfer))
        assertEquals(PosFeedback.Notice(PosNotice.TransferRequested), r.state.feedback)
        assertTrue(r.effects.single() is FulfillmentEffect.Load)
    }
}
