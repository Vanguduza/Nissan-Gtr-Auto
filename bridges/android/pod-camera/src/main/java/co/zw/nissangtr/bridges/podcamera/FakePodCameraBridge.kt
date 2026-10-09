package co.zw.nissangtr.bridges.podcamera

import java.time.Instant

/**
 * JVM/instrumented test + Fake-mode host stub. No CameraX / Activity.
 * Returns a configurable local path (often `fake://…`) for app upload wiring.
 */
class FakePodCameraBridge(
    var permission: CameraPermissionStatus = CameraPermissionStatus.GRANTED,
    var nextResult: PodCaptureResult = PodCaptureResult(
        localPath = "fake://pod-photos/evidence.jpg",
        mimeType = "image/jpeg",
        capturedAt = Instant.parse("2026-08-14T00:00:00Z").toString(),
    ),
) : PodCameraBridge {

    var captureCount: Int = 0
        private set

    override suspend fun getCameraPermissionStatus(): CameraPermissionStatus = permission

    override suspend fun requestCameraPermission(): CameraPermissionStatus = permission

    override suspend fun capturePhoto(): PodCaptureResult {
        if (permission != CameraPermissionStatus.GRANTED) {
            throw SecurityException("Camera permission not granted ($permission)")
        }
        captureCount++
        return nextResult
    }
}
