package co.zw.nissangtr.pos.till

import co.zw.nissangtr.pos.api.FakePosClient
import co.zw.nissangtr.pos.api.TicketCta
import co.zw.nissangtr.pos.api.TillItem
import co.zw.nissangtr.pos.api.VehicleLatch
import co.zw.nissangtr.pos.lookup.FinderMode
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * §16.7 CTA: sellable→PAY, quote-only→QUOTE, unpriced→no add.
 * Also core-charge child + latch re-badge without refetch.
 */
class TicketCtaAndAddTest {

    private val latch = VehicleLatch(chassisCode = "R35", engineCode = "VR38DETT")

    @Test
    fun sellableAdd_ctaPay_coreChargeChild() = runTest {
        val client = FakePosClient()
        client.createCart("wh2-fake")
        val session = TillSession(client, TillLayoutMode.Expanded)
        session.latchForTest(latch)

        val pad = TillItem(
            stockItemId = "stock-pad-r35",
            oemPartNumber = "40206-JF00A",
            description = "Pad kit",
            unitPrice = 190.0,
            coreCharge = 22.0,
            currency = "USD",
            saleableQty = 4,
            chassisCodes = listOf("R35"),
            engineCodes = listOf("VR38DETT"),
        )
        assertNull(session.tryAddTile(pad, autoConfirmVerify = true))
        assertEquals(TicketCta.PAY, session.cta)
        val lines = session.state.fake.ticket.lines
        assertTrue(lines.any { !it.isCoreCharge })
        assertTrue(lines.any { it.isCoreCharge && it.parentLineId != null })
        assertEquals(212.0, session.state.fake.ticket.subtotal, 0.001)
    }

    @Test
    fun quoteOnlyOos_ctaQuote() = runTest {
        val client = FakePosClient()
        client.createCart("wh2-fake")
        val session = TillSession(client, TillLayoutMode.Expanded)
        session.latchForTest(latch)

        val oos = TillItem(
            oemPartNumber = "99999-OOS0A",
            description = "OOS",
            unitPrice = 15.0,
            currency = "USD",
            saleableQty = 0,
            chassisCodes = listOf("R35"),
            engineCodes = listOf("VR38DETT"),
        )
        assertNull(session.tryAddTile(oos))
        assertEquals(TicketCta.QUOTE, session.cta)
        assertTrue(session.state.fake.ticket.lines.any { it.isQuoteOnly })
    }

    @Test
    fun unpriced_noAdd() = runTest {
        val client = FakePosClient()
        client.createCart("wh2-fake")
        val session = TillSession(client, TillLayoutMode.Expanded)
        session.latchForTest(latch)
        val before = session.state.fake.ticket.lines.size

        val unpriced = TillItem(
            oemPartNumber = "11111-NOP0A",
            description = "No price",
            unitPrice = null,
            currency = "USD",
            saleableQty = 3,
            chassisCodes = listOf("R35"),
        )
        val err = session.tryAddTile(unpriced)
        assertEquals("Needs price", err)
        assertEquals(before, session.state.fake.ticket.lines.size)
    }

    @Test
    fun noFit_cannotSilentAdd() = runTest {
        val client = FakePosClient()
        client.createCart("wh2-fake")
        val session = TillSession(client, TillLayoutMode.Expanded)
        session.latchForTest(latch)

        val y62 = TillItem(
            oemPartNumber = "41060-1LA0A",
            description = "Y62",
            unitPrice = 98.0,
            currency = "USD",
            saleableQty = 6,
            chassisCodes = listOf("Y62"),
        )
        assertNotNull(session.tryAddTile(y62))
        assertTrue(session.state.fake.ticket.lines.isEmpty())
    }

    @Test
    fun vehicleHit_latchesShopStock() = runTest {
        val client = FakePosClient()
        val session = TillSession(client, TillLayoutMode.Expanded)
        session.clearLatch()
        session.applySearchHits(
            co.zw.nissangtr.pos.api.CatalogSearchMode.VIN,
            "JN1AR5EF0AM000001",
        )
        assertEquals("R35", session.state.latch?.chassisCode)
        assertEquals(FinderMode.SHOP_STOCK, session.state.finderMode)
    }

    @Test
    fun latchChange_rebadsWithoutRefetch() {
        val client = FakePosClient()
        val session = TillSession(client, TillLayoutMode.Expanded)
        val tile = session.state.tiles.first { it.oemPartNumber == "41060-1LA0A" }
        session.latchForTest(latch)
        assertEquals(
            co.zw.nissangtr.pos.api.FitmentBadge.NO_FIT,
            session.badgeFor(tile),
        )
        session.latchForTest(VehicleLatch(chassisCode = "Y62"))
        assertEquals(
            co.zw.nissangtr.pos.api.FitmentBadge.FITS,
            session.badgeFor(tile),
        )
        // Same tile object arrays — no refetch required
        assertEquals(listOf("Y62"), tile.chassisCodes)
    }

    @Test
    fun quoteCta_createsQtAndParks() = runTest {
        val client = FakePosClient()
        client.createCart("wh2-fake")
        val session = TillSession(client, TillLayoutMode.Expanded)
        session.latchForTest(latch)
        session.tryAddTile(
            TillItem(
                oemPartNumber = "99999-OOS0A",
                description = "OOS",
                unitPrice = 15.0,
                currency = "USD",
                saleableQty = 0,
                chassisCodes = listOf("R35"),
                engineCodes = listOf("VR38DETT"),
            ),
        )
        assertEquals(TicketCta.QUOTE, session.cta)
        assertNull(session.runQuoteCta())
        assertTrue(session.state.banner?.startsWith("Created QT-") == true)
        assertTrue(session.state.fake.ticket.lines.isEmpty())
        assertEquals(TicketCta.PAY, session.cta)
    }
}
