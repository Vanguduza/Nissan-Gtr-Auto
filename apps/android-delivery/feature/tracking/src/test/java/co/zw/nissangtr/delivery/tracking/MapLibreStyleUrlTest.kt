package co.zw.nissangtr.delivery.tracking

import org.junit.Assert.assertEquals
import org.junit.Test

class MapLibreStyleUrlTest {
    @Test
    fun blankFallsBackToDemotiles() {
        assertEquals(DEFAULT_MAPLIBRE_STYLE_URL, resolveMapLibreStyleUrl(""))
        assertEquals(DEFAULT_MAPLIBRE_STYLE_URL, resolveMapLibreStyleUrl("   "))
    }

    @Test
    fun configuredUrlWins() {
        assertEquals(
            EMULATOR_MAPLIBRE_STYLE_URL,
            resolveMapLibreStyleUrl(EMULATOR_MAPLIBRE_STYLE_URL),
        )
        assertEquals(
            "http://192.168.1.10:8081/styles/basic-preview/style.json",
            resolveMapLibreStyleUrl(" http://192.168.1.10:8081/styles/basic-preview/style.json "),
        )
    }
}
