package co.zw.nissangtr.management.pos

import co.zw.nissangtr.management.pos.offline.LocalCartLine
import co.zw.nissangtr.management.rpc.CurrencyCode
import co.zw.nissangtr.management.rpc.MoneyDualRead
import co.zw.nissangtr.management.rpc.PosCartLineSummary

/**
 * Pure cart-line math pulled out of [PosViewModel] — the safe, low-risk slice of the
 * cart-line/checkout extraction recommended in
 * `docs/audit/2026-08-04-master-audit/structural-critique.md` §1. Deliberately stateless and
 * coroutine-free (no `RpcClient`, no `StateFlow`) so it is unit-testable without a fake
 * network client, and so this pass does not risk the offline-sync / manager-reauth /
 * companion-pairing flows that still live in [PosViewModel]'s single `StateFlow`.
 *
 * A full split into `PosCartViewModel` / `PosCompanionViewModel` / `PosManagerAuthViewModel` /
 * `PosQuotationViewModel` (the audit's full recommendation) is deferred — see the TODO on
 * [PosViewModel] for why that is not attempted in this layout-focused pass.
 */
object PosCartLineOps {

    /**
     * Sum of non-core-charge line totals — the customer-facing cart total shown in the UI.
     * H4 dual-read: prefers `lineTotalMinor` when present (parity with web `sumPosCartLinesMajor`).
     */
    fun cartTotal(
        lines: List<PosCartLineSummary>,
        currency: CurrencyCode = CurrencyCode.USD,
    ): Double {
        val saleable = lines.filter { !it.isCoreCharge }
        val sumMinor = MoneyDualRead.sumPreferAmountMinor(
            saleable.map { it.lineTotalMinor to it.lineTotal },
            currency,
        )
        return MoneyDualRead.fromAmountMinor(sumMinor, currency)
    }

    /** Maps local offline cart lines to the same summary shape the online RPC path returns. */
    fun toSummaries(lines: List<LocalCartLine>): List<PosCartLineSummary> =
        lines.map { ol ->
            PosCartLineSummary(
                id = ol.id,
                stockItemId = ol.stockItemId,
                oemPartNumber = ol.oemPartNumber,
                qty = ol.qty,
                unitPrice = ol.unitPrice,
                lineTotal = ol.lineTotal,
                isCoreCharge = ol.isCoreCharge,
            )
        }

    /**
     * Applies a qty delta to an offline local line (and its linked core-charge line, if any)
     * in place, removing lines whose qty reaches zero. Mirrors the exact behavior previously
     * inlined in `PosViewModel.bumpLineQty`'s offline branch.
     */
    fun applyOfflineQtyDelta(
        lines: MutableList<LocalCartLine>,
        targetId: String,
        targetStockItemId: String,
        delta: Double,
    ) {
        val idx = lines.indexOfFirst { it.id == targetId }
        if (idx < 0) return
        val cur = lines[idx]
        val next = (cur.qty + delta).coerceAtLeast(0.0)
        if (next <= 0) {
            lines.removeAll { it.id == targetId || (it.isCoreCharge && it.stockItemId == targetStockItemId) }
        } else {
            lines[idx] = cur.copy(qty = next, lineTotal = cur.unitPrice * next)
            lines.replaceAll { l ->
                if (l.isCoreCharge && l.stockItemId == targetStockItemId) {
                    l.copy(qty = next, lineTotal = l.unitPrice * next)
                } else {
                    l
                }
            }
        }
    }
}
