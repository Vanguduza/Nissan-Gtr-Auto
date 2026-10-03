package co.zw.nissangtr.pos.ui.store

import co.zw.nissangtr.pos.domain.error.PosError
import co.zw.nissangtr.pos.domain.gateway.CartGateway
import co.zw.nissangtr.pos.domain.gateway.CatalogGateway
import co.zw.nissangtr.pos.domain.gateway.FitmentGateway
import co.zw.nissangtr.pos.domain.gateway.PinGateway
import co.zw.nissangtr.pos.domain.gateway.SessionGateway
import co.zw.nissangtr.pos.domain.model.CartProjection
import co.zw.nissangtr.pos.domain.model.CatalogPart
import co.zw.nissangtr.pos.domain.model.CurrencyCode
import co.zw.nissangtr.pos.domain.model.Money
import co.zw.nissangtr.pos.domain.model.Operator
import co.zw.nissangtr.pos.domain.model.PopularPin
import co.zw.nissangtr.pos.domain.model.VehicleGeneration
import co.zw.nissangtr.pos.domain.model.VehicleModel
import co.zw.nissangtr.pos.domain.model.VehicleSelection
import co.zw.nissangtr.pos.domain.gateway.OfflineSaleGateway
import co.zw.nissangtr.pos.domain.model.OfflineQueued
import co.zw.nissangtr.pos.domain.model.OfflineSyncStatus
import co.zw.nissangtr.pos.domain.model.ReceiptContacts
import co.zw.nissangtr.pos.domain.model.Tender
import co.zw.nissangtr.pos.domain.model.TenderLine
import co.zw.nissangtr.pos.domain.result.PosResult
import kotlinx.coroutines.test.advanceTimeBy
import co.zw.nissangtr.pos.domain.state.CompanionIntent
import co.zw.nissangtr.pos.domain.model.CompanionStatus
import co.zw.nissangtr.pos.domain.model.CompanionSession
import co.zw.nissangtr.pos.domain.gateway.CompanionGateway
import co.zw.nissangtr.pos.domain.state.PosFeedback
import co.zw.nissangtr.pos.domain.state.PosIntent
import co.zw.nissangtr.pos.domain.state.PosSaleIntent
import co.zw.nissangtr.pos.ui.fakes.FakeSaleGateways
import co.zw.nissangtr.pos.ui.fakes.PosFixtures
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PosStoreTest {

    private class FakeCart : CartGateway {
        var opened = 0
        val lines = mutableListOf<CatalogPart>()
        override suspend fun open(currency: CurrencyCode): PosResult<CartProjection> {
            opened++
            return PosResult.Ok(CartProjection.empty(currency).copy(cartId = "cart-$opened"))
        }
        override suspend fun addLine(cartId: String, part: CatalogPart, qty: Double): PosResult<CartProjection> {
            lines += part
            val minor = lines.sumOf { it.price!!.minor }
            val total = Money(minor, CurrencyCode.USD)
            return PosResult.Ok(
                CartProjection(
                    cartId, CurrencyCode.USD,
                    lines.mapIndexed { i, p -> PosFixtures.line("l$i", p, 1.0) },
                    total, Money.zero(CurrencyCode.USD), total,
                ),
            )
        }
        override suspend fun setQuantity(cartId: String, lineId: String, qty: Double) = PosResult.Err(PosError.Transient(true))
        override suspend fun removeLine(cartId: String, lineId: String) = PosResult.Err(PosError.Transient(true))
        override suspend fun setVehicle(cartId: String, vehicle: VehicleSelection?): PosResult<Unit> = PosResult.Ok(Unit)
    }

    private class FakePins(var failWrites: Boolean = false) : PinGateway {
        override suspend fun pins(): PosResult<List<PopularPin>> = PosResult.Ok(emptyList())
        override suspend fun pin(pin: PopularPin) = if (failWrites) PosResult.Err(PosError.Transient(true)) else PosResult.Ok(Unit)
        override suspend fun unpin(pin: PopularPin) = if (failWrites) PosResult.Err(PosError.Transient(true)) else PosResult.Ok(Unit)
        override suspend fun hiddenBestSellers(): PosResult<Set<String>> = PosResult.Ok(emptySet())
        override suspend fun hideBestSeller(stockItemId: String) = if (failWrites) PosResult.Err(PosError.Transient(true)) else PosResult.Ok(Unit)
        override suspend fun unhideBestSeller(stockItemId: String): PosResult<Unit> = PosResult.Ok(Unit)
    }

    private class FakeOutbox : OfflineSaleGateway {
        val queued = mutableListOf<CartProjection>()
        var syncs = 0
        override suspend fun searchLocal(query: String, vehicle: VehicleSelection?) =
            PosResult.Ok(PosFixtures.bestSellers.filter { it.name.contains(query, ignoreCase = true) })
        override suspend fun queueCashSale(cart: CartProjection, vehicle: VehicleSelection?, contacts: ReceiptContacts): PosResult<OfflineQueued> {
            queued += cart
            return PosResult.Ok(OfflineQueued("c0ffee00-1111", "2026-10-01T10:00", OfflineSyncStatus(queued.size, 0)))
        }
        override suspend fun sync(): PosResult<OfflineSyncStatus> {
            syncs++
            return PosResult.Ok(OfflineSyncStatus(0, 0, synced = queued.size))
        }
        override suspend fun status() = PosResult.Ok(OfflineSyncStatus(queued.size, 0))
    }

    private class FakeCompanion : CompanionGateway {
        var polls = 0
        val revoked = mutableListOf<String>()
        override suspend fun create(cartId: String) =
            PosResult.Ok(CompanionSession("s1", cartId, "482913", "2099-01-01T00:00:00Z", CompanionStatus.Open))
        override suspend fun revoke(sessionId: String): PosResult<Unit> { revoked += sessionId; return PosResult.Ok(Unit) }
        override suspend fun status(sessionId: String): PosResult<CompanionStatus> {
            polls++
            return PosResult.Ok(if (polls >= 2) CompanionStatus.Claimed else CompanionStatus.Open)
        }
        // The phone has scanned the pads into the sale by the second poll.
        override suspend fun cart(cartId: String): PosResult<CartProjection> {
            val lines = if (polls >= 2) listOf(PosFixtures.line("p1", PosFixtures.brakePads, 1.0)) else emptyList()
            val total = Money(lines.sumOf { it.lineTotal.minor }, CurrencyCode.USD)
            return PosResult.Ok(CartProjection(cartId, CurrencyCode.USD, lines, total, Money.zero(CurrencyCode.USD), total))
        }
        override suspend fun claim(pairingCode: String) = PosResult.Ok(co.zw.nissangtr.pos.domain.model.ScannerLink("s1", "cart-1"))
        override suspend fun addFromQr(cartId: String, payload: String) = PosResult.Ok(payload)
    }

    private fun gateways(
        cart: CartGateway = FakeCart(),
        pins: PinGateway = FakePins(),
        outbox: OfflineSaleGateway = OfflineSaleGateway.None,
        companion: CompanionGateway = CompanionGateway.None,
        till: co.zw.nissangtr.pos.domain.gateway.TillGateway = co.zw.nissangtr.pos.domain.gateway.TillGateway.None,
        sales: co.zw.nissangtr.pos.domain.gateway.SalesGateway = FakeSaleGateways.sales,
        badgeScanner: co.zw.nissangtr.pos.domain.gateway.BadgeScanner = co.zw.nissangtr.pos.domain.gateway.BadgeScanner.None,
        reserve: co.zw.nissangtr.pos.domain.gateway.ReserveCheckoutGateway = co.zw.nissangtr.pos.domain.gateway.ReserveCheckoutGateway.None,
        split: co.zw.nissangtr.pos.domain.gateway.SplitPaymentGateway = co.zw.nissangtr.pos.domain.gateway.SplitPaymentGateway.None,
        terminal: co.zw.nissangtr.pos.domain.gateway.CardTerminalGateway = co.zw.nissangtr.pos.domain.gateway.CardTerminalGateway.None,
    ) = PosGateways(
        session = object : SessionGateway {
            override suspend fun operator() = PosResult.Ok(Operator("Tendai Moyo", "Sales"))
        },
        catalog = object : CatalogGateway {
            override suspend fun search(query: String, vehicle: VehicleSelection?) =
                PosResult.Ok(PosFixtures.bestSellers.filter { it.name.contains(query, ignoreCase = true) || it.oemPartNumber.equals(query, ignoreCase = true) })
            override suspend fun bestSellers() = PosResult.Ok(PosFixtures.bestSellers)
        },
        fitment = object : FitmentGateway {
            override suspend fun models() = PosResult.Ok(PosFixtures.models)
            override suspend fun generations(model: VehicleModel) = PosResult.Ok(listOf(VehicleGeneration("R35", "R35")))
            override suspend fun engines(model: VehicleModel, generation: VehicleGeneration) = PosResult.Ok(listOf("VR38DETT"))
        },
        cart = cart,
        pins = pins,
        checkout = FakeSaleGateways.checkout,
        customers = FakeSaleGateways.customers,
        sales = sales,
        epc = FakeSaleGateways.epc,
        offline = outbox,
        companion = companion,
        till = till,
        badgeScanner = badgeScanner,
        reserve = reserve,
        split = split,
        terminal = terminal,
    )

    private fun TestScope.store(g: PosGateways) = PosStore(TestScope(UnconfinedTestDispatcher(testScheduler)), g)

    @Test
    fun `start loads operator, cascade models and the popular row`() = runTest {
        val s = store(gateways())
        s.dispatch(PosIntent.Start)
        advanceUntilIdle()
        assertEquals("Tendai Moyo", s.state.value.operator?.displayName)
        assertEquals(3, s.state.value.cascade.models.size)
        assertEquals(4, s.state.value.popularRow.size)
    }

    @Test
    fun `first add opens one cart, then adds to it`() = runTest {
        val cart = FakeCart()
        val s = store(gateways(cart = cart))
        s.dispatch(PosIntent.AddPart(PosFixtures.oilFilter))
        s.dispatch(PosIntent.AddPart(PosFixtures.brakePads))
        advanceUntilIdle()
        assertEquals(1, cart.opened)
        assertEquals(2, s.state.value.cart.lines.size)
        assertEquals(10850L, s.state.value.cart.total.minor)
        assertEquals(0, s.state.value.cartBusy)
    }

    @Test
    fun `cascade drives generations and engines through the gateway`() = runTest {
        val s = store(gateways())
        s.dispatch(PosIntent.Start)
        s.dispatch(PosIntent.PickModel(PosFixtures.models.first()))
        s.dispatch(PosIntent.PickGeneration(VehicleGeneration("R35", "R35")))
        s.dispatch(PosIntent.PickEngine("VR38DETT"))
        advanceUntilIdle()
        assertEquals("GT-R R35 VR38DETT", s.state.value.vehicle?.label)
    }

    @Test
    fun `server-refused pin is rolled back and reported`() = runTest {
        val s = store(gateways(pins = FakePins(failWrites = true)))
        s.dispatch(PosIntent.Pin(PopularPin.forPart(PosFixtures.oilFilter)))
        advanceUntilIdle()
        assertTrue(s.state.value.pins.isEmpty())
        assertTrue(s.state.value.feedback is PosFeedback.Failure)
    }

    @Test
    fun `failed cart mutation is reported and the cart is released`() = runTest {
        val s = store(gateways())
        s.dispatch(PosIntent.AddPart(PosFixtures.oilFilter))
        advanceUntilIdle()
        s.dispatch(PosIntent.SetQuantity("l0", 3.0))
        advanceUntilIdle()
        assertTrue(s.state.value.feedback is PosFeedback.Failure)
        assertEquals(0, s.state.value.cartBusy)
    }

    @Test
    fun `search results arrive for the current query`() = runTest {
        val s = store(gateways())
        s.dispatch(PosIntent.SearchFor("filter"))
        advanceUntilIdle()
        assertEquals(listOf("Oil Filter", "Air Filter"), s.state.value.searchResults?.map { it.name })
    }

    @Test
    fun `checkout posts the sale and produces a receipt with change`() = runTest {
        val s = store(gateways())
        s.dispatch(PosIntent.AddPart(PosFixtures.oilFilter))
        advanceUntilIdle()
        s.dispatch(co.zw.nissangtr.pos.domain.state.PosSaleIntent.OpenPayment)
        s.dispatch(
            co.zw.nissangtr.pos.domain.state.PosSaleIntent.Checkout(
                tenders = listOf(co.zw.nissangtr.pos.domain.model.TenderLine(co.zw.nissangtr.pos.domain.model.Tender.Cash, Money(1250, CurrencyCode.USD))),
                cashGiven = Money(2000, CurrencyCode.USD),
                contacts = co.zw.nissangtr.pos.domain.model.ReceiptContacts(null, null),
            ),
        )
        advanceUntilIdle()
        val receipt = s.state.value.receipt
        assertEquals("INV-000123", receipt?.documentNumber)
        assertEquals(750L, receipt?.change?.minor)
        assertTrue(s.state.value.cart.isEmpty)
    }

    @Test
    fun `void approval empties the sale`() = runTest {
        val s = store(gateways())
        s.dispatch(PosIntent.AddPart(PosFixtures.oilFilter))
        advanceUntilIdle()
        s.dispatch(co.zw.nissangtr.pos.domain.state.PosSaleIntent.RequestApproval(co.zw.nissangtr.pos.domain.model.ApprovalRequest.VoidSale))
        s.dispatch(co.zw.nissangtr.pos.domain.state.PosSaleIntent.SubmitApproval(co.zw.nissangtr.pos.domain.model.ManagerCredentials("mgr", "pw", null)))
        advanceUntilIdle()
        assertTrue(s.state.value.cart.isEmpty)
        assertEquals(null, s.state.value.approval)
    }

    @Test
    fun `EPC drill-down opens the only diagram, loads its image, and Add puts the part in the cart`() = runTest {
        val s = store(gateways())
        s.dispatch(PosSaleIntent.EpcPickModel(PosFixtures.models.first()))
        advanceUntilIdle()
        s.dispatch(PosSaleIntent.EpcPickVariant(s.state.value.epc.variants!!.first()))
        advanceUntilIdle()
        s.dispatch(PosSaleIntent.EpcPickSection(s.state.value.epc.sections!!.first()))
        advanceUntilIdle()
        val epc = s.state.value.epc
        assertEquals(3, epc.detail?.hotspots?.size)
        assertTrue(epc.image?.bytes?.isNotEmpty() == true)
        s.dispatch(PosSaleIntent.EpcAdd(epc.detail!!.parts.first()))
        advanceUntilIdle()
        assertEquals("D1060-JF00A", s.state.value.cart.lines.single().oemPartNumber)
    }

    @Test
    fun `offline - search the snapshot, sell for cash, then reconnect moves the next cart to the server and syncs`() = runTest {
        val cart = FakeCart()
        val outbox = FakeOutbox()
        val s = store(gateways(cart = cart, outbox = outbox))
        s.dispatch(PosIntent.ConnectivityChanged(false))
        s.dispatch(PosIntent.SearchFor("oil"))
        advanceUntilIdle()
        val oil = s.state.value.searchResults!!.single()
        s.dispatch(PosIntent.AddPart(oil))
        s.dispatch(PosSaleIntent.OpenPayment)
        s.dispatch(PosSaleIntent.Checkout(listOf(TenderLine(Tender.Cash, oil.price!!)), null, ReceiptContacts(null, null)))
        advanceUntilIdle()
        assertEquals(0, cart.opened)
        assertEquals(1, outbox.queued.size)
        assertTrue(s.state.value.receipt!!.offline)
        assertEquals(1, s.state.value.offlineQueue.pending)

        // An unpaid local cart when the connection returns: rebuilt on the server, outbox drained.
        s.dispatch(PosSaleIntent.NewSale)
        s.dispatch(PosIntent.AddPart(oil))
        s.dispatch(PosIntent.ConnectivityChanged(true))
        advanceUntilIdle()
        assertEquals(1, cart.opened)
        assertEquals("cart-1", s.state.value.cart.cartId)
        assertEquals(oil.oemPartNumber, s.state.value.cart.lines.single().oemPartNumber)
        assertEquals(1, outbox.syncs)
        assertEquals(0, s.state.value.offlineQueue.pending)
    }

    @Test
    fun `companion pairing opens a sale, shows the code, picks up phone scans and ends on request`() = runTest {
        val cart = FakeCart()
        val companion = FakeCompanion()
        val s = PosStore(TestScope(UnconfinedTestDispatcher(testScheduler)), gateways(cart = cart, companion = companion), companionPollMs = 1_000)
        s.dispatch(CompanionIntent.Open)
        advanceTimeBy(100)
        assertEquals(1, cart.opened)
        assertEquals("482913", s.state.value.companion?.pairingCode)
        assertEquals("cart-1", s.state.value.companion?.cartId)
        advanceTimeBy(2_500)
        assertEquals(CompanionStatus.Claimed, s.state.value.companion?.status)
        assertEquals("D1060-JF00A", s.state.value.cart.lines.single().oemPartNumber)
        s.dispatch(CompanionIntent.End)
        advanceUntilIdle()
        assertEquals(listOf("s1"), companion.revoked)
        assertEquals(null, s.state.value.companion)
        val polls = companion.polls
        advanceTimeBy(5_000)
        assertEquals(polls, companion.polls)
    }

    private class FakeTill : co.zw.nissangtr.pos.domain.gateway.TillGateway {
        var session: co.zw.nissangtr.pos.domain.model.TillSession? = null
        val attached = mutableListOf<Pair<String, String>>()
        override suspend fun current() = PosResult.Ok(session)
        override suspend fun open(openingFloat: Money): PosResult<co.zw.nissangtr.pos.domain.model.TillSession> {
            val s = co.zw.nissangtr.pos.domain.model.TillSession(
                "till-1", "w1", openingFloat.currency, "u1", openingFloat,
                co.zw.nissangtr.pos.domain.model.TillStatus.Open, null, null, null, null, "2026-10-03T08:00:00Z", null,
            )
            session = s
            return PosResult.Ok(s)
        }
        override suspend fun attachCart(cartId: String, sessionId: String): PosResult<Unit> {
            attached += cartId to sessionId
            return PosResult.Ok(Unit)
        }
        override suspend fun reasons(action: String) = PosResult.Ok(emptyList<co.zw.nissangtr.pos.domain.model.ReasonCode>())
        override suspend fun cashIn(sessionId: String, amount: Money, reasonCode: String, notes: String?) = PosResult.Ok(Unit)
        override suspend fun close(sessionId: String, counts: List<co.zw.nissangtr.pos.domain.model.DenominationCount>, varianceReasonCode: String?, notes: String?) =
            PosResult.Err(PosError.BusinessRule("variance_reason_required", ""))
        override suspend fun handoverOperators() = PosResult.Ok(emptyList<co.zw.nissangtr.pos.domain.model.HandoverOperator>())
        override suspend fun recent() = PosResult.Ok(listOfNotNull(session))
    }

    @Test
    fun `no open till sends the sale to the Till screen, an open till takes the cart`() = runTest {
        val till = FakeTill()
        val cart = FakeCart()
        val s = store(gateways(cart = cart, till = till))
        s.dispatch(PosIntent.Start)
        advanceUntilIdle()
        s.dispatch(PosIntent.AddPart(PosFixtures.oilFilter))
        advanceUntilIdle()
        assertEquals(co.zw.nissangtr.pos.domain.state.PosDestination.Till, s.state.value.destination)
        assertEquals(0, cart.opened)

        s.dispatch(co.zw.nissangtr.pos.domain.state.TillIntent.Open(Money.ofMajor(50.0, CurrencyCode.USD)))
        advanceUntilIdle()
        assertTrue(s.state.value.till.isOpen)
        s.dispatch(PosIntent.AddPart(PosFixtures.oilFilter))
        advanceUntilIdle()
        assertEquals(listOf("cart-1" to "till-1"), till.attached)
        assertEquals(1, s.state.value.cart.lines.size)
    }

    @Test
    fun `an out count keeps the dialog and asks for a variance reason`() = runTest {
        val till = FakeTill()
        val s = store(gateways(till = till))
        s.dispatch(PosIntent.Start)
        s.dispatch(co.zw.nissangtr.pos.domain.state.TillIntent.Open(Money.ofMajor(50.0, CurrencyCode.USD)))
        advanceUntilIdle()
        s.dispatch(co.zw.nissangtr.pos.domain.state.TillIntent.ShowDialog(co.zw.nissangtr.pos.domain.state.TillDialog.Close))
        s.dispatch(co.zw.nissangtr.pos.domain.state.TillIntent.Close(listOf(co.zw.nissangtr.pos.domain.model.DenominationCount(5000, 1)), null, null))
        advanceUntilIdle()
        assertTrue(s.state.value.till.needsVarianceReason)
        assertEquals(co.zw.nissangtr.pos.domain.state.TillDialog.Close, s.state.value.till.dialog)
    }

    @Test
    fun `the front camera reads the manager badge and the approval carries it`() = runTest {
        var usedBadge: String? = null
        var usedCredentials: co.zw.nissangtr.pos.domain.model.ManagerCredentials? = null
        val sales = object : co.zw.nissangtr.pos.domain.gateway.SalesGateway by FakeSaleGateways.sales {
            override suspend fun approve(
                credentials: co.zw.nissangtr.pos.domain.model.ManagerCredentials?,
                request: co.zw.nissangtr.pos.domain.model.ApprovalRequest,
                cartId: String,
                reason: co.zw.nissangtr.pos.domain.model.ReasonCode?,
                notes: String?,
                badge: String?,
            ): PosResult<CartProjection?> {
                usedBadge = badge
                usedCredentials = credentials
                return PosResult.Ok(CartProjection.empty(CurrencyCode.USD))
            }
        }
        val s = store(gateways(sales = sales, badgeScanner = { PosResult.Ok("GTRMGR1:b1:secret") }))
        s.dispatch(PosIntent.AddPart(PosFixtures.oilFilter))
        advanceUntilIdle()
        s.dispatch(PosSaleIntent.RequestApproval(co.zw.nissangtr.pos.domain.model.ApprovalRequest.VoidSale))
        advanceUntilIdle()
        assertTrue(s.state.value.approvalNeedsManager)
        s.dispatch(co.zw.nissangtr.pos.domain.state.GovernanceIntent.ScanBadge(null, null))
        advanceUntilIdle()
        assertEquals("GTRMGR1:b1:secret", usedBadge)
        assertEquals(null, usedCredentials)
        assertEquals(null, s.state.value.approval)
    }

    @Test
    fun `no camera keeps the approval open with a reason to use the password`() = runTest {
        val s = store(gateways())
        s.dispatch(PosIntent.AddPart(PosFixtures.oilFilter))
        advanceUntilIdle()
        s.dispatch(PosSaleIntent.RequestApproval(co.zw.nissangtr.pos.domain.model.ApprovalRequest.VoidSale))
        s.dispatch(co.zw.nissangtr.pos.domain.state.GovernanceIntent.ScanBadge(null, null))
        advanceUntilIdle()
        assertTrue(s.state.value.approval != null)
        assertTrue((s.state.value.feedback as PosFeedback.Failure).error is PosError.HardwareUnavailable)
    }

    /** Server stand-in for reserve-first checkout: one order, settled by the counter or the provider. */
    private class FakeReserve : co.zw.nissangtr.pos.domain.gateway.ReserveCheckoutGateway {
        var state = "awaiting_payment"
        var invoice: String? = null
        val prepared = mutableListOf<String>()
        val settled = mutableListOf<String>()
        val cancelled = mutableListOf<String>()
        var total = Money.zero(CurrencyCode.USD)
        private fun st() = co.zw.nissangtr.pos.domain.model.PaymentStatus("o1", state, total, "2099-01-01T00:00:00Z", null, null, null, null, null, invoice, null)
        override suspend fun prepare(cartId: String, requestId: String, contacts: co.zw.nissangtr.pos.domain.model.ReceiptContacts): PosResult<String> {
            prepared += requestId
            return PosResult.Ok("o1")
        }
        override suspend fun status(orderId: String) = PosResult.Ok(st())
        override suspend fun settle(orderId: String, paymentRequestId: String, tenders: List<co.zw.nissangtr.pos.domain.model.TenderLine>): PosResult<co.zw.nissangtr.pos.domain.gateway.CheckoutResult> {
            settled += paymentRequestId
            state = "paid"; invoice = "inv-9"
            return PosResult.Ok(co.zw.nissangtr.pos.domain.gateway.CheckoutResult("inv-9", "INV-9"))
        }
        override suspend fun providerAvailability() = PosResult.Ok(co.zw.nissangtr.pos.domain.model.DigitalProvider.entries.associateWith { null as String? })
        override suspend fun startProvider(orderId: String, provider: co.zw.nissangtr.pos.domain.model.DigitalProvider, msisdn: String?, method: co.zw.nissangtr.pos.domain.model.ProviderMethod?) =
            PosResult.Ok(co.zw.nissangtr.pos.domain.model.ProviderStart("i1", null, null)).also { state = "payment_processing" }
        override suspend fun cancel(orderId: String, reason: String): PosResult<Unit> { cancelled += orderId; return PosResult.Ok(Unit) }
        override suspend fun onAccount(cartId: String, contacts: co.zw.nissangtr.pos.domain.model.ReceiptContacts) =
            PosResult.Ok(co.zw.nissangtr.pos.domain.gateway.CheckoutResult("inv-a", "INV-A"))
        override suspend fun documentNumber(invoiceId: String) = "INV-P"
        override suspend fun recovery() = PosResult.Ok(emptyList<co.zw.nissangtr.pos.domain.model.RecoveryItem>())
        override suspend fun pickups(query: String?) = PosResult.Ok(emptyList<co.zw.nissangtr.pos.domain.model.PickupOrder>())
        override suspend fun collect(orderId: String) = PosResult.Ok(Unit)
    }

    @Test
    fun `reserve first, settle once with a key, then the receipt from the reserved sale`() = runTest {
        val reserve = FakeReserve()
        val s = store(gateways(reserve = reserve))
        s.dispatch(PosIntent.Start)
        s.dispatch(PosIntent.AddPart(PosFixtures.oilFilter))
        runCurrent()
        reserve.total = s.state.value.cart.total
        s.dispatch(co.zw.nissangtr.pos.domain.state.PosSaleIntent.OpenPayment)
        runCurrent()
        assertEquals(1, reserve.prepared.size)
        assertEquals("o1", s.state.value.checkout?.orderId)
        // The reserved sale is locked.
        s.dispatch(PosIntent.AddPart(PosFixtures.brakePads))
        runCurrent()
        assertEquals(1, s.state.value.cart.lines.size)

        s.dispatch(co.zw.nissangtr.pos.domain.state.CheckoutIntent.PayManual(
            listOf(co.zw.nissangtr.pos.domain.model.TenderLine(co.zw.nissangtr.pos.domain.model.Tender.Cash, reserve.total)), null,
            co.zw.nissangtr.pos.domain.model.ReceiptContacts(null, null),
        ))
        advanceUntilIdle()
        assertEquals(1, reserve.settled.size)
        val receipt = s.state.value.receipt!!
        assertEquals("INV-9", receipt.documentNumber)
        assertEquals(1, receipt.lines.size)
        assertEquals("o1", s.state.value.receiptOrderId)
        assertNull(s.state.value.checkout)
    }

    @Test
    fun `a provider payment settles from the poll and never twice`() = runTest {
        val reserve = FakeReserve()
        val s = store(gateways(reserve = reserve))
        s.dispatch(PosIntent.Start)
        s.dispatch(PosIntent.AddPart(PosFixtures.oilFilter))
        runCurrent()
        reserve.total = s.state.value.cart.total
        s.dispatch(co.zw.nissangtr.pos.domain.state.PosSaleIntent.OpenPayment)
        runCurrent()
        s.dispatch(co.zw.nissangtr.pos.domain.state.CheckoutIntent.PayProvider(
            co.zw.nissangtr.pos.domain.model.DigitalProvider.EcoCash, "0771234567", null, co.zw.nissangtr.pos.domain.model.ReceiptContacts(null, null),
        ))
        runCurrent()
        assertTrue(s.state.value.checkout!!.inFlight)
        // The webhook settles the order; the next poll finishes the sale.
        reserve.state = "paid"; reserve.invoice = "inv-p"
        advanceUntilIdle()
        val receipt = s.state.value.receipt!!
        assertEquals("INV-P", receipt.documentNumber)
        assertEquals(co.zw.nissangtr.pos.domain.model.Tender.EcoCash, receipt.tenders.single().tender)
        assertTrue(reserve.settled.isEmpty())
        assertNull(s.state.value.checkout)
    }

    @Test
    fun `going back to the sale releases the reservation`() = runTest {
        val reserve = FakeReserve()
        val s = store(gateways(reserve = reserve))
        s.dispatch(PosIntent.Start)
        s.dispatch(PosIntent.AddPart(PosFixtures.oilFilter))
        runCurrent()
        s.dispatch(co.zw.nissangtr.pos.domain.state.PosSaleIntent.OpenPayment)
        runCurrent()
        s.dispatch(co.zw.nissangtr.pos.domain.state.PosSaleIntent.ClosePayment)
        advanceUntilIdle()
        assertEquals(listOf("o1"), reserve.cancelled)
        assertNull(s.state.value.checkout)
        assertFalse(s.state.value.paymentOpen)
    }

    /** Server stand-in for part payments: parts captured at once; posts when they cover the total. */
    private class FakeSplit(val reserve: FakeReserve) : co.zw.nissangtr.pos.domain.gateway.SplitPaymentGateway {
        val keys = mutableListOf<String>()
        var legs = listOf<co.zw.nissangtr.pos.domain.model.SplitLeg>()
        var failNextWithNetwork = false
        private fun usd(minor: Long) = Money(minor, CurrencyCode.USD)
        fun view(status: String? = null): co.zw.nissangtr.pos.domain.model.SplitSession {
            val received = legs.sumOf { it.amount.minor }
            val total = reserve.total.minor
            val posted = received >= total && total > 0
            return co.zw.nissangtr.pos.domain.model.SplitSession(
                "s1", "o1", status ?: if (posted) "settled" else if (received > 0) "partially_captured" else "open",
                usd(total), usd(received), usd(0), usd((total - received).coerceAtLeast(0)), usd((total - received).coerceAtLeast(0)),
                if (posted && status == null) "inv-split" else null, null, legs, emptyList(),
            )
        }
        override suspend fun find(orderId: String) = PosResult.Ok(null)
        override suspend fun start(orderId: String) = PosResult.Ok(view())
        override suspend fun addPart(sessionId: String, tender: co.zw.nissangtr.pos.domain.model.SplitTender, amount: Money, requestId: String, reference: String?): PosResult<co.zw.nissangtr.pos.domain.model.SplitSession> {
            keys += requestId
            if (failNextWithNetwork) { failNextWithNetwork = false; return PosResult.Err(co.zw.nissangtr.pos.domain.error.PosError.Transient(true)) }
            if (keys.count { it == requestId } == 1 || legs.none { it.id == requestId }) {
                legs = legs.filterNot { it.id == requestId } + co.zw.nissangtr.pos.domain.model.SplitLeg(requestId, legs.size + 1, tender.rpcValue, amount, "captured", reference, null, amount, null)
            }
            return PosResult.Ok(view())
        }
        override suspend fun reduceBasket(sessionId: String, items: List<Pair<String, Double>>, notes: String?) = PosResult.Ok(view())
        override suspend fun cancel(sessionId: String, reason: String, feePolicy: co.zw.nissangtr.pos.domain.model.RefundFeePolicy) = PosResult.Ok(view("cancelled"))
        override suspend fun retryFinalization(sessionId: String) = PosResult.Ok(view())
        override suspend fun recovery() = PosResult.Ok(emptyList<co.zw.nissangtr.pos.domain.model.SplitRecoveryItem>())
        override suspend fun cart(cartId: String) = PosResult.Err(co.zw.nissangtr.pos.domain.error.PosError.Transient(true))
    }

    @Test
    fun `part payments post the sale when the parts cover it, and a lost answer reuses its key`() = runTest {
        val reserve = FakeReserve()
        val split = FakeSplit(reserve)
        val s = store(gateways(reserve = reserve, split = split))
        s.dispatch(PosIntent.Start)
        s.dispatch(PosIntent.AddPart(PosFixtures.brakePads))
        runCurrent()
        reserve.total = s.state.value.cart.total
        s.dispatch(co.zw.nissangtr.pos.domain.state.PosSaleIntent.OpenPayment)
        runCurrent()
        s.dispatch(co.zw.nissangtr.pos.domain.state.SplitIntent.Start)
        runCurrent()
        assertEquals("s1", s.state.value.split?.sessionId)

        split.failNextWithNetwork = true
        s.dispatch(co.zw.nissangtr.pos.domain.state.SplitIntent.AddPart(co.zw.nissangtr.pos.domain.model.SplitTender.Cash, Money(2000, CurrencyCode.USD), null))
        runCurrent()
        val lost = s.state.value.splitPartKey
        assertEquals(split.keys.single(), lost)
        s.dispatch(co.zw.nissangtr.pos.domain.state.SplitIntent.AddPart(co.zw.nissangtr.pos.domain.model.SplitTender.Cash, Money(2000, CurrencyCode.USD), null))
        runCurrent()
        assertEquals(listOf(lost, lost), split.keys)
        assertEquals(2000L, s.state.value.split?.received?.minor)

        val rest = s.state.value.split!!.availableToAllocate
        s.dispatch(co.zw.nissangtr.pos.domain.state.SplitIntent.AddPart(co.zw.nissangtr.pos.domain.model.SplitTender.Bank, rest, "SLIP-1"))
        advanceUntilIdle()
        val receipt = s.state.value.receipt!!
        assertEquals(listOf(co.zw.nissangtr.pos.domain.model.Tender.Cash, co.zw.nissangtr.pos.domain.model.Tender.Bank), receipt.tenders.map { it.tender })
        assertEquals(reserve.total.minor, receipt.tenders.sumOf { it.amount.minor })
        assertNull(s.state.value.split)
        assertNull(s.state.value.checkout)
    }

    /** Card machine stand-in: [answer] is what the machine reports; finalize posts the sale. */
    private class FakeTerminal(val reserve: FakeReserve, var answer: String = "approved", var runFails: Boolean = false) : co.zw.nissangtr.pos.domain.gateway.CardTerminalGateway {
        val machine = co.zw.nissangtr.pos.domain.model.CardTerminal("t1", "Counter machine", "Bank", mapOf("package_name" to "p", "purchase_action" to "a"))
        val begun = mutableListOf<String>()
        var finalized = 0
        private fun a(status: String) = co.zw.nissangtr.pos.domain.model.TerminalAttempt("a1", "purchase", status, reserve.total, "Counter machine", "4242", "VISA", "T1", null, "o1", null, if (status == "settled") "inv-ct" else null, null)
        override suspend fun setup() = PosResult.Ok(co.zw.nissangtr.pos.domain.model.TerminalSetup(listOf(machine), machine, appInstalled = true, paired = true))
        override suspend fun select(terminalId: String) = setup()
        override suspend fun pair(terminalId: String, admin: co.zw.nissangtr.pos.domain.model.ManagerCredentials?) = setup()
        override suspend fun beginPurchase(orderId: String, terminalId: String, requestId: String): PosResult<co.zw.nissangtr.pos.domain.model.TerminalAttempt> { begun += requestId; return PosResult.Ok(a("initiated")) }
        override suspend fun beginSplitPart(sessionId: String, amount: Money, terminalId: String, requestId: String) = PosResult.Ok(a("initiated"))
        override suspend fun run(attempt: co.zw.nissangtr.pos.domain.model.TerminalAttempt, statusOnly: Boolean): PosResult<co.zw.nissangtr.pos.domain.model.TerminalAttempt> =
            if (runFails && !statusOnly) PosResult.Err(co.zw.nissangtr.pos.domain.error.PosError.Transient(true)) else PosResult.Ok(a(answer))
        override suspend fun finalize(attemptId: String): PosResult<co.zw.nissangtr.pos.domain.model.TerminalAttempt> { finalized++; return PosResult.Ok(a("settled")) }
        override suspend fun beginReversal(purchaseAttemptId: String, requestId: String) = PosResult.Ok(a("initiated").copy(operation = "reversal"))
        override suspend fun attempt(attemptId: String) = PosResult.Ok(a(answer))
        override suspend fun recovery() = PosResult.Ok(emptyList<co.zw.nissangtr.pos.domain.model.TerminalRecoveryItem>())
    }

    private suspend fun kotlinx.coroutines.test.TestScope.cardSale(terminal: FakeTerminal, reserve: FakeReserve): PosStore {
        val s = store(gateways(reserve = reserve, terminal = terminal))
        s.dispatch(PosIntent.Start)
        s.dispatch(PosIntent.AddPart(PosFixtures.oilFilter))
        runCurrent()
        reserve.total = s.state.value.cart.total
        s.dispatch(co.zw.nissangtr.pos.domain.state.PosSaleIntent.OpenPayment)
        runCurrent()
        return s
    }

    @Test
    fun `a card machine approval posts the sale once and prints a card receipt`() = runTest {
        val reserve = FakeReserve()
        val terminal = FakeTerminal(reserve)
        val s = cardSale(terminal, reserve)
        s.dispatch(co.zw.nissangtr.pos.domain.state.TerminalIntent.Pay)
        advanceUntilIdle()
        assertEquals(1, terminal.begun.size)
        assertEquals(1, terminal.finalized)
        val receipt = s.state.value.receipt!!
        assertEquals(co.zw.nissangtr.pos.domain.model.Tender.Bank, receipt.tenders.single().tender)
        assertNull(s.state.value.checkout)
    }

    @Test
    fun `a lost card machine answer is Unknown and is resolved by asking the machine again`() = runTest {
        val reserve = FakeReserve()
        val terminal = FakeTerminal(reserve, runFails = true)
        val s = cardSale(terminal, reserve)
        s.dispatch(co.zw.nissangtr.pos.domain.state.TerminalIntent.Pay)
        runCurrent()
        assertEquals(co.zw.nissangtr.pos.domain.model.TenderOutcome.Unknown, s.state.value.checkout?.outcome)
        assertEquals(0, terminal.finalized)
        // Paying again is blocked; checking on the machine finds the approval and posts the sale.
        s.dispatch(co.zw.nissangtr.pos.domain.state.TerminalIntent.Pay)
        runCurrent()
        assertEquals(1, terminal.begun.size)
        s.dispatch(co.zw.nissangtr.pos.domain.state.TerminalIntent.CheckOnMachine(s.state.value.terminalAttempt!!))
        advanceUntilIdle()
        assertEquals(1, terminal.finalized)
        assertTrue(s.state.value.receipt != null)
    }
}
