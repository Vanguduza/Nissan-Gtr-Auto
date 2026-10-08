package co.zw.nissangtr.pos.data

import co.zw.nissangtr.management.rpc.RpcClient
import co.zw.nissangtr.pos.domain.gateway.ApprovalsGateway
import co.zw.nissangtr.pos.domain.model.CurrencyCode
import co.zw.nissangtr.pos.domain.model.Money
import co.zw.nissangtr.pos.domain.model.WaitingApproval

/** The signed-in person's approvals inbox over `list_my_approvals`. */
class RpcApprovalsGateway(private val rpc: RpcClient) : ApprovalsGateway {
    override suspend fun waiting() = call {
        rpc.listMyApprovals().map { r ->
            WaitingApproval(
                kind = r.kind,
                ref = r.ref,
                title = r.title,
                detail = r.detail,
                urgent = r.urgent,
                waitingSince = r.waitingSince,
                amount = r.amount?.let { a -> r.currency?.let { c -> Money.ofMajor(a, CurrencyCode(c)) } },
            )
        }
    }
}
