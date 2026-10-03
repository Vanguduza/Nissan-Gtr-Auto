package co.zw.nissangtr.pos.data

import co.zw.nissangtr.management.rpc.PosSplitSession
import co.zw.nissangtr.management.rpc.RpcClient
import co.zw.nissangtr.pos.domain.gateway.SplitPaymentGateway
import co.zw.nissangtr.pos.domain.model.Money
import co.zw.nissangtr.pos.domain.model.RefundFeePolicy
import co.zw.nissangtr.pos.domain.model.SplitLeg
import co.zw.nissangtr.pos.domain.model.SplitRecoveryItem
import co.zw.nissangtr.pos.domain.model.SplitRefund
import co.zw.nissangtr.pos.domain.model.SplitSession
import co.zw.nissangtr.pos.domain.model.SplitTender

/**
 * Part payments (staged split) over the `*_pos_split_*` RPCs. Refund steps are manager / finance
 * actions and go through `SalesGateway.approve` with the rest of the governed actions.
 */
class RpcSplitPaymentGateway(private val rpc: RpcClient) : SplitPaymentGateway {
    private val carts = RpcSaleGateways(rpc).companion

    override suspend fun cart(cartId: String) = carts.cart(cartId)

    override suspend fun find(orderId: String) = call { rpc.findPosSplitPayment(orderId)?.toDomain() }

    override suspend fun start(orderId: String) = call { rpc.startPosSplitPayment(orderId).toDomain() }

    override suspend fun addPart(sessionId: String, tender: SplitTender, amount: Money, requestId: String, reference: String?) = call {
        rpc.addPosSplitPaymentLeg(sessionId, tender.rpcValue, amount.minor / 100.0, requestId, reference).toDomain()
    }

    override suspend fun reduceBasket(sessionId: String, items: List<Pair<String, Double>>, notes: String?) = call {
        rpc.acceptPosSplitAffordableItems(sessionId, items, notes).toDomain()
    }

    override suspend fun cancel(sessionId: String, reason: String, feePolicy: RefundFeePolicy) = call {
        rpc.requestPosSplitCancellation(sessionId, reason, feePolicy.rpcValue).toDomain()
    }

    override suspend fun retryFinalization(sessionId: String) = call { rpc.retryPosSplitFinalization(sessionId).toDomain() }

    override suspend fun recovery() = call {
        rpc.listPosSplitPaymentRecovery().map { SplitRecoveryItem(it.documentNumber, it.customerName, it.updatedAt, it.session.toDomain()) }
    }
}

internal fun PosSplitSession.toDomain(): SplitSession {
    val c = currency.toDomain()
    fun m(v: Double) = Money.ofMajor(v, c)
    return SplitSession(
        sessionId = sessionId,
        orderId = orderId,
        status = status,
        total = m(total),
        received = m(locked),
        pending = m(pending),
        balanceDue = m(balanceDue),
        availableToAllocate = m(availableToAllocate),
        finalInvoiceId = finalInvoiceId,
        finalizationError = finalizationError,
        legs = legs.map { SplitLeg(it.id, it.sequenceNo, it.tender, m(it.amount), it.status, it.externalReference ?: it.providerRef, it.statusDetail, it.appliedAmount?.let(::m), it.refundRequired?.let(::m)) },
        refunds = refunds.map { SplitRefund(it.id, it.legId, it.status, m(it.grossAmount), it.feePolicy, it.netCustomerRefund?.let(::m), it.providerRef, it.failureReason, it.notes) },
    )
}
