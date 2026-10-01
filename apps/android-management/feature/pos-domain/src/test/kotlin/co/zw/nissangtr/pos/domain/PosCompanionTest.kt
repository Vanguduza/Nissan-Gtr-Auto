package co.zw.nissangtr.pos.domain

import co.zw.nissangtr.pos.domain.model.CartLine
import co.zw.nissangtr.pos.domain.model.CartProjection
import co.zw.nissangtr.pos.domain.model.CompanionSession
import co.zw.nissangtr.pos.domain.model.CompanionStatus
import co.zw.nissangtr.pos.domain.model.CurrencyCode
import co.zw.nissangtr.pos.domain.model.Money
import co.zw.nissangtr.pos.domain.model.Receipt
import co.zw.nissangtr.pos.domain.state.CompanionEffect
import co.zw.nissangtr.pos.domain.state.CompanionEvent
import co.zw.nissangtr.pos.domain.state.CompanionIntent
import co.zw.nissangtr.pos.domain.state.PosSaleEvent
import co.zw.nissangtr.pos.domain.state.PosState
import co.zw.nissangtr.pos.domain.state.reduce
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PosCompanionTest {
    private fun usd(v: Double) = Money.ofMajor(v, CurrencyCode.USD)
    private fun cart(id: String, vararg lines: CartLine): CartProjection {
        val total = usd(lines.sumOf { it.lineTotal.minor } / 100.0)
        return CartProjection(id, CurrencyCode.USD, lines.toList(), total, usd(0.0), total)
    }
    private val pads = CartLine("l1", "si-2", "D1060-JF00A", "Pads", 1.0, usd(96.0), usd(96.0), false, null)
    private val oil = CartLine("l2", "si-1", "15208-65F0A", "Oil", 1.0, usd(12.5), usd(12.5), false, null)
    private val session = CompanionSession("s1", "cart-1", "482913", "2026-10-01T10:15:00Z", CompanionStatus.Open)

    @Test
    fun `open with no sale creates the cart and session, then watches it`() {
        val r = reduce(PosState(), CompanionIntent.Open)
        assertTrue(r.state.companionOpen)
        assertTrue(r.state.companionPairing)
        assertNull((r.effects.single() as CompanionEffect.Create).cartId)
        val created = reduce(r.state, CompanionEvent.Created(session, cart("cart-1")))
        assertEquals("482913", created.state.companion?.pairingCode)
        assertEquals("cart-1", created.state.cart.cartId)
        assertEquals(CompanionEffect.Watch("s1", "cart-1"), created.effects.single())
    }

    @Test
    fun `open on a live session only shows it, and offline it is refused`() {
        val live = PosState(cart = cart("cart-1", pads), companion = session)
        val r = reduce(live, CompanionIntent.Open)
        assertTrue(r.effects.isEmpty())
        assertTrue(r.state.companionOpen)
        assertTrue(reduce(PosState(online = false), CompanionIntent.Open).effects.isEmpty())
        assertEquals("cart-1", (reduce(PosState(cart = cart("cart-1", pads)), CompanionIntent.Open).effects.single() as CompanionEffect.Create).cartId)
    }

    @Test
    fun `phone scans arrive through polling, but never over a till mutation in flight`() {
        val s = PosState(cart = cart("cart-1", pads), companion = session)
        val polled = reduce(s, CompanionEvent.Polled("s1", CompanionStatus.Claimed, cart("cart-1", pads, oil))).state
        assertEquals(CompanionStatus.Claimed, polled.companion?.status)
        assertEquals(2, polled.cart.lines.size)
        val busy = reduce(s.copy(cartBusy = 1), CompanionEvent.Polled("s1", CompanionStatus.Claimed, cart("cart-1", pads, oil))).state
        assertEquals(1, busy.cart.lines.size)
        // Stale poll from an ended session is ignored.
        assertEquals(s, reduce(s, CompanionEvent.Polled("old", CompanionStatus.Claimed, cart("cart-1"))).state)
    }

    @Test
    fun `ending revokes a live session`() {
        val r = reduce(PosState(cart = cart("cart-1", pads), companion = session, companionOpen = true), CompanionIntent.End)
        assertNull(r.state.companion)
        assertFalse(r.state.companionOpen)
        assertEquals(CompanionEffect.Revoke("s1"), r.effects.single())
    }

    @Test
    fun `the pairing ends with the sale`() {
        val s = PosState(cart = cart("cart-1", pads), companion = session.copy(status = CompanionStatus.Claimed), paying = true)
        val receipt = Receipt("inv", "INV-1", listOf(pads), usd(96.0), usd(0.0), usd(96.0), emptyList(), null, null, null, null, null, "2026-10-01T10:00")
        val done = reduce(s, PosSaleEvent.CheckoutDone(receipt))
        assertNull(done.state.companion)
        assertTrue(done.effects.contains(CompanionEffect.Revoke("s1")))
        val parked = reduce(s.copy(paying = false), PosSaleEvent.SaleParked)
        assertTrue(parked.effects.contains(CompanionEffect.Revoke("s1")))
    }

    @Test
    fun `phone side - claim a six digit code, then scans go to the till's cart`() {
        assertTrue(reduce(PosState(), CompanionIntent.Claim("12a")).effects.isEmpty())
        val claiming = reduce(PosState(), CompanionIntent.Claim("482 913"))
        assertEquals(CompanionEffect.ClaimCode("482913"), claiming.effects.single())
        val linked = reduce(claiming.state, CompanionEvent.Claimed(co.zw.nissangtr.pos.domain.model.ScannerLink("s1", "cart-1"))).state
        assertTrue(linked.scannerOpen)
        val scan = reduce(linked, CompanionIntent.Scanned("GTR:D1060-JF00A"))
        assertEquals(CompanionEffect.AddFromQr("cart-1", "GTR:D1060-JF00A"), scan.effects.single())
        // One scan at a time.
        assertTrue(reduce(scan.state, CompanionIntent.Scanned("GTR:15208")).effects.isEmpty())
        val added = reduce(scan.state, CompanionEvent.ScanAdded("D1060-JF00A")).state
        assertEquals(listOf("D1060-JF00A"), added.scanner?.scans)
        assertFalse(added.scanner!!.busy)
        assertNull(reduce(added, CompanionIntent.LeaveScanner).state.scanner)
    }
}
