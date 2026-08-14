package co.zw.nissangtr.bridges.podcamera

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class FakePodCameraBridgeTest {

    @Test
    fun capturePhoto_returnsConfiguredEvidencePath() = runBlocking {
        val bridge = FakePodCameraBridge()
        val result = bridge.capturePhoto()
        assertEquals("image/jpeg", result.mimeType)
        assertEquals("fake://pod-photos/evidence.jpg", result.localPath)
        assertEquals(1, bridge.captureCount)
    }

    @Test
    fun capturePhoto_requiresGrantedPermission() {
        val bridge = FakePodCameraBridge(permission = CameraPermissionStatus.DENIED)
        assertThrows(SecurityException::class.java) {
            runBlocking { bridge.capturePhoto() }
        }
    }
}
