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
        override suspend fun searchLocal(query: String) =
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
        sales = FakeSaleGateways.sales,
        epc = FakeSaleGateways.epc,
        offline = outbox,
        companion = companion,
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
}
