package co.zw.nissangtr.pos.domain

import co.zw.nissangtr.pos.domain.error.PosError
import co.zw.nissangtr.pos.domain.model.ApprovalRequest
import co.zw.nissangtr.pos.domain.model.CartLine
import co.zw.nissangtr.pos.domain.model.CartProjection
import co.zw.nissangtr.pos.domain.model.CurrencyCode
import co.zw.nissangtr.pos.domain.model.Customer
import co.zw.nissangtr.pos.domain.model.CustomerDraft
import co.zw.nissangtr.pos.domain.model.CustomerKind
import co.zw.nissangtr.pos.domain.model.GarageVehicle
import co.zw.nissangtr.pos.domain.model.InvoiceSummary
import co.zw.nissangtr.pos.domain.model.ManagerCredentials
import co.zw.nissangtr.pos.domain.model.Money
import co.zw.nissangtr.pos.domain.model.ParkedSale
import co.zw.nissangtr.pos.domain.model.Receipt
import co.zw.nissangtr.pos.domain.model.ReceiptContacts
import co.zw.nissangtr.pos.domain.model.Tender
import co.zw.nissangtr.pos.domain.model.TenderLine
import co.zw.nissangtr.pos.domain.model.VehicleModel
import co.zw.nissangtr.pos.domain.state.PosDestination
import co.zw.nissangtr.pos.domain.state.PosEffect
import co.zw.nissangtr.pos.domain.state.PosFeedback
import co.zw.nissangtr.pos.domain.state.PosIntent
import co.zw.nissangtr.pos.domain.state.PosSaleEffect
import co.zw.nissangtr.pos.domain.state.PosSaleEvent
import co.zw.nissangtr.pos.domain.state.PosSaleIntent
import co.zw.nissangtr.pos.domain.state.PosState
import co.zw.nissangtr.pos.domain.state.reduce
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PosSaleFlowsTest {
    private fun usd(v: Double) = Money.ofMajor(v, CurrencyCode.USD)
    private val line = CartLine("l1", "si", "OEM-1", "Brake pads", 1.0, usd(96.0), usd(96.0), false, null)
    private val cart = CartProjection("cart-1", CurrencyCode.USD, listOf(line), usd(96.0), usd(0.0), usd(96.0))
    private val selling = PosState(cart = cart)
    private val contacts = ReceiptContacts(null, null)

    @Test
    fun `payment opens only with a sale and online`() {
        assertFalse(reduce(PosState(), PosSaleIntent.OpenPayment).state.paymentOpen)
        assertTrue(reduce(selling, PosSaleIntent.OpenPayment).state.paymentOpen)
        val offline = reduce(selling.copy(online = false), PosSaleIntent.OpenPayment).state
        assertFalse(offline.paymentOpen)
        assertTrue((offline.feedback as PosFeedback.Failure).error is PosError.OfflineRestricted)
    }

    @Test
    fun `tenders must equal the server total exactly`() {
        val short = reduce(selling, PosSaleIntent.Checkout(listOf(TenderLine(Tender.Cash, usd(90.0))), null, contacts))
        assertTrue(short.effects.isEmpty())
        assertTrue(short.state.feedback is PosFeedback.Failure)
        val over = reduce(selling, PosSaleIntent.Checkout(listOf(TenderLine(Tender.Cash, usd(100.0))), usd(100.0), contacts))
        assertTrue(over.effects.isEmpty())
        val split = reduce(
            selling,
            PosSaleIntent.Checkout(listOf(TenderLine(Tender.Cash, usd(50.0)), TenderLine(Tender.EcoCash, usd(46.0))), usd(60.0), contacts),
        )
        assertTrue(split.state.paying)
        val effect = split.effects.single() as PosSaleEffect.Checkout
        assertEquals(2, effect.tenders.size)
    }

    @Test
    fun `a second checkout while paying is ignored`() {
        val paying = selling.copy(paying = true)
        assertTrue(reduce(paying, PosSaleIntent.Checkout(listOf(TenderLine(Tender.Cash, usd(96.0))), null, contacts)).effects.isEmpty())
    }

    @Test
    fun `completed sale clears the cart and shows the receipt, new sale resets context`() {
        val receipt = Receipt("inv", "INV-1", cart.lines, cart.subtotal, cart.discount, cart.total, emptyList(), null, null, "Rudo", null, "Op", "2026-10-01T10:00")
        var s = reduce(selling.copy(paying = true, customer = cust), PosSaleEvent.CheckoutDone(receipt)).state
        assertTrue(s.cart.isEmpty)
        assertEquals(receipt, s.receipt)
        assertFalse(s.paying)
        s = reduce(s, PosSaleIntent.NewSale).state
        assertNull(s.receipt)
        assertNull(s.customer)
        assertEquals(PosDestination.Home, s.destination)
    }

    private val cust = Customer("c1", "Rudo", CustomerKind.Individual, null, null, null, null)

    @Test
    fun `selecting a customer attaches them to an open cart and loads the garage`() {
        val r = reduce(selling, PosSaleIntent.SelectCustomer(cust))
        assertEquals(cust, r.state.customer)
        assertTrue(r.effects.any { it is PosSaleEffect.LoadGarage })
        assertTrue(r.effects.any { it is PosSaleEffect.AttachCustomer && it.customerId == "c1" })
    }

    @Test
    fun `garage with one vehicle sets the sale vehicle, several ask which one`() {
        val gtr = GarageVehicle("g1", "gt-r", "GT-R", "R35", "R35", "VR38DETT", true)
        val nav = GarageVehicle("g2", "navara", "Navara", "D40", "D40", "YD25", false)
        val base = reduce(selling, PosSaleIntent.SelectCustomer(cust)).state
        val one = reduce(base, PosSaleEvent.GarageLoaded("c1", listOf(gtr))).state
        assertEquals("VR38DETT", one.vehicle?.engineCode)
        val many = reduce(base, PosSaleEvent.GarageLoaded("c1", listOf(gtr, nav))).state
        assertTrue(many.garagePrompt)
        val chosen = reduce(many, PosSaleIntent.ChooseGarageVehicle(nav))
        assertEquals("YD25", chosen.state.vehicle?.engineCode)
        assertFalse(chosen.state.garagePrompt)
        assertTrue(chosen.effects.any { it is PosEffect.SetCartVehicle })
    }

    @Test
    fun `customer drafts are validated before saving`() {
        val blank = reduce(PosState(), PosSaleIntent.CreateCustomer(CustomerDraft(CustomerKind.Individual, " ", null, null, null, null)))
        assertTrue(blank.effects.isEmpty())
        val badEmail = reduce(PosState(), PosSaleIntent.CreateCustomer(CustomerDraft(CustomerKind.Individual, "Rudo", null, "nope", null, null)))
        assertTrue(badEmail.effects.isEmpty())
        val ok = reduce(PosState(), PosSaleIntent.CreateCustomer(CustomerDraft(CustomerKind.Individual, "Rudo", null, "r@x.co", null, null)))
        assertTrue(ok.effects.single() is PosSaleEffect.SaveCustomer)
    }

    @Test
    fun `manager approval needs credentials and runs once`() {
        var s = reduce(selling, PosSaleIntent.RequestApproval(ApprovalRequest.Discount(10.0))).state
        assertTrue(s.approval is ApprovalRequest.Discount)
        assertTrue(reduce(s, PosSaleIntent.SubmitApproval(ManagerCredentials("", "", null))).effects.isEmpty())
        val r = reduce(s, PosSaleIntent.SubmitApproval(ManagerCredentials("mgr", "pw", null)))
        assertTrue(r.state.approving)
        assertTrue(reduce(r.state, PosSaleIntent.SubmitApproval(ManagerCredentials("mgr", "pw", null))).effects.isEmpty())
        s = reduce(r.state, PosSaleEvent.Approved(ApprovalRequest.Discount(10.0), cart.copy(discount = usd(9.6)))).state
        assertNull(s.approval)
        assertEquals(960L, s.cart.discount.minor)
    }

    @Test
    fun `discount outside 0-100 percent is refused`() {
        assertNull(reduce(selling, PosSaleIntent.RequestApproval(ApprovalRequest.Discount(150.0))).state.approval)
    }

    @Test
    fun `refund approval reloads invoices`() {
        val inv = InvoiceSummary("i1", "INV-1", null, usd(10.0), null, null)
        val s = reduce(PosState(approval = ApprovalRequest.Refund(inv), approving = true), PosSaleEvent.Approved(ApprovalRequest.Refund(inv), null))
        assertTrue(s.effects.any { it is PosSaleEffect.LoadInvoices })
    }

    @Test
    fun `parking clears the sale, resuming needs an empty cart`() {
        val parked = reduce(selling, PosSaleEvent.SaleParked).state
        assertTrue(parked.cart.isEmpty)
        val sale = ParkedSale("p1", null, null, usd(5.0), 1)
        assertTrue(reduce(selling, PosSaleIntent.Resume(sale)).effects.isEmpty())
        assertTrue(reduce(parked, PosSaleIntent.Resume(sale)).effects.single() is PosSaleEffect.Resume)
    }

    @Test
    fun `navigating to Orders and Returns loads their data`() {
        assertTrue(reduce(PosState(), PosIntent.Navigate(PosDestination.Orders)).effects.single() is PosSaleEffect.LoadOrders)
        assertTrue(reduce(PosState(), PosIntent.Navigate(PosDestination.Returns)).effects.single() is PosSaleEffect.LoadInvoices)
    }

    @Test
    fun `EPC drill-down goes down and back one level at a time`() {
        val m = VehicleModel("gt-r", "GT-R")
        var s = reduce(PosState(), PosSaleIntent.EpcPickModel(m)).state
        val v = co.zw.nissangtr.pos.domain.model.EpcVariant("r35", "R35", "VR38DETT", null)
        s = reduce(s, PosSaleEvent.EpcVariantsLoaded(m, listOf(v))).state
        s = reduce(s, PosSaleIntent.EpcPickVariant(v)).state
        assertEquals(v, s.epc.variant)
        s = reduce(s, PosSaleIntent.EpcBack).state
        assertNull(s.epc.variant)
        assertEquals(m, s.epc.model)
        s = reduce(s, PosSaleIntent.EpcBack).state
        assertNull(s.epc.model)
    }
}
