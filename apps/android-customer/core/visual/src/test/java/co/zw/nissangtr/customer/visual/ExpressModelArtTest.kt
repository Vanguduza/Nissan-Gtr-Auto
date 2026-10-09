package co.zw.nissangtr.customer.visual

import org.junit.Assert.assertEquals
import org.junit.Test

class ExpressModelArtTest {
    @Test
    fun matchesTheCatalogueModelVariant() {
        assertEquals(R.drawable.kit_bluebird, ExpressModelArt.forModel("Nissan Bluebird"))
        assertEquals(R.drawable.kit_bluebird_sylphy, ExpressModelArt.forModel("Nissan Bluebird Sylphy"))
        assertEquals(R.drawable.kit_cube_cube_cubic, ExpressModelArt.forModel("Nissan Cube/Cube Cubic"))
        assertEquals(R.drawable.kit_rnessa, ExpressModelArt.forModel("Nissan R`Nessa"))
    }

    @Test
    fun gtrKeepsTheOriginalPhoto() {
        assertEquals(R.drawable.pos_hero_car, ExpressModelArt.forModel("Nissan Nissan GT-R"))
        assertEquals(R.drawable.pos_hero_car, ExpressModelArt.forModel("Nissan GT-R R35"))
    }

    @Test
    fun prefixMatchesStopAtAWord() {
        assertEquals(R.drawable.kit_skyline, ExpressModelArt.forModel("Nissan Skyline R33"))
        assertEquals("no 'be' for Bertone", ExpressModelArt.Default, ExpressModelArt.forModel("Nissan Bertone"))
        assertEquals(ExpressModelArt.Default, ExpressModelArt.forModel(null))
    }
}
