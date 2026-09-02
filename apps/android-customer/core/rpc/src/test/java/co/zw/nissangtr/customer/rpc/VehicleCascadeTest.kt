package co.zw.nissangtr.customer.rpc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleCascadeTest {
    @Test
    fun deriveMaker_brandPrefix() {
        assertEquals(
            "Nissan",
            VehicleCascade.deriveMaker(row(modelVariant = "Nissan Navara D40 · YD25")),
        )
    }

    @Test
    fun deriveMaker_thaiWmiWithoutBrandPrefix() {
        assertEquals(
            "Nissan",
            VehicleCascade.deriveMaker(
                row(vinPrefix = "MNTCCND40", modelVariant = "NAVARA"),
            ),
        )
    }

    @Test
    fun deriveMaker_bareNissanModelToken() {
        assertEquals(
            "Nissan",
            VehicleCascade.deriveMaker(row(modelVariant = "GT-R R35")),
        )
    }

    @Test
    fun makers_includeNissanFromMixedRows() {
        val makers = VehicleCascade.makers(
            listOf(
                row(vinPrefix = "MNTCCND40", chassisCode = "D40", modelVariant = "NAVARA"),
                row(vinPrefix = "JN1AR5EF", chassisCode = "R35", modelVariant = "GT-R"),
            ),
        )
        assertEquals(listOf("Nissan"), makers)
    }

    @Test
    fun makers_emptyWhenUnidentifiable() {
        assertTrue(
            VehicleCascade.makers(
                listOf(row(vinPrefix = "XXXX", modelVariant = "Unknown")),
            ).isEmpty(),
        )
    }

    private fun row(
        vinPrefix: String? = null,
        chassisCode: String = "R35",
        modelVariant: String,
    ) = VehicleMasterRow(
        vinPrefix = vinPrefix,
        chassisCode = chassisCode,
        modelVariant = modelVariant,
    )
}
