package co.zw.nissangtr.pos.domain

import co.zw.nissangtr.pos.domain.error.PosError
import co.zw.nissangtr.pos.domain.model.CartLine
import co.zw.nissangtr.pos.domain.model.CartProjection
import co.zw.nissangtr.pos.domain.model.CatalogPart
import co.zw.nissangtr.pos.domain.model.CurrencyCode
import co.zw.nissangtr.pos.domain.model.FulfillmentDraft
import co.zw.nissangtr.pos.domain.model.FulfillmentKind
import co.zw.nissangtr.pos.domain.model.FulfillmentRequest
import co.zw.nissangtr.pos.domain.model.FulfillmentStep
import co.zw.nissangtr.pos.domain.model.Money
import co.zw.nissangtr.pos.domain.model.sellable
import co.zw.nissangtr.pos.domain.state.FulfillmentEffect
import co.zw.nissangtr.pos.domain.state.FulfillmentEvent
import co.zw.nissangtr.pos.domain.state.FulfillmentIntent
import co.zw.nissangtr.pos.domain.state.PosFeedback
import co.zw.nissangtr.pos.domain.state.PosNotice
import co.zw.nissangtr.pos.domain.state.PosEffect
import co.zw.nissangtr.pos.domain.state.PosEvent
import co.zw.nissangtr.pos.domain.state.PosState
import co.zw.nissangtr.pos.domain.state.backorderOnSale
import co.zw.nissangtr.pos.domain.state.reduce
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    @Test fun transferForACustomerIsSoldBeforeHandOver() {
        val forCustomer = req(FulfillmentKind.BranchTransfer, "ready").copy(customerId = "cust-1")
        assertTrue(forCustomer.sellable)
        assertFalse(FulfillmentStep.HandOver in forCustomer.steps)
        val paid = forCustomer.copy(invoiceId = "inv-1")
        assertFalse(paid.sellable)
        assertTrue(FulfillmentStep.HandOver in paid.steps)
        // A stock move between branches (no customer) is just received.
        val restock = req(FulfillmentKind.BranchTransfer, "ready")
        assertFalse(restock.sellable)
        assertTrue(FulfillmentStep.HandOver in restock.steps)
    }

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

    // --- An arrived back-order sold on the current sale, then handed over

    private val arrived = req(FulfillmentKind.Backorder, "ready").copy(cartId = null, qty = 3.0)

    @Test fun arrivedBackorderIsSoldThenHandedOver() {
        // Not handed over before its sale has posted; once it has, hand over (never just release).
        assertEquals(listOf(FulfillmentStep.Release), arrived.steps)
        assertEquals(listOf(FulfillmentStep.HandOver), arrived.copy(invoiceId = "inv").steps)

        // A sale with something else on it: the back-ordered part is not there yet.
        val other = CartLine("l9", "si-9", "OEM-9", "Wiper", 1.0, usd(5.0), usd(5.0), false, null)
        val sale = PosState(cart = CartProjection("cart-1", CurrencyCode.USD, listOf(other), usd(5.0), usd(0.0), usd(5.0)))
        val find = reduce(sale, FulfillmentIntent.Sell(arrived))
        assertEquals(FulfillmentEffect.FindPart(arrived), find.effects.single())

        // The part goes through the normal add; the request waits for it to be on the server sale.
        val part = CatalogPart("si-1", "OEM-1", "Oil filter", usd(12.5), 5.0, null)
        val found = reduce(find.state, FulfillmentEvent.PartFound(arrived, part))
        assertEquals(arrived, found.state.sellingBackorder)
        assertTrue(found.effects.any { it is PosEffect.AddToCart })

        assertTrue(found.effects.none { it is FulfillmentEffect.Attach })
        // The part is on the server sale now: the request is tied to it.
        val tie = reduce(found.state, PosEvent.CartUpdated(sale.cart.copy(lines = listOf(other, line))))
        assertNull(tie.state.sellingBackorder)
        assertEquals(FulfillmentEffect.Attach(arrived, "cart-1"), tie.effects.single { it is FulfillmentEffect.Attach })

        // Tied: the whole back-ordered quantity goes on the sale, and the row says so.
        val attached = reduce(tie.state.copy(fulfillment = listOf(arrived)), FulfillmentEvent.Attached(arrived))
        assertEquals(PosFeedback.Notice(PosNotice.BackorderOnSale), attached.state.feedback)
        assertTrue(attached.effects.any { it is PosEffect.SetQuantity && it.qty == 3.0 })
        assertTrue(attached.state.backorderOnSale(attached.state.fulfillment!!.single()))
    }

    @Test fun backorderNotYetArrivedOrAlreadySoldIsNotOffered() {
        assertTrue(reduce(withSale, FulfillmentIntent.Sell(req(FulfillmentKind.Backorder, "requested"))).effects.isEmpty())
        assertTrue(reduce(withSale, FulfillmentIntent.Sell(arrived.copy(invoiceId = "inv"))).effects.isEmpty())
        assertTrue(reduce(withSale, FulfillmentIntent.Sell(req(FulfillmentKind.CustomerCollection, "ready"))).effects.isEmpty())
    }

    @Test fun partThatCannotBeFoundOrSoldDropsTheBackorder() {
        val missing = reduce(withSale.copy(fulfillmentBusy = true), FulfillmentEvent.PartFound(arrived, null))
        assertTrue(missing.state.feedback is PosFeedback.Failure)
        assertFalse(missing.state.fulfillmentBusy)
        val unpriced = reduce(withSale, FulfillmentEvent.PartFound(arrived, CatalogPart("si-1", "OEM-1", "Oil filter", null, 5.0, null)))
        assertNull("a refused add never ties the request", unpriced.state.sellingBackorder)
        assertTrue(unpriced.effects.none { it is FulfillmentEffect.Attach })
    }

    @Test fun offlineCannotSellABackorder() {
        val r = reduce(withSale.copy(online = false), FulfillmentIntent.Sell(arrived))
        assertTrue(r.effects.isEmpty())
        assertTrue((r.state.feedback as PosFeedback.Failure).error is PosError.OfflineRestricted)
    }
}
