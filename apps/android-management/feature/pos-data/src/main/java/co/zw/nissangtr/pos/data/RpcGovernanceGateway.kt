package co.zw.nissangtr.pos.data

import co.zw.nissangtr.management.rpc.RpcClient
import co.zw.nissangtr.pos.domain.gateway.GovernanceGateway
import co.zw.nissangtr.pos.domain.model.ApprovalPolicy
import co.zw.nissangtr.pos.domain.model.ReasonCode
import co.zw.nissangtr.pos.domain.result.PosResult

/** Approval policies and reason codes over the live RPCs (`pos_approval_*`). */
class RpcGovernanceGateway(private val rpc: RpcClient) : GovernanceGateway {
    override suspend fun requiresManager(action: String, value: Double): PosResult<Boolean> =
        // Fail closed: if the policy cannot be read, ask for a manager.
        when (val r = call { rpc.posActionRequiresManager(action, value) }) {
            is PosResult.Ok -> r
            is PosResult.Err -> PosResult.Ok(true)
        }

    override suspend fun reasons(action: String) = call {
        rpc.listPosApprovalReasons(action).map { ReasonCode(it.code, it.label, it.requiresNotes) }
    }

    override suspend fun policies() = call {
        rpc.listPosApprovalPolicies().map { ApprovalPolicy(it.action, it.thresholdValue, it.alwaysRequireManager, it.reasonRequired) }
    }

    override suspend fun selfApprover() = call { rpc.myPosApproverStatus() }

    override suspend fun setPolicy(policy: ApprovalPolicy) = call {
        rpc.setPosApprovalPolicy(policy.action, policy.thresholdValue, policy.alwaysRequireManager, policy.reasonRequired)
    }
}
