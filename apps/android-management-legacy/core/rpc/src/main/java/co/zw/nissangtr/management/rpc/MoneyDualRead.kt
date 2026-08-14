package co.zw.nissangtr.management.rpc

import kotlin.math.ceil
import kotlin.math.floor

/**
 * H4 / B-MONEY-1 dual-read helpers for Android management POS.
 *
 * Mirrors `@gtr/shared` `preferAmountMinor` / `displayMajorFromDual` /
 * `displayUnitPriceMajor` / `displayLineTotalMajor` / `sumPreferAmountMinor`.
 * Prefer `*_minor` when present; fall back to major NUMERIC. Never invents
 * payable amounts — callers supply DB/API values only.
 */
object MoneyDualRead {

    /** Minor units per major for USD / ZiG (both 2 decimals today). */
    const val MINOR_PER_MAJOR: Int = 100

    /**
     * Convert a decimal major-unit amount to minor units.
     * Rejects non-finite values; rounds half-away-from-zero to nearest minor.
     */
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

    /** Convert minor units back to major for display / legacy RPC bridge. */
    fun fromAmountMinor(
        amountMinor: Long,
        @Suppress("UNUSED_PARAMETER") currency: CurrencyCode = CurrencyCode.USD,
    ): Double = amountMinor.toDouble() / MINOR_PER_MAJOR

    /**
     * Dual-read: prefer [amountMinor] when present; else convert [amountMajor].
     * @throws IllegalArgumentException when both are absent
     */
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

    /** Display major from dual-read row (minor wins when present). */
    fun displayMajorFromDual(
        amountMinor: Long?,
        amountMajor: Double?,
        currency: CurrencyCode = CurrencyCode.USD,
    ): Double = fromAmountMinor(
        preferAmountMinor(amountMinor, amountMajor, currency),
        currency,
    )

    /** Display major for a dual-read unit price. */
    fun displayUnitPriceMajor(
        unitPrice: Double,
        unitPriceMinor: Long?,
        currency: CurrencyCode = CurrencyCode.USD,
    ): Double = displayMajorFromDual(unitPriceMinor, unitPrice, currency)

    /** Display major for a dual-read line total. */
    fun displayLineTotalMajor(
        lineTotal: Double,
        lineTotalMinor: Long?,
        currency: CurrencyCode = CurrencyCode.USD,
    ): Double = displayMajorFromDual(lineTotalMinor, lineTotal, currency)

    /**
     * Sum dual-read money rows in minor units (H4 POS cart totals).
     * Each row is (amountMinor?, amountMajor?).
     */
    fun sumPreferAmountMinor(
        rows: List<Pair<Long?, Double?>>,
        currency: CurrencyCode = CurrencyCode.USD,
    ): Long = rows.sumOf { (minor, major) ->
        preferAmountMinor(minor, major, currency)
    }
}

/** H4 dual-read: unit price display for a POS cart line. */
fun PosCartLineSummary.displayUnitPrice(currency: CurrencyCode): Double =
    MoneyDualRead.displayUnitPriceMajor(unitPrice, unitPriceMinor, currency)

/** H4 dual-read: line total display for a POS cart line. */
fun PosCartLineSummary.displayLineTotal(currency: CurrencyCode): Double =
    MoneyDualRead.displayLineTotalMajor(lineTotal, lineTotalMinor, currency)
