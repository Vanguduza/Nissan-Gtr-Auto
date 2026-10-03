package co.zw.nissangtr.pos.domain.state

import co.zw.nissangtr.pos.domain.error.PosError
import co.zw.nissangtr.pos.domain.model.ApprovalPolicy
import co.zw.nissangtr.pos.domain.model.ApprovalRequest
import co.zw.nissangtr.pos.domain.model.ReasonCode
import co.zw.nissangtr.pos.domain.model.choosesReason
import co.zw.nissangtr.pos.domain.model.policyAction
import kotlin.math.abs

/*
 * Governed sale actions (Blueprint §10.10): discount, price override, void and refund always carry a
 * configured reason; the approval policy (`pos_action_requires_manager`) decides whether a manager
 * signs in for this one action. Drawer actions (cash out, variance, handover) always need a manager.
 */

sealed interface GovernanceIntent : PosSaleIntent {
    data object LoadPolicies : GovernanceIntent
    /** Open the front camera for the manager's badge; a read submits the open approval with it. */
    data class ScanBadge(val reason: ReasonCode?, val notes: String?) : GovernanceIntent
    data class SavePolicy(val policy: ApprovalPolicy) : GovernanceIntent
}

sealed interface GovernanceEvent : PosSaleEvent {
    /** Reasons and the policy decision for the open approval [request]. */
    data class ContextLoaded(val request: ApprovalRequest, val reasons: List<ReasonCode>, val needsManager: Boolean) : GovernanceEvent
    data class PoliciesLoaded(val policies: List<ApprovalPolicy>) : GovernanceEvent
    data object PolicySaved : GovernanceEvent
    data class SelfLoaded(val isApprover: Boolean) : GovernanceEvent
    /** [payload] null: the operator closed the camera. */
    data class BadgeScanned(val payload: String?, val reason: ReasonCode?, val notes: String?) : GovernanceEvent
    data class BadgeScanFailed(val error: PosError) : GovernanceEvent
    data class Failed(val error: PosError) : GovernanceEvent
}

sealed interface GovernanceEffect : PosSaleEffect {
    data class LoadContext(val request: ApprovalRequest, val action: String, val value: Double) : GovernanceEffect
    data object LoadPolicies : GovernanceEffect
    data class SavePolicy(val policy: ApprovalPolicy) : GovernanceEffect
    data object LoadSelf : GovernanceEffect
    data class ScanBadge(val reason: ReasonCode?, val notes: String?) : GovernanceEffect
}

/** Open the approval dialog for [request]; governed sale actions first load reasons and the policy. */
internal fun openApproval(state: PosState, request: ApprovalRequest): Reduction {
    // A signed-in manager approves as themselves: no badge or password is asked for.
    val next = state.copy(approval = request, approvalReasons = null, approvalNeedsManager = !state.selfApprover, badgeScanning = false)
    if (!request.choosesReason) return Reduction(next.copy(approvalReasons = emptyList()))
    val value = when (request) {
        is ApprovalRequest.Discount -> request.percent
        is ApprovalRequest.PriceOverride -> {
            val before = state.cart.lines.firstOrNull { it.lineId == request.lineId }?.unitPrice?.minor?.div(100.0) ?: 0.0
            when {
                before == 0.0 -> if (request.unitPrice == 0.0) 0.0 else 100.0
                else -> abs(request.unitPrice - before) / before * 100.0
            }
        }
        else -> 0.0
    }
    return Reduction(next, listOf(GovernanceEffect.LoadContext(request, request.policyAction, value)))
}

internal fun reduceGovernanceIntent(state: PosState, intent: GovernanceIntent): Reduction = when (intent) {
    GovernanceIntent.LoadPolicies -> if (!state.online) offlineRefusal(state, "online_only")
    else Reduction(state, listOf(GovernanceEffect.LoadPolicies))

    is GovernanceIntent.ScanBadge -> when {
        state.approval == null || state.approving || state.badgeScanning -> Reduction(state)
        !state.online -> offlineRefusal(state, "manager_approval")
        else -> Reduction(state.copy(badgeScanning = true, feedback = null), listOf(GovernanceEffect.ScanBadge(intent.reason, intent.notes)))
    }

    is GovernanceIntent.SavePolicy -> when {
        !state.online -> offlineRefusal(state, "online_only")
        intent.policy.thresholdValue < 0 -> Reduction(state.copy(feedback = PosFeedback.Failure(PosError.Input("threshold", "positive"))))
        else -> Reduction(state.copy(feedback = null), listOf(GovernanceEffect.SavePolicy(intent.policy)))
    }
}

internal fun reduceGovernanceEvent(state: PosState, event: GovernanceEvent): Reduction = when (event) {
    // Only for the request still open: a cancelled or replaced dialog ignores a late answer.
    is GovernanceEvent.ContextLoaded -> if (state.approval != event.request) Reduction(state)
    else Reduction(state.copy(approvalReasons = event.reasons, approvalNeedsManager = event.needsManager && !state.selfApprover))

    is GovernanceEvent.PoliciesLoaded -> Reduction(state.copy(policies = event.policies))

    GovernanceEvent.PolicySaved -> Reduction(
        state.copy(feedback = PosFeedback.Notice(PosNotice.PolicySaved)),
        listOf(GovernanceEffect.LoadPolicies),
    )

    is GovernanceEvent.Failed -> Reduction(state.copy(feedback = PosFeedback.Failure(event.error)))

    is GovernanceEvent.SelfLoaded -> Reduction(state.copy(selfApprover = event.isApprover))

    is GovernanceEvent.BadgeScanned -> {
        val scanned = state.copy(badgeScanning = false)
        if (event.payload.isNullOrBlank() || scanned.approval == null) Reduction(scanned)
        else reduce(scanned, PosSaleIntent.SubmitApproval(credentials = null, reason = event.reason, notes = event.notes, badge = event.payload))
    }

    is GovernanceEvent.BadgeScanFailed -> Reduction(state.copy(badgeScanning = false, feedback = PosFeedback.Failure(event.error)))
}
