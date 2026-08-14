package co.zw.nissangtr.management.fleet

import co.zw.nissangtr.management.rpc.FakeRpcClient
import co.zw.nissangtr.management.rpc.FleetVehicleStatus
import co.zw.nissangtr.management.rpc.RpcNames
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FleetFakeRpcParityTest {

    @Test
    fun listUpsertStatusAndRpcNames() = runBlocking {
        val rpc = FakeRpcClient()

        val seeded = rpc.listFleetVehicles()
        assertTrue(seeded.any { it.id == FakeRpcClient.FAKE_FLEET_VEHICLE_ID })

        val id = rpc.upsertFleetVehicle(
            plate = "  xy-99  ",
            label = "Van Beta",
            status = FleetVehicleStatus.ACTIVE,
            assignedDriverUserId = FakeRpcClient.FAKE_DRIVER_USER_ID,
            notes = "unit",
        )
        val created = rpc.listFleetVehicles().first { it.id == id }
        assertEquals("XY-99", created.plate)
        assertEquals("Van Beta", created.label)

        rpc.setFleetVehicleStatus(id, FleetVehicleStatus.IN_SERVICE)
        assertEquals(
            FleetVehicleStatus.IN_SERVICE,
            rpc.listFleetVehicles().first { it.id == id }.status,
        )

        rpc.upsertFleetVehicle(
            plate = "XY-99",
            label = "Van Beta renamed",
            status = FleetVehicleStatus.IN_SERVICE,
            assignedDriverUserId = null,
            notes = null,
            id = id,
        )
        val updated = rpc.listFleetVehicles().first { it.id == id }
        assertEquals("Van Beta renamed", updated.label)
        assertEquals(null, updated.assignedDriverUserId)

        val activeOnly = rpc.listFleetVehicles(FleetVehicleStatus.ACTIVE)
        assertTrue(activeOnly.all { it.status == FleetVehicleStatus.ACTIVE })

        assertEquals("list_fleet_vehicles", RpcNames.LIST_FLEET_VEHICLES)
        assertEquals("upsert_fleet_vehicle", RpcNames.UPSERT_FLEET_VEHICLE)
        assertEquals("set_fleet_vehicle_status", RpcNames.SET_FLEET_VEHICLE_STATUS)
    }

    @Test
    fun duplicatePlateAndNonDriverAssigneeDenied() = runBlocking {
        val rpc = FakeRpcClient()
        rpc.upsertFleetVehicle(plate = "DUP-1", label = "A")

        try {
            rpc.upsertFleetVehicle(plate = "dup-1", label = "B")
            error("expected duplicate plate")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("already exists"))
        }

        try {
            rpc.upsertFleetVehicle(
                plate = "BAD-DRV",
                assignedDriverUserId = FakeRpcClient.FAKE_CUSTOMER_ID,
            )
            error("expected non-driver deny")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("driver"))
        }
    }
}
