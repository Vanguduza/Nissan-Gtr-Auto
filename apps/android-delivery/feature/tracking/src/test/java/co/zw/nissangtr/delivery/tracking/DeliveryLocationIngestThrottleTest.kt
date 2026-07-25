package co.zw.nissangtr.delivery.tracking

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeliveryLocationIngestThrottleTest {

    @Test
    fun acceptsFirstThenBlocksUntilInterval() {
        var now = 1_000L
        val throttle = DeliveryLocationIngestThrottle(
            minIntervalMs = 5_000L,
            clockMs = { now },
        )
        assertTrue(throttle.tryAccept())
        assertFalse(throttle.tryAccept())
        now = 5_999L
        assertFalse(throttle.tryAccept())
        now = 6_000L
        assertTrue(throttle.tryAccept())
    }

    @Test
    fun resetAllowsImmediateAccept() {
        var now = 0L
        val throttle = DeliveryLocationIngestThrottle(
            minIntervalMs = 5_000L,
            clockMs = { now },
        )
        assertTrue(throttle.tryAccept())
        throttle.reset()
        now = 100L
        assertTrue(throttle.tryAccept())
    }
}
