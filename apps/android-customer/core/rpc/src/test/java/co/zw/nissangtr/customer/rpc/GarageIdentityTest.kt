package co.zw.nissangtr.customer.rpc

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class GarageIdentityTest {
    @Test fun rpcPayloadRetainsCanonicalIdentityWithoutInventingSlugOrCustomerId() {
        val body = GarageVehicleInput(
            model = "Navara", generation = "D40", chassisCode = "D40",
            vehicleMasterId = "nissan/navara/d40-yd25", isPrimary = true,
        ).toRpcBody()
        assertEquals("D40", body["p_chassis_code"]?.jsonPrimitive?.content)
        assertEquals("nissan/navara/d40-yd25", body["p_vehicle_master_id"]?.jsonPrimitive?.content)
        assertEquals(JsonNull, body["p_model_slug"])
        assertFalse(body.containsKey("p_customer_id"))
        assertFalse(body.containsKey("p_profile_id"))
    }

    @Test fun explicitModelSlugIsPreserved() {
        val body = GarageVehicleInput(modelSlug = " navara-d40 ").toRpcBody()
        assertEquals("navara-d40", body["p_model_slug"]?.jsonPrimitive?.content)
    }

    @Test fun debugGarageRoundTripKeepsPublishedVehicleKey() = runBlocking {
        val rpc = FakeRpcClient()
        val id = rpc.upsertCustomerGarageVehicle(GarageVehicleInput(
            model = "Navara", generation = "D40", chassisCode = "D40",
            modelSlug = "navara-d40", vehicleMasterId = "nissan/navara/d40-yd25", isPrimary = true,
        ))
        val row = rpc.listGarageVehicles().single { it.id == id }
        assertEquals("D40", row.chassisCode)
        assertEquals("navara-d40", row.modelSlug)
        assertEquals("nissan/navara/d40-yd25", row.vehicleMasterId)
        assertEquals(1, rpc.listGarageVehicles().count { it.isPrimary })
    }
    @Test fun legacyUpdateDoesNotErasePreviouslySavedIdentity() = runBlocking {
        val rpc = FakeRpcClient()
        val id = rpc.upsertCustomerGarageVehicle(GarageVehicleInput(
            model = "Navara", generation = "D40", chassisCode = "D40",
            modelSlug = "navara-d40", vehicleMasterId = "nissan/navara/d40-yd25",
        ))
        rpc.upsertCustomerGarageVehicle(GarageVehicleInput(id = id, vin = "MNTCCND40FIXTURE"))
        val row = rpc.listGarageVehicles().single { it.id == id }
        assertEquals("D40", row.chassisCode)
        assertEquals("navara-d40", row.modelSlug)
        assertEquals("nissan/navara/d40-yd25", row.vehicleMasterId)
    }

}
