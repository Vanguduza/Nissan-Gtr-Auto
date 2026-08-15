package co.zw.nissangtr.pos.lookup

import co.zw.nissangtr.pos.api.FakePosClient
import co.zw.nissangtr.pos.api.ListTillItemsRequest
import co.zw.nissangtr.pos.api.TillItemsSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Price-check path uses listTillItems / local find only — never add_cart_line.
 */
class PriceCheckNoAddTest {
    @Test
    fun priceCheckLookup_doesNotRequireAddCartLine() = runTest {
        val client = FakePosClient()
        val before = client.fakeTillState().ticket.itemCount
        val items = client.listTillItems(
            ListTillItemsRequest(
                warehouseId = "wh2-fake",
                source = TillItemsSource.OEMS,
                inStockOnly = false,
                oems = listOf("40206-JF00A"),
            ),
        )
        assertEquals(1, items.size)
        assertTrue(items.single().binCode != null)
        // Ticket unchanged — price-check never mutates cart.
        assertEquals(before, client.fakeTillState().ticket.itemCount)
    }
}
