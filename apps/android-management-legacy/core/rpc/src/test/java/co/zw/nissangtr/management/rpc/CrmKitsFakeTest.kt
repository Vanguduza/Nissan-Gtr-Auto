package co.zw.nissangtr.management.rpc

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Fake CRM kits — create ≥2 components; toggle active. */
class CrmKitsFakeTest {
    @Test
    fun createKitRequiresTwoComponents() = runBlocking {
        val rpc = FakeRpcClient()
        val a = FakeRpcClient.FAKE_STOCK_ITEM_ID
        val b = "00000000-0000-4000-8000-0000000000i2"
        val id = rpc.createKitWithComponents(
            oem = "KIT-TEST-1",
            title = "Brake service",
            componentItemIds = listOf(a, b),
        )
        assertTrue(id.isNotBlank())
        val kits = rpc.listStaffKits()
        assertEquals(1, kits.size)
        assertEquals("KIT-TEST-1", kits[0].oem)
        assertEquals(2, kits[0].components.size)
        assertTrue(kits[0].isActive)

        rpc.updateItemKit(kitId = id, isActive = false)
        assertEquals(false, rpc.listStaffKits().first().isActive)
    }

    @Test(expected = IllegalArgumentException::class)
    fun createKitRejectsSingleComponent() = runBlocking {
        val rpc = FakeRpcClient()
        rpc.createKitWithComponents(
            oem = "KIT-BAD",
            title = "Incomplete",
            componentItemIds = listOf(FakeRpcClient.FAKE_STOCK_ITEM_ID),
        )
        Unit
    }
}
