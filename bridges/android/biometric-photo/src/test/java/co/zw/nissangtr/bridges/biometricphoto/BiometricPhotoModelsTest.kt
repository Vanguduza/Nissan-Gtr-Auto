package co.zw.nissangtr.bridges.biometricphoto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BiometricPhotoModelsTest {

    @Test
    fun captureResult_holdsLocalPathContract() {
        val result = BiometricPhotoCaptureResult(
            localPath = "/data/cache/hr-biometric-photos/hr_1.jpg",
            mimeType = "image/jpeg",
            capturedAt = "2026-08-03T12:00:00Z",
        )
        assertEquals("image/jpeg", result.mimeType)
        assertEquals("/data/cache/hr-biometric-photos/hr_1.jpg", result.localPath)
    }

    @Test
    fun options_defaultPreferFrontCamera() {
        val opts = BiometricPhotoCaptureOptions()
        assertTrue(opts.preferFrontCamera)
    }
}
