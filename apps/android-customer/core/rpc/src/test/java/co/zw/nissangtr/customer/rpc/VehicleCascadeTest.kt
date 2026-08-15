package co.zw.nissangtr.customer.rpc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Parity with web `VehicleCascade.deriveMaker` — multi-make + regional Nissan WMIs. */
class VehicleCascadeTest {

    @Test
    fun deriveMaker_bareNavara_and_mntWmi_are_nissan() {
        val bare = VehicleMasterRow(
            chassisCode = "D40",
            modelVariant = "NAVARA",
            vinPrefix = null,
        )
        assertEquals("Nissan", VehicleCascade.deriveMaker(bare))

        val mnt = VehicleMasterRow(
            chassisCode = "D40",
            modelVariant = "NAVARA",
            vinPrefix = "MNTCCND40",
        )
        assertEquals("Nissan", VehicleCascade.deriveMaker(mnt))
        assertTrue(VehicleCascade.models(listOf(mnt), "Nissan").contains("NAVARA"))
    }

    @Test
    fun deriveMaker_regional_nissan_wmis() {
        for (prefix in listOf("SJN", "MNT", "VSK", "MDH", "ADN", "3N1", "5N1")) {
            val row = VehicleMasterRow(
                chassisCode = "T31",
                modelVariant = "Unknown Trim",
                vinPrefix = "${prefix}ABCDEF",
            )
            assertEquals(prefix, "Nissan", VehicleCascade.deriveMaker(row))
        }
    }

    @Test
    fun deriveMaker_brand_prefix_and_toyota_wmi() {
        val toyota = VehicleMasterRow(
            chassisCode = "NZE121",
            modelVariant = "Toyota Corolla",
            vinPrefix = null,
        )
        assertEquals("Toyota", VehicleCascade.deriveMaker(toyota))

        val jtd = VehicleMasterRow(
            chassisCode = "NZE121",
            modelVariant = "Corolla",
            vinPrefix = "JTDKB20E0",
        )
        assertEquals("Toyota", VehicleCascade.deriveMaker(jtd))
    }

    @Test
    fun deriveMaker_infiniti_jnk_before_jn() {
        val row = VehicleMasterRow(
            chassisCode = "Y51",
            modelVariant = "Q70",
            vinPrefix = "JNKCV51E",
        )
        assertEquals("Infiniti", VehicleCascade.deriveMaker(row))
    }

    @Test
    fun deriveMaker_unknown_without_wmi_is_null() {
        val row = VehicleMasterRow(
            chassisCode = "XX",
            modelVariant = "Mystery Wagon",
            vinPrefix = null,
        )
        assertNull(VehicleCascade.deriveMaker(row))
    }

    @Test
    fun makers_includes_multi_make_from_seed_shaped_rows() {
        val rows = listOf(
            VehicleMasterRow(chassisCode = "D40", modelVariant = "NAVARA", vinPrefix = "MNTCCND40"),
            VehicleMasterRow(chassisCode = "T31", modelVariant = "X-TRAIL", vinPrefix = "JN1T31XX"),
            VehicleMasterRow(chassisCode = "NZE", modelVariant = "Toyota Corolla", vinPrefix = null),
        )
        assertEquals(listOf("Nissan", "Toyota"), VehicleCascade.makers(rows))
        assertEquals(listOf("NAVARA", "X-TRAIL"), VehicleCascade.models(rows, "Nissan"))
    }
}
