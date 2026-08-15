package co.zw.nissangtr.pos.pay

import co.zw.nissangtr.pos.api.CheckoutReceiptContacts
import co.zw.nissangtr.pos.api.FakePosClient
import co.zw.nissangtr.pos.api.MoneyCents
import co.zw.nissangtr.pos.api.TenderMode
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class CheckoutFakeIntegrationTest {

    @Test
    fun splitCashPlusBank_postsAppliedSumEqualsDue() = runTest {
        val client = FakePosClient()
        val cartId = client.fakeTillState().session.cartId!!
        val dueCents = MoneyCents.majorToCents(client.fakeTillState().ticket.subtotal)
        val drafts = TenderAllocator.splitEqually(
            dueCents,
            listOf(TenderMode.CASH, TenderMode.BANK),
        )
        val snap = TenderAllocator.allocate(dueCents, drafts)
        assertEquals(0L, snap.remainingCents)
        assertEquals(dueCents, snap.appliedSumCents)

        val result = LiveRailSettler.confirmCheckout(
            client = client,
            cartId = cartId,
            dueCents = dueCents,
            drafts = drafts,
            currency = "USD",
            online = true,
            receipt = CheckoutReceiptContacts(
                email = "buyer@example.com",
                whatsappE164 = "+263771234567",
            ),
        )
        assertTrue(result.paid)
        assertEquals("posted", result.status)

        val posted = client.lastCheckoutTenders!!
        val postedSum = posted.sumOf { MoneyCents.majorStringToCents(it.amount) }
        assertEquals(dueCents, postedSum)
        assertEquals(setOf("cash", "bank"), posted.map { it.tender }.toSet())
        assertEquals("buyer@example.com", client.lastReceiptContacts?.email)
        assertEquals("+263771234567", client.lastReceiptContacts?.whatsappE164)
    }

    @Test
    fun creditHold_onHold_notTreatedAsPaid() = runTest {
        val client = FakePosClient()
        val cartId = client.fakeTillState().session.cartId!!
        client.bindCustomer(cartId, "cust-hold")
        val dueCents = MoneyCents.majorToCents(client.fakeTillState().ticket.subtotal)
        val drafts = listOf(TenderDraft(TenderMode.CASH, dueCents))
        val result = LiveRailSettler.confirmCheckout(
            client = client,
            cartId = cartId,
            dueCents = dueCents,
            drafts = drafts,
            currency = "USD",
            online = true,
            receipt = CheckoutReceiptContacts(),
        )
        assertEquals("on_hold", result.status)
        assertFalse(result.paid)
    }

    @Test
    fun liveRailFailure_doesNotCheckout() = runTest {
        val client = FakePosClient()
        client.failNextRailSettle = true
        val cartId = client.fakeTillState().session.cartId!!
        val dueCents = MoneyCents.majorToCents(client.fakeTillState().ticket.subtotal)
        val drafts = TenderAllocator.splitEqually(
            dueCents,
            listOf(TenderMode.CASH, TenderMode.ECOCASH),
        )
        try {
            LiveRailSettler.confirmCheckout(
                client = client,
                cartId = cartId,
                dueCents = dueCents,
                drafts = drafts,
                currency = "USD",
                online = true,
                receipt = CheckoutReceiptContacts(),
            )
            fail("expected LiveRailException")
        } catch (_: Exception) {
            // expected
        }
        assertEquals(null, client.lastCheckoutTenders)
    }

    @Test
    fun offline_nonCashDisabled() {
        assertTrue(TenderMode.CASH.enabledWhen(online = false))
        assertFalse(TenderMode.ECOCASH.enabledWhen(online = false))
        assertFalse(TenderMode.BANK.enabledWhen(online = false))
    }
}
