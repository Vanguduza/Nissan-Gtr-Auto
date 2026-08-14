package co.zw.nissangtr.management.dispatch

import co.zw.nissangtr.management.rpc.DeliveryJobDeskSummary
import co.zw.nissangtr.management.rpc.FakeRpcClient
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeliveryOverrideAssignGateTest {

    @Test
    fun unassignedPending_allowsOverride() {
        assertTrue(DeliveryOverrideAssignGate.canOverrideAssign(null, "pending"))
        assertTrue(DeliveryOverrideAssignGate.canOverrideAssign("  ", "pending"))
        assertTrue(DeliveryOverrideAssignGate.canOverrideAssign(null, "dispatched"))
    }

    @Test
    fun assignedHappyPath_blocksOverride() {
        assertFalse(
            DeliveryOverrideAssignGate.canOverrideAssign(
                FakeRpcClient.FAKE_DRIVER_USER_ID,
                "pending",
            ),
        )
        assertFalse(
            DeliveryOverrideAssignGate.canOverrideAssign(
                FakeRpcClient.FAKE_DRIVER_USER_ID,
                "dispatched",
            ),
        )
    }

    @Test
    fun terminalJobs_blockOverride() {
        assertFalse(DeliveryOverrideAssignGate.canOverrideAssign(null, "completed"))
        assertFalse(DeliveryOverrideAssignGate.canOverrideAssign(null, "failed"))
        assertFalse(
            DeliveryOverrideAssignGate.canOverrideAssign(
                FakeRpcClient.FAKE_DRIVER_USER_ID,
                "COMPLETED",
            ),
        )
    }

    @Test
    fun deskListLookup_requiresKnownUnassignedJob() {
        val unassigned = DeliveryJobDeskSummary(
            id = FakeRpcClient.FAKE_UNASSIGNED_JOB_ID,
            documentNumber = "DJ-UNASSIGNED-001",
            deliveryNoteId = "00000000-0000-4000-8000-0000000000d1",
            status = "pending",
            assigneeUserId = null,
        )
        val assigned = unassigned.copy(
            id = "assigned-job",
            assigneeUserId = FakeRpcClient.FAKE_DRIVER_USER_ID,
        )
        val jobs = listOf(unassigned, assigned)

        assertTrue(
            DeliveryOverrideAssignGate.canOverrideAssign(
                FakeRpcClient.FAKE_UNASSIGNED_JOB_ID,
                jobs,
            ),
        )
        assertFalse(DeliveryOverrideAssignGate.canOverrideAssign("assigned-job", jobs))
        assertFalse(DeliveryOverrideAssignGate.canOverrideAssign("missing-id", jobs))
        assertFalse(DeliveryOverrideAssignGate.canOverrideAssign("", jobs))
    }

    @Test
    fun fakeRpc_overrideAssignUnassignedOnlyThenBlocksHappyPath() = runBlocking {
        val rpc = FakeRpcClient()
        val stuck = rpc.listDeliveryJobs()
            .first { it.id == FakeRpcClient.FAKE_UNASSIGNED_JOB_ID }
        assertTrue(stuck.isUnassigned)
        assertTrue(DeliveryOverrideAssignGate.canOverrideAssign(stuck.assigneeUserId, stuck.status))

        val id = rpc.assignDeliveryJob(
            stuck.id,
            FakeRpcClient.FAKE_DRIVER_USER_ID,
            override = true,
        )
        assertEquals(stuck.id, id)

        val after = rpc.listDeliveryJobs().first { it.id == stuck.id }
        assertFalse(after.isUnassigned)
        assertFalse(
            DeliveryOverrideAssignGate.canOverrideAssign(after.assigneeUserId, after.status),
        )
    }
}
