package co.zw.nissangtr.bridges.podsignature

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PodModelsTest {

    @Test
    fun signatureOptions_defaults() {
        val opts = PodSignatureOptions()
        assertNull(opts.title)
        assertNull(opts.strokeWidth)
    }

    @Test
    fun captureResult_holdsPngPath() {
        val result = PodCaptureResult(
            localPath = "/data/cache/pod-signatures/sig_1.png",
            mimeType = "image/png",
            capturedAt = "2026-07-25T08:00:00Z",
        )
        assertEquals("image/png", result.mimeType)
    }
}
