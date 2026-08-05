package co.zw.nissangtr.management.kiosk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KioskDevicePrefsTest {

    @Test
    fun clampIdleMinutes_respectsRange() {
        assertEquals(1, KioskDevicePrefs.clampIdleMinutes(0))
        assertEquals(1, KioskDevicePrefs.clampIdleMinutes(-5))
        assertEquals(3, KioskDevicePrefs.clampIdleMinutes(3))
        assertEquals(15, KioskDevicePrefs.clampIdleMinutes(15))
        assertEquals(15, KioskDevicePrefs.clampIdleMinutes(99))
    }

    @Test
    fun defaults_matchLockedPlan() {
        assertFalse(KioskDevicePrefs.DEFAULT_ENGINE_AUDIO_ENABLED)
        assertEquals(3, KioskDevicePrefs.DEFAULT_IDLE_MINUTES)
        assertEquals(1, KioskDevicePrefs.MIN_IDLE_MINUTES)
        assertEquals(15, KioskDevicePrefs.MAX_IDLE_MINUTES)
    }

    @Test
    fun defaults_areWithinClampRange() {
        assertTrue(
            KioskDevicePrefs.DEFAULT_IDLE_MINUTES in
                KioskDevicePrefs.MIN_IDLE_MINUTES..KioskDevicePrefs.MAX_IDLE_MINUTES,
        )
    }
}

class HardeningPathDetectorTest {

    @Test
    fun phoneApk_neverClaimsDeviceOwnerPath() {
        val s = HardeningPathDetector.detect(
            tabletKiosk = false,
            isDeviceOwner = true,
            rootedHeuristic = true,
        )
        assertEquals(HardeningPathKind.PhoneNonKiosk, s.kind)
    }

    @Test
    fun rootedPlusDo_pathBPrimary() {
        val s = HardeningPathDetector.detect(
            tabletKiosk = true,
            isDeviceOwner = true,
            rootedHeuristic = true,
        )
        assertEquals(HardeningPathKind.PathBPrimaryLikely, s.kind)
    }

    @Test
    fun doWithoutRoot_pathAFallback() {
        val s = HardeningPathDetector.detect(
            tabletKiosk = true,
            isDeviceOwner = true,
            rootedHeuristic = false,
        )
        assertEquals(HardeningPathKind.PathAFallbackActive, s.kind)
    }

    @Test
    fun notProvisioned_mentionsRunbook() {
        val s = HardeningPathDetector.detect(
            tabletKiosk = true,
            isDeviceOwner = false,
            rootedHeuristic = false,
        )
        assertEquals(HardeningPathKind.NotProvisioned, s.kind)
        assertTrue(s.detail.contains("android-management-kiosk-device-owner"))
    }
}
