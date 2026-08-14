package co.zw.nissangtr.bridges.podsignature

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class FakePodSignatureBridgeTest {

    @Test
    fun captureSignature_returnsConfiguredPngPath() = runBlocking {
        val bridge = FakePodSignatureBridge()
        val result = bridge.captureSignature(PodSignatureOptions(title = "test"))
        assertEquals("image/png", result.mimeType)
        assertEquals("fake://pod-signatures/sig.png", result.localPath)
        assertEquals(1, bridge.captureCount)
    }
}
