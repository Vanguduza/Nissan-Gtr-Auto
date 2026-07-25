package co.zw.nissangtr.bridges.podcamera

import org.junit.Assert.assertEquals
import org.junit.Test

class PodModelsTest {

    @Test
    fun captureResult_holdsLocalPathContract() {
        val result = PodCaptureResult(
            localPath = "/data/cache/pod-photos/pod_1.jpg",
            mimeType = "image/jpeg",
            capturedAt = "2026-07-25T08:00:00Z",
        )
        assertEquals("image/jpeg", result.mimeType)
        assertEquals("/data/cache/pod-photos/pod_1.jpg", result.localPath)
    }
}
