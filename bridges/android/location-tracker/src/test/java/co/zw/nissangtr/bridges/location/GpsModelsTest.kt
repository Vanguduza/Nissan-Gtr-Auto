package co.zw.nissangtr.bridges.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @Test
    fun pingBuffer_dropsOldestWhenFull() {
        val buffer = GpsPingBuffer(capacity = 2)
        buffer.offer(GpsCoordinate(1.0, 1.0, capturedAt = "a"))
        buffer.offer(GpsCoordinate(2.0, 2.0, capturedAt = "b"))
        buffer.offer(GpsCoordinate(3.0, 3.0, capturedAt = "c"))
        val drained = buffer.drain()
        assertEquals(2, drained.size)
        assertEquals("b", drained[0].capturedAt)
        assertEquals("c", drained[1].capturedAt)
        assertTrue(buffer.size() == 0)
    }

    @Test
    fun watchOptions_defaultIsAuto() {
        assertEquals(GpsWatchCadence.AUTO, GpsWatchOptions().cadence)
    }
}
