package co.zw.nissangtr.pos.till

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IdleLockControllerTest {
    @Test
    fun locksAfterIdleThreshold() {
        val start = 1_000_000L
        assertFalse(IdleLockController.shouldLock(start, start + 60_000L))
        assertTrue(
            IdleLockController.shouldLock(
                start,
                start + IdleLockController.IDLE_MS,
            ),
        )
    }
}
