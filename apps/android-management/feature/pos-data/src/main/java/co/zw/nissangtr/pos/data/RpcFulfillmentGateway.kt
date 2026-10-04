package co.zw.nissangtr.pos.data

import co.zw.nissangtr.management.rpc.RpcClient
import co.zw.nissangtr.pos.domain.gateway.FulfillmentGateway
import co.zw.nissangtr.pos.domain.model.FulfillmentDraft
import co.zw.nissangtr.pos.domain.model.FulfillmentKind
import co.zw.nissangtr.pos.domain.model.FulfillmentRequest
import co.zw.nissangtr.pos.domain.model.FulfillmentStep

/** Holds, other-branch pickup, branch transfers and back-orders over the `*_pos_fulfillment_*` RPCs. */
class RpcFulfillmentGateway(private val rpc: RpcClient) : FulfillmentGateway {
    override suspend fun create(draft: FulfillmentDraft) = call {
        rpc.createPosFulfillmentRequest(
            draft.kind.rpcValue, draft.stockItemId, draft.qty, draft.sourceWarehouseId, draft.destinationWarehouseId,
            draft.customerId, draft.cartId, null, draft.holdMinutes,
        )
    }

    override suspend fun list(query: String?, status: String?) = call {
        rpc.listPosFulfillmentRequests(query, status).map {
            FulfillmentRequest(
                it.id, it.documentNumber, FulfillmentKind.of(it.kind), it.status, it.stockItemId, it.oemPartNumber, it.description, it.qty,
                it.sourceWarehouseName, it.destinationWarehouseName, it.cartId, it.invoiceId, it.expiresAt, it.createdAt,
            )
        }
    }

    override suspend fun step(requestId: String, step: FulfillmentStep) = call { rpc.posFulfillmentStep(requestId, step.rpcValue, null) }
}
