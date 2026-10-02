package co.zw.nissangtr.pos.domain

import co.zw.nissangtr.pos.domain.error.PosError
import co.zw.nissangtr.pos.domain.model.CatalogPart
import co.zw.nissangtr.pos.domain.model.Category
import co.zw.nissangtr.pos.domain.model.CurrencyCode
import co.zw.nissangtr.pos.domain.model.Money
import co.zw.nissangtr.pos.domain.model.PinKind
import co.zw.nissangtr.pos.domain.model.PopularPin
import co.zw.nissangtr.pos.domain.model.PopularRowItem
import co.zw.nissangtr.pos.domain.model.VehicleGeneration
import co.zw.nissangtr.pos.domain.model.VehicleModel
import co.zw.nissangtr.pos.domain.state.PosDestination
import co.zw.nissangtr.pos.domain.state.PosEffect
import co.zw.nissangtr.pos.domain.state.PosEvent
import co.zw.nissangtr.pos.domain.state.PosFeedback
import co.zw.nissangtr.pos.domain.state.PosIntent
import co.zw.nissangtr.pos.domain.state.PosState
import co.zw.nissangtr.pos.domain.state.Rollback
import co.zw.nissangtr.pos.domain.state.reduce
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PosReducerTest {

    private fun part(oem: String, id: String? = "id-$oem", price: Double? = 10.0) = CatalogPart(
        stockItemId = id,
        oemPartNumber = oem,
        name = "Part $oem",
        price = price?.let { Money.ofMajor(it, CurrencyCode.USD) },
        saleableQty = 3.0,
        imageUrl = null,
    )

    private val gtr = VehicleModel("gt-r", "GT-R")
    private val r35 = VehicleGeneration("R35", "R35")

    @Test
    fun `start loads operator, models, popular row and syncs the offline outbox`() {
        val r = reduce(PosState(), PosIntent.Start)
        assertEquals(
            listOf(PosEffect.LoadOperator, PosEffect.LoadModels, PosEffect.LoadPopular, co.zw.nissangtr.pos.domain.state.PosSaleEffect.SyncOffline),
            r.effects,
        )
    }

    @Test
    fun `reducer is deterministic`() {
        val s = PosState(searchQuery = "brake")
        assertEquals(reduce(s, PosIntent.SubmitSearch), reduce(s, PosIntent.SubmitSearch))
    }

    @Test
    fun `a chassis with no engine codes completes the vehicle on the generation`() {
        val skyline = VehicleModel("skyline", "Skyline")
        val r33 = VehicleGeneration("ENR33", "ENR33")
        var s = reduce(PosState(), PosIntent.PickModel(skyline)).state
        s = reduce(s, PosIntent.PickGeneration(r33)).state
        assertNull(s.vehicle)
        s = reduce(s, PosEvent.EnginesLoaded(r33, emptyList())).state
        assertEquals("Skyline ENR33", s.vehicle?.label)
        assertEquals("", s.vehicle?.engineCode)
    }

    @Test
    fun `cascade selecting a level resets levels to its right`() {
        var s = reduce(PosState(), PosIntent.PickModel(gtr)).state
        s = reduce(s, PosEvent.GenerationsLoaded(gtr, listOf(r35))).state
        s = reduce(s, PosIntent.PickGeneration(r35)).state
        s = reduce(s, PosEvent.EnginesLoaded(r35, listOf("VR38DETT"))).state
        s = reduce(s, PosIntent.PickEngine("VR38DETT")).state
        assertEquals("GT-R R35 VR38DETT", s.vehicle?.label)

        val other = VehicleModel("navara", "Navara")
        s = reduce(s, PosIntent.PickModel(other)).state
        assertEquals(other, s.cascade.model)
        assertNull(s.cascade.generation)
        assertNull(s.cascade.engine)
        assertTrue(s.cascade.generations.isEmpty())
        assertNull(s.vehicle)
    }

    @Test
    fun `late cascade responses for a superseded level are ignored`() {
        var s = reduce(PosState(), PosIntent.PickModel(gtr)).state
        s = reduce(s, PosIntent.PickModel(VehicleModel("navara", "Navara"))).state
        s = reduce(s, PosEvent.GenerationsLoaded(gtr, listOf(r35))).state
        assertTrue(s.cascade.generations.isEmpty())
    }

    @Test
    fun `generation cannot be picked before a model`() {
        val r = reduce(PosState(), PosIntent.PickGeneration(r35))
        assertNull(r.state.cascade.generation)
        assertTrue(r.effects.isEmpty())
    }

    @Test
    fun `search records recent queries, newest first, de-duplicated and bounded`() {
        var s = PosState()
        listOf("brake", "filter", "BRAKE").forEach { s = reduce(s, PosIntent.SearchFor(it)).state }
        assertEquals(listOf("BRAKE", "filter"), s.recentSearches)
        repeat(20) { s = reduce(s, PosIntent.SearchFor("q$it")).state }
        assertEquals(PosState.RECENT_SEARCH_LIMIT, s.recentSearches.size)
    }

    @Test
    fun `search goes to Search Spares with the fitment context`() {
        var s = PosState()
        s = reduce(s, PosIntent.PickModel(gtr)).state
        s = reduce(s, PosIntent.PickGeneration(r35)).state
        s = reduce(s, PosIntent.PickEngine("VR38DETT")).state
        val r = reduce(s, PosIntent.OpenCategory(Category("brakes", "Brakes", "brake")))
        assertEquals(PosDestination.SearchSpares, r.state.destination)
        assertEquals(listOf(PosEffect.Search("brake", s.vehicle)), r.effects)
    }

    @Test
    fun `stale search results are dropped`() {
        var s = reduce(PosState(), PosIntent.SearchFor("brake")).state
        s = reduce(s, PosIntent.SearchFor("filter")).state
        s = reduce(s, PosEvent.SearchLoaded("brake", listOf(part("A")))).state
        assertNull(s.searchResults)
        s = reduce(s, PosEvent.SearchLoaded("filter", listOf(part("B")))).state
        assertEquals(listOf("B"), s.searchResults?.map { it.oemPartNumber })
    }

    @Test
    fun `parts without a price or stock id cannot be added`() {
        val r = reduce(PosState(), PosIntent.AddPart(part("X", price = null)))
        assertTrue(r.effects.isEmpty())
        assertTrue(r.state.feedback is PosFeedback.Failure)
    }

    @Test
    fun `adding a sellable part emits one cart effect and marks the cart busy`() {
        val r = reduce(PosState(), PosIntent.AddPart(part("A")))
        assertEquals(1, r.state.cartBusy)
        assertTrue(r.effects.single() is PosEffect.AddToCart)
        assertEquals(0, reduce(r.state, PosEvent.CartMutationFinished).state.cartBusy)
    }

    @Test
    fun `popular row is pins then best sellers minus pinned and hidden (D1)`() {
        val a = part("A")
        val b = part("B")
        val c = part("C")
        var s = PosState()
        s = reduce(s, PosEvent.BestSellersLoaded(listOf(a, b, c))).state
        s = reduce(s, PosIntent.Pin(PopularPin.forPart(b))).state
        s = reduce(s, PosIntent.HideBestSeller(c)).state
        val keys = s.popularRow.map { it.stableKey }
        assertEquals(listOf("pin:part:B", "seller:id-A"), keys)
        assertTrue(s.popularRow.first() is PopularRowItem.Pinned)
    }

    @Test
    fun `failed pin is rolled back and reported, never swallowed`() {
        val pin = PopularPin.forPart(part("A"))
        var s = reduce(PosState(), PosIntent.Pin(pin)).state
        s = reduce(s, PosEvent.Failed(PosError.Transient(retryable = true), Rollback.RemovePinAdded(pin))).state
        assertTrue(s.pins.isEmpty())
        assertTrue(s.feedback is PosFeedback.Failure)
    }

    @Test
    fun `failed unpin restores the pin at its position`() {
        val p1 = PopularPin.forPart(part("A"))
        val p2 = PopularPin.forPart(part("B"))
        val p3 = PopularPin.forPart(part("C"))
        var s = PosState(pins = listOf(p1, p2, p3))
        val r = reduce(s, PosIntent.Unpin(p2))
        assertEquals(listOf(PosEffect.PersistUnpin(p2, 1)), r.effects)
        s = reduce(r.state, PosEvent.Failed(PosError.Transient(true), Rollback.RestorePinRemoved(p2, 1))).state
        assertEquals(listOf(p1, p2, p3), s.pins)
    }

    @Test
    fun `failed hide brings the best seller back`() {
        val a = part("A")
        var s = reduce(PosState(bestSellers = listOf(a)), PosIntent.HideBestSeller(a)).state
        assertTrue(s.popularRow.isEmpty())
        s = reduce(s, PosEvent.Failed(PosError.Transient(true), Rollback.UnhideBestSeller("id-A"))).state
        assertEquals(1, s.popularRow.size)
    }

    @Test
    fun `pinning twice is a no-op`() {
        val pin = PopularPin.forPart(part("A"))
        val s = reduce(PosState(), PosIntent.Pin(pin)).state
        val r = reduce(s, PosIntent.Pin(pin))
        assertEquals(1, r.state.pins.size)
        assertTrue(r.effects.isEmpty())
    }

    @Test
    fun `activating a vehicle pin searches for it`() {
        val pin = PopularPin(PinKind.MODEL, "gt-r:R35:VR38DETT", "GT-R R35", "VR38DETT", "GT-R R35 VR38DETT")
        val r = reduce(PosState(), PosIntent.ActivatePin(pin))
        assertEquals(PosDestination.SearchSpares, r.state.destination)
        assertEquals("GT-R R35 VR38DETT", r.state.searchQuery)
    }

    @Test
    fun `changing the vehicle on an open cart updates the cart vehicle`() {
        var s = PosState(cart = co.zw.nissangtr.pos.domain.model.CartProjection.empty(CurrencyCode.USD).copy(cartId = "c1"))
        s = reduce(s, PosIntent.PickModel(gtr)).state
        s = reduce(s, PosIntent.PickGeneration(r35)).state
        val r = reduce(s, PosIntent.PickEngine("VR38DETT"))
        assertTrue(r.effects.any { it is PosEffect.SetCartVehicle && it.vehicle?.engineCode == "VR38DETT" })
        val cleared = reduce(r.state, PosIntent.ClearVehicle)
        assertTrue(cleared.effects.any { it is PosEffect.SetCartVehicle && it.vehicle == null })
    }
}
