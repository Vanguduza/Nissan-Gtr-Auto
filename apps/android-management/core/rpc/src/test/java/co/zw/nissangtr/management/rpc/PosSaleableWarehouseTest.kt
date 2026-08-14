package co.zw.nissangtr.management.rpc

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** H-PARITY-WH2: POS picker must exclude WH1 receiving / quarantine. */
class PosSaleableWarehouseTest {

    @Test
    fun roleCodeWh2_isSaleable() {
        assertTrue(
            isPosSaleableWarehouse(
                WarehouseRef("1", "STORE", "Floor", roleCode = "WH2"),
            ),
        )
    }

    @Test
    fun legacyCodeWh2_isSaleableWhenRoleMissing() {
        assertTrue(
            isPosSaleableWarehouse(
                WarehouseRef("1", "WH2", "Legacy floor", roleCode = null),
            ),
        )
    }

    @Test
    fun wh1Receiving_notSaleable() {
        assertFalse(
            isPosSaleableWarehouse(
                WarehouseRef("1", "WH1", "Receiving", roleCode = "WH1"),
            ),
        )
        assertFalse(
            isPosSaleableWarehouse(
                WarehouseRef("1", "MAIN", "Main", roleCode = null),
            ),
        )
    }

    @Test
    fun quarantineOrInactive_notSaleable() {
        assertFalse(
            isPosSaleableWarehouse(
                WarehouseRef("1", "WH2", "Q", roleCode = "WH2", isQuarantine = true),
            ),
        )
        assertFalse(
            isPosSaleableWarehouse(
                WarehouseRef("1", "WH2", "Off", roleCode = "WH2", isActive = false),
            ),
        )
    }

    @Test
    fun fakeListSaleable_excludesWh1() = runBlocking {
        val fake = FakeRpcClient()
        val all = fake.listWarehouses()
        val saleable = fake.listSaleableWarehouses()
        assertTrue(all.any { it.roleCode == "WH1" || it.code == "WH1" })
        assertEquals(1, saleable.size)
        assertTrue(saleable.all(::isPosSaleableWarehouse))
        assertEquals(FakeRpcClient.FAKE_WAREHOUSE_ID, saleable.single().id)
        assertEquals("WH2", saleable.single().roleCode)
    }
}
