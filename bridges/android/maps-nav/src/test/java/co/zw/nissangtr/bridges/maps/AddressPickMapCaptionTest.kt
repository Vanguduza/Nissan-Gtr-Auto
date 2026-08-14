package co.zw.nissangtr.bridges.maps

import org.junit.Assert.assertEquals
import org.junit.Test

class AddressPickMapCaptionTest {
    @Test
    fun mapLibreIsPrimaryCaption() {
        assertEquals("MapLibre SoR", addressPickMapCaption(showingMapLibre = true, mapsKeyPresent = false))
        assertEquals("MapLibre SoR", addressPickMapCaption(showingMapLibre = true, mapsKeyPresent = true))
    }

    @Test
    fun googleIsDeprecatedFallbackCaption() {
        assertEquals(
            "DEPRECATED Google Maps fallback",
            addressPickMapCaption(showingMapLibre = false, mapsKeyPresent = true),
        )
    }

    @Test
    fun unavailableWhenNeitherEngine() {
        assertEquals("Map unavailable", addressPickMapCaption(showingMapLibre = false, mapsKeyPresent = false))
    }
}
