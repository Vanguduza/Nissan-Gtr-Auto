package co.zw.nissangtr.pos.domain

import co.zw.nissangtr.pos.domain.error.PosError
import co.zw.nissangtr.pos.domain.model.ApprovalRequest
import co.zw.nissangtr.pos.domain.model.CartLine
import co.zw.nissangtr.pos.domain.model.CartProjection
import co.zw.nissangtr.pos.domain.model.CatalogPart
import co.zw.nissangtr.pos.domain.model.CurrencyCode
import co.zw.nissangtr.pos.domain.model.Customer
import co.zw.nissangtr.pos.domain.model.CustomerKind
import co.zw.nissangtr.pos.domain.model.Money
import co.zw.nissangtr.pos.domain.model.OfflineQueued
import co.zw.nissangtr.pos.domain.model.OfflineSyncStatus
import co.zw.nissangtr.pos.domain.model.ReceiptContacts
import co.zw.nissangtr.pos.domain.model.Tender
import co.zw.nissangtr.pos.domain.model.TenderLine
import co.zw.nissangtr.pos.domain.state.PosEffect
import co.zw.nissangtr.pos.domain.state.PosFeedback
import co.zw.nissangtr.pos.domain.state.PosIntent
import co.zw.nissangtr.pos.domain.state.PosNotice
import co.zw.nissangtr.pos.domain.state.PosSaleEffect
import co.zw.nissangtr.pos.domain.state.PosSaleEvent
import co.zw.nissangtr.pos.domain.state.PosSaleIntent
import co.zw.nissangtr.pos.domain.state.PosState
import co.zw.nissangtr.pos.domain.state.isLocal
import co.zw.nissangtr.pos.domain.state.reduce
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PosOfflineTest {
    private fun usd(v: Double) = Money.ofMajor(v, CurrencyCode.USD)
    private val oil = CatalogPart("si-1", "15208-65F0A", "Oil Filter", usd(12.50), 3.0, null)
    private val pads = CatalogPart("si-2", "D1060-JF00A", "Front Brake Pad Set", usd(96.0), 8.0, null)
    private val offline = PosState(online = false)
    private val contacts = ReceiptContacts(null, null)

    private fun refusedFor(s: PosState): Set<String> =
        ((s.feedback as PosFeedback.Failure).error as PosError.OfflineRestricted).blocked

    @Test
    fun `offline adds build a local cart priced from the snapshot, with no server calls`() {
        var r = reduce(offline, PosIntent.AddPart(oil))
        assertTrue(r.effects.isEmpty())
        r = reduce(r.state, PosIntent.AddPart(oil))
        r = reduce(r.state, PosIntent.AddPart(pads))
        val cart = r.state.cart
        assertTrue(cart.isLocal)
        assertEquals(2, cart.lines.size)
        assertEquals(2.0, cart.lines.first().qty, 0.0)
        assertEquals(12100L, cart.total.minor)
        val qty = reduce(r.state, PosIntent.SetQuantity(cart.lines.first().lineId, 1.0)).state
        assertEquals(10850L, qty.cart.total.minor)
        val removed = reduce(qty, PosIntent.RemoveLine(qty.cart.lines.last().lineId)).state
        assertEquals(1250L, removed.cart.total.minor)
    }

    @Test
    fun `offline stock from the snapshot caps the quantity`() {
        var s = offline
        repeat(3) { s = reduce(s, PosIntent.AddPart(oil)).state }
        val over = reduce(s, PosIntent.AddPart(oil)).state
        assertEquals(3.0, over.cart.lines.single().qty, 0.0)
        assertEquals("offline_stock", ((over.feedback as PosFeedback.Failure).error as PosError.BusinessRule).rule)
    }

    @Test
    fun `a sale started online is not converted while offline`() {
        val line = CartLine("l1", "si-2", "D1060-JF00A", "Pads", 1.0, usd(96.0), usd(96.0), false, null)
        val server = PosState(online = false, cart = CartProjection("cart-9", CurrencyCode.USD, listOf(line), usd(96.0), usd(0.0), usd(96.0)))
        assertEquals(setOf("server_cart"), refusedFor(reduce(server, PosIntent.AddPart(oil)).state))
        assertEquals(setOf("server_cart"), refusedFor(reduce(server, PosSaleIntent.OpenPayment).state))
        assertEquals(setOf("server_cart"), refusedFor(reduce(server, PosIntent.SetQuantity("l1", 2.0)).state))
    }

    @Test
    fun `offline is cash only and walk-in only, and back-office actions wait for the connection`() {
        val s = reduce(offline, PosIntent.AddPart(pads)).state
        val card = reduce(s, PosSaleIntent.Checkout(listOf(TenderLine(Tender.EcoCash, usd(96.0))), null, contacts))
        assertTrue(card.effects.isEmpty())
        assertEquals(setOf("non_cash"), refusedFor(card.state))
        val cust = Customer("c1", "Rudo", CustomerKind.Individual, null, null, null, null)
        assertEquals(setOf("walk_in_only"), refusedFor(reduce(s, PosSaleIntent.SelectCustomer(cust)).state))
        assertEquals(setOf("manager_approval"), refusedFor(reduce(s, PosSaleIntent.RequestApproval(ApprovalRequest.Discount(10.0))).state))
        assertEquals(setOf("online_only"), refusedFor(reduce(s, PosSaleIntent.Park).state))
        assertEquals(setOf("online_only"), refusedFor(reduce(s, PosSaleIntent.CreateQuotation(null, null)).state))
    }

    @Test
    fun `cash checkout queues the sale and shows an offline receipt with change`() {
        val s = reduce(reduce(offline, PosIntent.AddPart(pads)).state, PosSaleIntent.OpenPayment).state
        assertTrue(s.paymentOpen)
        val r = reduce(s, PosSaleIntent.Checkout(listOf(TenderLine(Tender.Cash, usd(96.0))), usd(100.0), contacts))
        assertTrue(r.state.paying)
        val effect = r.effects.single() as PosSaleEffect.QueueOfflineSale
        assertEquals(9600L, effect.cart.total.minor)
        val done = reduce(r.state, PosSaleEvent.OfflineSaleQueued(effect, OfflineQueued("0f1e2d3c-aaaa", "2026-10-01T10:00", OfflineSyncStatus(1, 0)))).state
        assertTrue(done.cart.isEmpty)
        assertFalse(done.paying)
        assertTrue(done.receipt!!.offline)
        assertEquals("OFFLINE-0F1E2D3C", done.receipt!!.documentNumber)
        assertEquals(400L, done.receipt!!.change!!.minor)
        assertEquals(1, done.offlineQueue.pending)
        assertEquals(PosNotice.OfflineSaleQueued, (done.feedback as PosFeedback.Notice).notice)
    }

    @Test
    fun `reconnecting moves an unpaid local cart to the server and drains the outbox`() {
        val local = reduce(offline, PosIntent.AddPart(pads)).state
        val back = reduce(local, PosIntent.ConnectivityChanged(true))
        assertTrue(back.state.online)
        assertTrue(back.state.cart.isEmpty)
        assertEquals(1, back.state.cartBusy)
        assertTrue(back.effects.any { it is PosSaleEffect.PromoteLocalCart && it.cart == local.cart })
        assertTrue(back.effects.any { it is PosSaleEffect.SyncOffline })
        // Nothing local: just drain.
        assertEquals(listOf<PosEffect>(PosSaleEffect.SyncOffline), reduce(offline, PosIntent.ConnectivityChanged(true)).effects)
    }

    @Test
    fun `sync results update the queue counts`() {
        val s = reduce(PosState(), PosSaleIntent.SyncOffline).state
        assertTrue(s.offlineSyncing)
        val done = reduce(s, PosSaleEvent.OfflineStatusLoaded(OfflineSyncStatus(0, 1, synced = 2), afterSync = true)).state
        assertFalse(done.offlineSyncing)
        assertEquals(1, done.offlineQueue.conflicts)
        assertEquals(PosNotice.OfflineSynced, (done.feedback as PosFeedback.Notice).notice)
        assertNull(reduce(PosState(), PosSaleEvent.OfflineStatusLoaded(OfflineSyncStatus(0, 0), afterSync = false)).state.feedback)
    }
}
