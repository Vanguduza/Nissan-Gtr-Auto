package co.zw.nissangtr.pos.lookup

import co.zw.nissangtr.pos.api.OemNormalize
import co.zw.nissangtr.pos.api.PartHit
import co.zw.nissangtr.pos.api.PncHit
import co.zw.nissangtr.pos.api.VehicleHit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** §16.2 / §16.7 hit router: part→oems, vehicle→latch, pnc→section. */
class HitRouterTest {

    @Test
    fun part_hydratesOems() {
        val actions = HitRouter.route(
            listOf(
                PartHit(oemPartNumber = "40206-jf00a"),
                PartHit(oemPartNumber = "40206-JF01B"),
            ),
        )
        assertEquals(1, actions.size)
        val hydrate = actions.single() as HitRouteAction.HydrateOems
        assertEquals(listOf("40206-JF00A", "40206-JF01B"), hydrate.oems)
    }

    @Test
    fun vehicle_latchesAndSwitchesShopStock() {
        val actions = HitRouter.route(
            listOf(
                VehicleHit(
                    chassisCode = "r35",
                    engineCode = "vr38dett",
                    modelVariant = "GT-R",
                ),
            ),
        )
        val latch = actions.single() as HitRouteAction.LatchVehicle
        assertEquals("R35", latch.latch.chassisCode)
        assertEquals("VR38DETT", latch.latch.engineCode)
        assertTrue(latch.switchToShopStock)
    }

    @Test
    fun pnc_opensEpcSection() {
        val actions = HitRouter.route(listOf(PncHit(pncCode = "40206")))
        val open = actions.single() as HitRouteAction.OpenEpcSection
        assertEquals("40206", open.pncCode)
        assertTrue(open.switchToEpc)
    }

    @Test
    fun mixed_partThenVehicle() {
        val actions = HitRouter.route(
            listOf(
                PartHit(oemPartNumber = "40206-JF00A"),
                VehicleHit(chassisCode = "R35", engineCode = "VR38DETT"),
            ),
        )
        assertTrue(actions[0] is HitRouteAction.HydrateOems)
        assertTrue(actions[1] is HitRouteAction.LatchVehicle)
    }

    @Test
    fun oemNormalize_viaBridgeStub() {
        assertEquals("40206-JF00A", BridgeScanStub.onScanPayload(" 40206-jf00a "))
        assertEquals("40206JF00A", OemNormalize.normalize("40206 JF00A"))
    }
}
