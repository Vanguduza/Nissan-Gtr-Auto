package co.zw.nissangtr.pos.data

import co.zw.nissangtr.management.rpc.PosDenominationLine
import co.zw.nissangtr.management.rpc.PosTillSessionRow
import co.zw.nissangtr.management.rpc.RpcClient
import co.zw.nissangtr.pos.domain.gateway.TillGateway
import co.zw.nissangtr.pos.domain.model.DenominationCount
import co.zw.nissangtr.pos.domain.model.HandoverOperator
import co.zw.nissangtr.pos.domain.model.Money
import co.zw.nissangtr.pos.domain.model.ReasonCode
import co.zw.nissangtr.pos.domain.model.TillCloseResult
import co.zw.nissangtr.pos.domain.model.TillSession
import co.zw.nissangtr.pos.domain.model.TillStatus

/**
 * Till sessions over the live `pos_till_*` RPCs. [deviceId] identifies this counter device so the
 * till it opened is found again after an app restart. Cash out, variance approval and handover
 * are manager actions and go through `SalesGateway.approve`.
 */
class RpcTillGateway(private val rpc: RpcClient, private val deviceId: String) : TillGateway {
    private var warehouseId: String? = null

    override suspend fun current() = call { rpc.getMyOpenPosTillSession(deviceId)?.toDomain() }

    override suspend fun open(openingFloat: Money) = call {
        val warehouse = warehouseId ?: shopWarehouseId(rpc).also { warehouseId = it }
        rpc.openPosTillSession(warehouse, deviceId, openingFloat.minor / 100.0, openingFloat.currency.toRpc())
        rpc.getMyOpenPosTillSession(deviceId)?.toDomain()
            ?: throw PosFailure(co.zw.nissangtr.pos.domain.error.PosError.BusinessRule("till_missing", ""))
    }

    override suspend fun attachCart(cartId: String, sessionId: String) = call { rpc.attachPosCartTillSession(cartId, sessionId) }

    override suspend fun reasons(action: String) = call {
        rpc.listPosApprovalReasons(action).map { ReasonCode(it.code, it.label, it.requiresNotes) }
    }

    override suspend fun cashIn(sessionId: String, amount: Money, reasonCode: String, notes: String?) = call {
        rpc.recordPosTillCashMovement(sessionId, "cash_in", amount.minor / 100.0, reasonCode, notes)
        Unit
    }

    override suspend fun close(sessionId: String, counts: List<DenominationCount>, varianceReasonCode: String?, notes: String?) = call {
        val currency = rpc.getMyOpenPosTillSession(deviceId)?.takeIf { it.id == sessionId }?.currency?.toDomain()
            ?: co.zw.nissangtr.pos.domain.model.CurrencyCode.USD
        val r = rpc.submitPosTillDenominatedClose(
            sessionId,
            counts.map { PosDenominationLine(it.denominationMinor / 100.0, it.quantity) },
            varianceReasonCode,
            notes,
        )
        TillCloseResult(
            sessionId = r.sessionId,
            expected = Money.ofMajor(r.expectedCash, currency),
            counted = Money.ofMajor(r.countedCash, currency),
            variance = Money.ofMajor(r.variance, currency),
            status = tillStatus(r.status),
        )
    }

    override suspend fun handoverOperators() = call {
        rpc.listPosHandoverOperators().map { HandoverOperator(it.userId, it.employeeCode, it.fullName) }
    }

    override suspend fun recent() = call { rpc.listPosTillSessions(20).map { it.toDomain() } }
}

private fun tillStatus(raw: String) = when (raw) {
    "variance_pending" -> TillStatus.VariancePending
    "closed" -> TillStatus.Closed
    else -> TillStatus.Open
}

private fun PosTillSessionRow.toDomain(): TillSession {
    val c = currency.toDomain()
    return TillSession(
        id = id,
        warehouseId = warehouseId,
        currency = c,
        operatorUserId = operatorUserId,
        openingFloat = Money.ofMajor(openingFloat, c),
        status = tillStatus(status),
        expectedCash = expectedCash?.let { Money.ofMajor(it, c) },
        countedCash = countedCash?.let { Money.ofMajor(it, c) },
        variance = variance?.let { Money.ofMajor(it, c) },
        varianceReasonCode = varianceReasonCode,
        openedAtIso = openedAt,
        closedAtIso = closedAt,
    )
}
