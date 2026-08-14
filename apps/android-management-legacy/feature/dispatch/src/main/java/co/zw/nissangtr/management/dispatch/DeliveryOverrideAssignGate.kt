package co.zw.nissangtr.management.dispatch

/**
 * Desk **exception** gate for manual [assign_delivery_job].
 *
 * Auto-assign (`_try_auto_assign_delivery_job` + FIFO/offer) remains SoR.
 * Staff may override only when the job is unassigned / stuck — never as the
 * happy-path “pick a driver” flow for already-assigned jobs.
 *
 * RPC `p_override` bypasses capacity/shift eligibility; this gate is about
 * **when** the desk may call assign at all (unassigned-only).
 */
object DeliveryOverrideAssignGate {
    private val terminalStatuses = setOf("completed", "failed")

    fun canOverrideAssign(assigneeUserId: String?, status: String?): Boolean {
        val normalized = status?.trim()?.lowercase().orEmpty()
        if (normalized in terminalStatuses) return false
        return assigneeUserId.isNullOrBlank()
    }

    fun canOverrideAssign(
        jobId: String,
        jobs: List<co.zw.nissangtr.management.rpc.DeliveryJobDeskSummary>,
    ): Boolean {
        if (jobId.isBlank()) return false
        val job = jobs.find { it.id == jobId } ?: return false
        return canOverrideAssign(job.assigneeUserId, job.status)
    }
}
