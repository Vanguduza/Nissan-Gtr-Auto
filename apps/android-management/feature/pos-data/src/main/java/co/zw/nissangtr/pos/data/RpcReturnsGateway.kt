package co.zw.nissangtr.pos.data

import co.zw.nissangtr.management.rpc.PosReplacementLineInput
import co.zw.nissangtr.management.rpc.PosReturnLineInput
import co.zw.nissangtr.management.rpc.RpcClient
import co.zw.nissangtr.pos.domain.gateway.ReturnsGateway
import co.zw.nissangtr.pos.domain.model.BranchStock
import co.zw.nissangtr.pos.domain.model.InvoiceDetail
import co.zw.nissangtr.pos.domain.model.InvoiceLine
import co.zw.nissangtr.pos.domain.model.Money
import co.zw.nissangtr.pos.domain.model.ReturnDraft
import co.zw.nissangtr.pos.domain.model.WarrantyClaim
import co.zw.nissangtr.pos.domain.model.WarrantySerial

/**
 * Returns, old cores, warranty claims and stock by branch over the phase 6 RPCs. Posting and warranty
 * decisions are approver actions and go through `SalesGateway.approve`.
 */
class RpcReturnsGateway(private val rpc: RpcClient) : ReturnsGateway {
    override suspend fun invoice(invoiceId: String) = call {
        val d = rpc.getPosInvoiceDetail(invoiceId)
        val currency = d.currency.toDomain()
        InvoiceDetail(
            id = d.id,
            documentNumber = d.documentNumber,
            customerId = d.customerId,
            total = Money.ofMajor(d.total, currency),
            amountPaid = Money.ofMajor(d.amountPaid, currency),
            postedAt = d.postedAt,
            tillSessionId = d.tillSessionId,
            lines = d.lines.map {
                InvoiceLine(
                    it.id, it.stockItemId, it.oemPartNumber, it.description, it.uomId, it.qty,
                    Money.ofMajor(it.unitPrice, currency), Money.ofMajor(it.lineTotal, currency), it.isCoreCharge, it.returnableQty,
                )
            },
        )
    }

    override suspend fun draft(draft: ReturnDraft, replacement: Boolean) = call {
        // A swap hands over the same part, same quantity.
        val lines = if (!replacement) null else (rpc.getPosInvoiceDetail(draft.invoiceId).lines).let { sold ->
            draft.lines.mapNotNull { l -> sold.firstOrNull { it.id == l.invoiceLineId }?.let { PosReplacementLineInput(it.stockItemId, it.uomId, l.qty) } }
        }
        rpc.createPosReturnCase(
            draft.invoiceId,
            draft.resolution.rpcValue,
            draft.reasonCode,
            draft.lines.map { PosReturnLineInput(it.invoiceLineId, it.qty, it.condition.rpcValue) },
            draft.notes,
            lines,
            draft.tillSessionId,
        )
    }

    override suspend fun openClaim(invoiceId: String, invoiceLineId: String, serialId: String?, notes: String?) = call {
        rpc.openPosWarrantyClaim(invoiceId, invoiceLineId, serialId, notes)
    }

    override suspend fun findSerial(serial: String) = call {
        rpc.findPosWarrantySerial(serial).map { WarrantySerial(it.id, it.serialNumber, it.stockItemId, it.oemPartNumber) }
    }

    override suspend fun claims(query: String?, status: String?) = call {
        rpc.listPosWarrantyClaims(query, status).map {
            WarrantyClaim(
                it.id, it.documentNumber, it.status, it.resolution, it.salesInvoiceId, it.invoiceNumber, it.stockItemId, it.oemPartNumber,
                it.serialNumber, it.notes, it.rejectReason, it.createdAt, it.decidedAt,
            )
        }
    }

    override suspend fun closeClaim(claimId: String) = call { rpc.closeWarrantyClaim(claimId) }

    override suspend fun stock(stockItemId: String) = call {
        rpc.listPosStockAvailability(stockItemId).map {
            BranchStock(it.warehouseId, it.warehouseCode, it.warehouseName, it.onHand, it.reserved, it.available, it.transferIncoming)
        }
    }
}
