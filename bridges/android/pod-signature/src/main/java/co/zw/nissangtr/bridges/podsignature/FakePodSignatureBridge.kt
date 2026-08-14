package co.zw.nissangtr.bridges.podsignature

import java.time.Instant

/**
 * JVM/instrumented test + Fake-mode host stub. No Compose Activity.
 * Returns a configurable local PNG path (often `fake://…`) for app upload wiring.
 */
class FakePodSignatureBridge(
    var nextResult: PodCaptureResult = PodCaptureResult(
        localPath = "fake://pod-signatures/sig.png",
        mimeType = "image/png",
        capturedAt = Instant.parse("2026-08-14T00:00:00Z").toString(),
    ),
) : PodSignatureBridge {

    var captureCount: Int = 0
        private set

    override suspend fun captureSignature(options: PodSignatureOptions): PodCaptureResult {
        captureCount++
        return nextResult
    }
}
