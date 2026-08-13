package co.zw.nissangtr.customer.rpc

import kotlin.math.ceil
import kotlin.math.floor

/**
 * H4 / B-MONEY-1 dual-read helpers for Android customer cart.
 *
 * Mirrors `@gtr/shared` and management `MoneyDualRead`. Prefer `*_minor` when
 * present; fall back to major NUMERIC. Never invents payable amounts — callers
 * supply DB/API values only.
 */
object MoneyDualRead {

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

/** H4 dual-read: unit price display for a customer cart line. */
fun CartLineSummary.displayUnitPrice(currency: CurrencyCode): Double? {
    val major = unitPrice ?: unitPriceUsd ?: return null
    return MoneyDualRead.displayUnitPriceMajor(major, unitPriceMinor, currency)
}

/** H4 dual-read: line total display (falls back to dual-read unit × qty). */
fun CartLineSummary.displayLineTotal(currency: CurrencyCode): Double? {
    if (lineTotalMinor != null || lineTotal != null) {
        val major = lineTotal ?: 0.0
        return MoneyDualRead.displayLineTotalMajor(major, lineTotalMinor, currency)
    }
    val unit = displayUnitPrice(currency) ?: return null
    return unit * qty
}

/** H4 dual-read: cart subtotal via minor units when present. */
fun CartSummary.displaySubtotal(): Double {
    val rows = lines.map { line ->
        val major = line.lineTotal
            ?: ((line.unitPrice ?: line.unitPriceUsd)?.times(line.qty))
        line.lineTotalMinor to major
    }
    if (rows.all { it.first == null && it.second == null }) return 0.0
    return MoneyDualRead.fromAmountMinor(
        MoneyDualRead.sumPreferAmountMinor(rows, currency),
        currency,
    )
}
