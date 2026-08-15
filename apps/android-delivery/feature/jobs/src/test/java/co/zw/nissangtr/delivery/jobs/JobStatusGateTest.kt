package co.zw.nissangtr.delivery.jobs

import co.zw.nissangtr.delivery.rpc.CurrencyCode
import co.zw.nissangtr.delivery.rpc.DeliveryJobSettlement
import co.zw.nissangtr.delivery.rpc.DeliveryJobSummary
import co.zw.nissangtr.delivery.rpc.FakeRpcClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JobStatusGateTest {

    private fun job(
        status: String,
        signature: String? = null,
        address: String? = "12 Test St",
        notes: String? = "Gate 1",
    ) = DeliveryJobSummary(
        id = FakeRpcClient.JOB_1,
        deliveryNoteId = "00000000-0000-4000-8000-0000000000d1",
        documentNumber = "DJ-1",
        status = status,
        dropoffLat = -17.82,
        dropoffLng = 31.05,
        etaAt = null,
        etaSeconds = null,
        notes = notes,
        routeSequence = 1,
        reattemptOf = null,
        failureReasonCode = null,
        podPhotoPath = null,
        podSignaturePath = signature,
        assigneeUserId = FakeRpcClient.FAKE_DRIVER_USER_ID,
        settlement = DeliveryJobSettlement(
            currency = CurrencyCode.USD,
            amountDueMinor = 4550L,
            amountPaidMinor = 0L,
        ),
        dropoffAddressText = address,
    )

    @Test
    fun activeUntilSignatureCaptured() {
        val active = job("dispatched", signature = null)
        assertTrue(JobStatusGate.isActive(active.status))
        assertTrue(JobStatusGate.canOpenCompleteFlow(active))
        assertEquals(
            JobStatusGate.COMPLETE_REQUIRES_SIGNATURE,
            JobStatusGate.blockingReasonForComplete(active.status, active.podSignaturePath),
        )
        assertFalse(JobStatusGate.canTransitionToDone(active.status, active.podSignaturePath))
    }

    @Test
    fun completeRequiresSignatureEvenWhenPhotoMissingOnGate() {
        // Client gate for Done transition mirrors ERP: signature required.
        assertFalse(JobStatusGate.canTransitionToDone("dispatched", null))
        assertTrue(JobStatusGate.canTransitionToDone("dispatched", "job/signature.png"))
    }

    @Test
    fun terminalJobsReadonly() {
        val done = job("completed", signature = "x/signature.png")
        assertTrue(JobStatusGate.isTerminal(done.status))
        assertFalse(JobStatusGate.canOpenCompleteFlow(done))
        assertFalse(JobStatusGate.canMarkFailed(done))
        assertEquals(
            JobStatusGate.TERMINAL_READONLY,
            JobStatusGate.blockingReasonForComplete(done.status, done.podSignaturePath),
        )
    }

    @Test
    fun receiptAndAddressLinesPresent() {
        val j = job("pending")
        val receipt = JobStatusGate.receiptCopyLines(j)
        assertTrue(receipt.any { it.contains("DJ-1") })
        assertTrue(receipt.any { it.contains("Amount due") })
        val address = JobStatusGate.deliveryAddressLines(j)
        assertTrue(address.any { it.contains("12 Test St") })
        assertTrue(address.any { it.contains("-17.82") })
    }

    @Test
    fun addressFallsBackToCoordsWhenTextMissing() {
        val j = job("pending", address = null, notes = null)
        val address = JobStatusGate.deliveryAddressLines(j)
        assertEquals(1, address.size)
        assertTrue(address[0].contains("-17.82"))
        assertNull(j.dropoffAddressText)
    }
}
