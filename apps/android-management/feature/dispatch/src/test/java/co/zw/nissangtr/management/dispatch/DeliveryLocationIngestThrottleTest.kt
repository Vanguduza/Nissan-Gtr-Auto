package co.zw.nissangtr.management.dispatch

import co.zw.nissangtr.management.rpc.DeliveryJobStatus
import co.zw.nissangtr.management.rpc.FakeRpcClient
import co.zw.nissangtr.management.rpc.RpcNames
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeliveryLocationIngestThrottleTest {

    @Test
    fun acceptsFirstThenBlocksUntilInterval() {
        var now = 1_000L
        val throttle = DeliveryLocationIngestThrottle(
            minIntervalMs = 5_000L,
            clockMs = { now },
        )
        assertTrue(throttle.tryAccept())
        assertFalse(throttle.tryAccept())
        now = 5_999L
        assertFalse(throttle.tryAccept())
        now = 6_000L
        assertTrue(throttle.tryAccept())
    }

    @Test
    fun resetAllowsImmediateAccept() {
        var now = 0L
        val throttle = DeliveryLocationIngestThrottle(
            minIntervalMs = 5_000L,
            clockMs = { now },
        )
        assertTrue(throttle.tryAccept())
        throttle.reset()
        now = 100L
        assertTrue(throttle.tryAccept())
    }
}

class DispatchGpsProducerGateTest {

    @Test
    fun allowDriverGpsProducerHardGatedFalse() {
        assertFalse(DispatchViewModel.ALLOW_DRIVER_GPS_PRODUCER)
        assertTrue(
            DispatchViewModel.DRIVER_GPS_PRODUCER_BLOCKED_MSG.contains("android-delivery"),
        )
    }
}

class DispatchAssignmentRpcTest {

    @Test
    fun suggestAssignOptimizeTrackAndPanicAck() = runBlocking {
        val rpc = FakeRpcClient()
        val beforeIngest = rpc.ingestedLocationCount.get()

        val jobId = rpc.createDeliveryJob(
            deliveryNoteId = "00000000-0000-4000-8000-0000000000d1",
        )
        val suggestions = rpc.suggestDeliveryAssignees(jobId, limit = 5)
        assertTrue(suggestions.isNotEmpty())
        assertEquals(FakeRpcClient.FAKE_DRIVER_USER_ID, suggestions.first().userId)

        val assigned = rpc.assignDeliveryJob(
            jobId,
            FakeRpcClient.FAKE_DRIVER_USER_ID,
            override = true,
        )
        assertEquals(jobId, assigned)

        val stops = rpc.optimizeDriverStops(FakeRpcClient.FAKE_DRIVER_USER_ID)
        assertTrue(stops.isNotEmpty())
        assertEquals(1, stops.first().routeSequence)

        assertNull(rpc.getDeliveryTrackPoint(jobId))
        rpc.updateDeliveryJobStatus(jobId, DeliveryJobStatus.DISPATCHED)
        assertNotNull(rpc.getDeliveryTrackPoint(jobId))

        val open = rpc.listOpenPanicEvents()
        assertTrue(open.any { it.id == FakeRpcClient.OPEN_PANIC_ID })
        rpc.acknowledgePanicEvent(FakeRpcClient.OPEN_PANIC_ID)
        assertTrue(rpc.listOpenPanicEvents().none { it.id == FakeRpcClient.OPEN_PANIC_ID })

        // Management must not use Fake ingest from dispatch UI — count unchanged here.
        assertEquals(beforeIngest, rpc.ingestedLocationCount.get())

        assertEquals("suggest_delivery_assignees", RpcNames.SUGGEST_DELIVERY_ASSIGNEES)
        assertEquals("assign_delivery_job", RpcNames.ASSIGN_DELIVERY_JOB)
        assertEquals("optimize_driver_stops", RpcNames.OPTIMIZE_DRIVER_STOPS)
        assertEquals("get_delivery_track_point", RpcNames.GET_DELIVERY_TRACK_POINT)
    }
}
