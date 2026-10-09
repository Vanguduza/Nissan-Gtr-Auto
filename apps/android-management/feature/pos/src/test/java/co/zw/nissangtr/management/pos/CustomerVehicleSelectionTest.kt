package co.zw.nissangtr.management.pos

import co.zw.nissangtr.management.rpc.CustomerGarageVehicle
import org.junit.Assert.assertEquals
import org.junit.Test

class CustomerVehicleSelectionTest {
    private fun vehicle(id: String) = CustomerGarageVehicle(
        id = id,
        customerId = "customer-1",
        make = "Nissan",
        modelSlug = "gt-r",
        model = "GT-R",
        generation = "R35",
        chassisCode = "R35",
        engine = "VR38DETT",
    )

    @Test fun noGarageVehicle_keepsManualCascade() {
        assertEquals(CustomerVehicleSelectionAction.NONE, customerVehicleSelectionAction(emptyList()))
    }

    @Test fun oneGarageVehicle_autoSelects() {
        assertEquals(CustomerVehicleSelectionAction.AUTO_SELECT, customerVehicleSelectionAction(listOf(vehicle("v1"))))
    }

    @Test fun multipleGarageVehicles_requiresPicker() {
        assertEquals(
            CustomerVehicleSelectionAction.CHOOSE,
            customerVehicleSelectionAction(listOf(vehicle("v1"), vehicle("v2"))),
        )
    }
}
