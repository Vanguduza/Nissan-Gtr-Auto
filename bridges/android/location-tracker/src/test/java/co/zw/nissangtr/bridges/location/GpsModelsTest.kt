package co.zw.nissangtr.bridges.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Contract-aligned helpers (no device / Play Services required).
 * Device tests: grant location → getCurrentPosition / watchPosition on hardware.
 */
class GpsModelsTest {

    @Test
    fun toDeliveryLocationIngest_mapsContractFields() {
        val coord = GpsCoordinate(
            latitude = -17.8292,
            longitude = 31.0522,
            accuracyMeters = 12.5f,
            capturedAt = "2026-07-24T20:00:00Z",
        )
        val ingest = toDeliveryLocationIngest(
            deliveryJobId = "11111111-1111-1111-1111-111111111111",
            coord = coord,
        )
        assertEquals("11111111-1111-1111-1111-111111111111", ingest.deliveryJobId)
        assertEquals(-17.8292, ingest.lat, 0.0)
        assertEquals(31.0522, ingest.lng, 0.0)
        assertEquals("2026-07-24T20:00:00Z", ingest.recordedAt)
        assertEquals(12.5, ingest.accuracyM!!, 0.0)
    }

    @Test
    fun toDeliveryLocationIngest_omitsNullAccuracy() {
        val coord = GpsCoordinate(
            latitude = 0.0,
            longitude = 0.0,
            accuracyMeters = null,
            capturedAt = "2026-07-24T20:00:00Z",
        )
        val ingest = toDeliveryLocationIngest("job", coord)
        assertNull(ingest.accuracyM)
    }
}
