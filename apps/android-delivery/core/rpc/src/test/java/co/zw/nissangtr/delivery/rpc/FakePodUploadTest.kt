package co.zw.nissangtr.delivery.rpc

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Fake Storage + submit_delivery_pod path for POD photo/signature evidence. */
class FakePodUploadTest {

    @Test
    fun uploadAndSubmitPod_storesEvidencePathsOnJob() = runBlocking {
        val rpc = FakeRpcClient()
        val jobId = FakeRpcClient.JOB_1
        val photoKey = "$jobId/photo.jpg"
        val sigKey = "$jobId/signature.png"

        assertEquals(photoKey, rpc.uploadPodAsset(photoKey, "fake://photo.jpg", "image/jpeg"))
        assertEquals(sigKey, rpc.uploadPodAsset(sigKey, "fake://sig.png", "image/png"))

        rpc.generateDeliveryPodOtp(jobId)
        assertTrue(rpc.verifyDeliveryPodOtp(jobId, FakeRpcClient.FAKE_OTP))

        val id = rpc.submitDeliveryPod(
            deliveryJobId = jobId,
            photoPath = photoKey,
            signaturePath = sigKey,
            otpCode = FakeRpcClient.FAKE_OTP,
            notes = "doorstep",
        )
        assertEquals(jobId, id)

        val job = rpc.listMyDeliveryJobs().first { it.id == jobId }
        assertEquals("completed", job.status)
        assertEquals(photoKey, job.podPhotoPath)
        assertEquals(sigKey, job.podSignaturePath)
    }
}
