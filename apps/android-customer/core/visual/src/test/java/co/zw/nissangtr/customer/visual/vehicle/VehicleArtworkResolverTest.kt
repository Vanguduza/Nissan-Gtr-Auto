package co.zw.nissangtr.customer.visual.vehicle

import co.zw.nissangtr.customer.rpc.SelectedFitmentVehicle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VehicleArtworkResolverTest {

    private fun vehicle(model: String, generation: String) =
        SelectedFitmentVehicle(
            make = "Nissan",
            model = model,
            generation = generation,
            engine = null,
        )

    @Test fun xTrailT32() {
        assertEquals("X-Trail T32",
            VehicleArtworkResolver.resolve(vehicle("Nissan X-Trail T32", "T32"))?.label)
    }

    @Test fun navaraNp300D23PrefersD23Artwork() {
        assertEquals("Navara D23",
            VehicleArtworkResolver.resolve(vehicle("Nissan Navara NP300 D23", "D23"))?.label)
    }

    @Test fun navaraHardbodyD22() {
        assertEquals("Navara D22",
            VehicleArtworkResolver.resolve(vehicle("Nissan Navara / Hardbody D22", "D22"))?.label)
    }

    @Test fun gtrR35() {
        assertEquals("GT-R R35",
            VehicleArtworkResolver.resolve(vehicle("Nissan GT-R R35", "R35"))?.label)
    }

    @Test fun silviaS14CombinedVariant() {
        assertEquals("Silvia S14",
            VehicleArtworkResolver.resolve(vehicle("Nissan 200SX / Silvia S14", "S14"))?.label)
    }

    @Test fun z33CombinedVariantUses350zArtwork() {
        assertEquals("350Z",
            VehicleArtworkResolver.resolve(vehicle("Nissan 350Z / Fairlady Z Z33", "Z33"))?.label)
    }

    @Test fun micraK13UsesMarchMicraFamilyArtwork() {
        assertEquals("March Micra",
            VehicleArtworkResolver.resolve(vehicle("Nissan Micra K13", "K13"))?.label)
    }

    @Test fun unknownNissanDoesNotLieWithWrongArtwork() {
        assertNull(VehicleArtworkResolver.resolve(vehicle("Nissan Qashqai J11", "J11")))
    }

    @Test fun otherMakeDoesNotResolveNissanArtwork() {
        val v = SelectedFitmentVehicle("Toyota", "Prius", "ZVW30", null)
        assertNull(VehicleArtworkResolver.resolve(v))
    }
}
