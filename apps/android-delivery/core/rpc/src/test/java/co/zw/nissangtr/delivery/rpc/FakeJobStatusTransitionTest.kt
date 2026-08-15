package co.zw.nissangtr.delivery.rpc

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Fake mirrors ERP: Active until signature via submit_delivery_pod. */
class FakeJobStatusTransitionTest {

    @Test
    fun updateStatusToCompletedWithoutSignatureFails() = runBlocking {
        val rpc = FakeRpcClient()
        val err = runCatching {
            rpc.updateDeliveryJobStatus(FakeRpcClient.JOB_1, DeliveryJobStatus.COMPLETED)
        }.exceptionOrNull()
        assertTrue(err is IllegalArgumentException)
        assertTrue(err!!.message!!.contains("submit_delivery_pod"))
        val job = rpc.getDeliveryJob(FakeRpcClient.JOB_1)!!
        assertEquals("dispatched", job.status)
    }

    @Test
    fun submitPodMovesActiveToDone() = runBlocking {
        val rpc = FakeRpcClient()
        rpc.generateDeliveryPodOtp(FakeRpcClient.JOB_1)
        rpc.uploadPodAsset("${FakeRpcClient.JOB_1}/photo.jpg", "fake://photo.jpg", "image/jpeg")
        rpc.uploadPodAsset("${FakeRpcClient.JOB_1}/signature.png", "fake://sig.png", "image/png")
        rpc.submitDeliveryPod(
            deliveryJobId = FakeRpcClient.JOB_1,
            photoPath = "${FakeRpcClient.JOB_1}/photo.jpg",
            signaturePath = "${FakeRpcClient.JOB_1}/signature.png",
            otpCode = FakeRpcClient.FAKE_OTP,
        )
        val job = rpc.getDeliveryJob(FakeRpcClient.JOB_1)!!
        assertEquals("completed", job.status)
        assertTrue(!job.podSignaturePath.isNullOrBlank())
    }

    @Test
    fun markFailedLeavesDoneJobsUntouched() = runBlocking {
        val rpc = FakeRpcClient()
        rpc.failDeliveryJob(
            deliveryJobId = FakeRpcClient.JOB_1,
            reason = DeliveryFailureReason.REFUSED,
            createReattempt = false,
        )
        assertEquals("failed", rpc.getDeliveryJob(FakeRpcClient.JOB_1)!!.status)
        assertEquals("completed", rpc.getDeliveryJob(FakeRpcClient.JOB_DONE)!!.status)
    }
}
