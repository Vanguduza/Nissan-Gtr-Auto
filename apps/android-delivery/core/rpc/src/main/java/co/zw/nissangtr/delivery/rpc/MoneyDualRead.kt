package co.zw.nissangtr.delivery.rpc

import kotlin.math.ceil
import kotlin.math.floor

/**
 * H4 / B-MONEY-1 dual-read helpers for Android delivery (COD / settlement).
 *
 * Mirrors `@gtr/shared` and customer/management `MoneyDualRead`. Prefer `*_minor`
 * when present; fall back to major NUMERIC. Never invents payable amounts —
 * callers supply DB/API values only.
 */
object MoneyDualRead {

    /** Minor units per major for USD / ZiG (both 2 decimals today). */
    const val MINOR_PER_MAJOR: Int = 100

    fun toAmountMinor(
        amountMajor: Double,
        @Suppress("UNUSED_PARAMETER") currency: CurrencyCode = CurrencyCode.USD,
    ): Long {
        require(amountMajor.isFinite()) { "amountMajor must be finite" }
        val scaled = amountMajor * MINOR_PER_MAJOR
        return if (scaled >= 0.0) {
            floor(scaled + 0.5).toLong()
        } else {
            ceil(scaled - 0.5).toLong()
        }
    }

    fun fromAmountMinor(
        amountMinor: Long,
        @Suppress("UNUSED_PARAMETER") currency: CurrencyCode = CurrencyCode.USD,
    ): Double = amountMinor.toDouble() / MINOR_PER_MAJOR

    fun preferAmountMinor(
        amountMinor: Long?,
        amountMajor: Double?,
        currency: CurrencyCode = CurrencyCode.USD,
    ): Long {
        if (amountMinor != null) return amountMinor
        require(amountMajor != null) {
            "preferAmountMinor: need amountMinor or amountMajor"
        }
        return toAmountMinor(amountMajor, currency)
    }

    fun displayMajorFromDual(
        amountMinor: Long?,
        amountMajor: Double?,
        currency: CurrencyCode = CurrencyCode.USD,
    ): Double = fromAmountMinor(
        preferAmountMinor(amountMinor, amountMajor, currency),
        currency,
    )

    fun displayUnitPriceMajor(
        unitPrice: Double,
        unitPriceMinor: Long?,
        currency: CurrencyCode = CurrencyCode.USD,
    ): Double = displayMajorFromDual(unitPriceMinor, unitPrice, currency)

    fun displayLineTotalMajor(
        lineTotal: Double,
        lineTotalMinor: Long?,
        currency: CurrencyCode = CurrencyCode.USD,
    ): Double = displayMajorFromDual(lineTotalMinor, lineTotal, currency)

    fun sumPreferAmountMinor(
        rows: List<Pair<Long?, Double?>>,
        currency: CurrencyCode = CurrencyCode.USD,
    ): Long = rows.sumOf { (minor, major) ->
        preferAmountMinor(minor, major, currency)
    }
}

/** H4 dual-read: invoice total display for a job settlement. */
fun DeliveryJobSettlement.displayInvoiceTotal(): Double? {
    if (invoiceTotalMinor == null && invoiceTotal == null) return null
    return MoneyDualRead.displayMajorFromDual(invoiceTotalMinor, invoiceTotal, currency)
}

/** H4 dual-read: amount paid display for a job settlement. */
fun DeliveryJobSettlement.displayAmountPaid(): Double? {
    if (amountPaidMinor == null && amountPaid == null) return null
    return MoneyDualRead.displayMajorFromDual(amountPaidMinor, amountPaid, currency)
}

/**
 * H4 dual-read: COD / open balance to collect.
 * Prefers explicit `amountDue*_`; else total − paid in minor units.
 */
fun DeliveryJobSettlement.displayAmountDue(): Double? {
    if (amountDueMinor != null || amountDue != null) {
        return MoneyDualRead.displayMajorFromDual(amountDueMinor, amountDue, currency)
    }
    val total = displayInvoiceTotal() ?: return null
    val paid = displayAmountPaid() ?: 0.0
    val dueMinor = MoneyDualRead.toAmountMinor(total, currency) -
        MoneyDualRead.toAmountMinor(paid, currency)
    return MoneyDualRead.fromAmountMinor(dueMinor, currency)
}

/** Format dual-read major for driver UI (currency + 2dp). */
fun DeliveryJobSettlement.formatAmountDueLabel(): String? {
    val due = displayAmountDue() ?: return null
    if (due <= 0.0) return null
    return "COD ${currency.rpcValue} %.2f".format(due)
}
