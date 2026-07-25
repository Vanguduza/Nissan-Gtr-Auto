package co.zw.nissangtr.delivery.pod

import org.junit.Assert.assertEquals
import org.junit.Test

class PodObjectKeyTest {
    @Test
    fun photoAndSignatureKeysMatchSharedClientConvention() {
        val jobId = "abc-123"
        assertEquals("abc-123/photo.jpg", deliveryPodPhotoObjectKey(jobId))
        assertEquals("abc-123/signature.png", deliveryPodSignatureObjectKey(jobId))
    }
}
