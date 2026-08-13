package co.zw.nissangtr.customer.rpc

import kotlin.math.round

/**
 * D-57 checkout display — mirrors `@gtr/payments` `buildCheckoutDisplay`.
 *
 * Browse/cart stays USD; ZiG only at pay step with ops daily rate + optional
 * `fxRateId`. Payable is always [MoneyMinor]-style (amountMinor + currency).
 * Never invents rates or payable amounts — callers supply usdMinor + ops rate.
 */
enum class CheckoutPayMethod {
    CONTIPAY,
    PAYNOW,
    ECOCASH,
    CASH,
}

data class MoneyMinorDto(
    val amountMinor: Long,
    val currency: CurrencyCode,
    val fxRateId: String? = null,
)

data class CheckoutDisplay(
    val browseCurrency: CurrencyCode = CurrencyCode.USD,
    val payCurrency: CurrencyCode,
    val payable: MoneyMinorDto,
    val fxRateId: String? = null,
    /** Indicative ZiG for COD / USD confirm when rate known. */
    val indicativeZigMinor: Long? = null,
)

object CheckoutDisplayBuilder {

    /**
     * @param usdMinor browse/cart total in USD minor units (from dual-read, not invented)
     * @param payMethod EcoCash → ZiG wallet; others stay USD
     * @param zigRatePerUsd ops daily ZiG per 1 USD; required when [payMethod] is EcoCash
     * @param fxRateId `daily_exchange_rates.id` when settling ZiG
     * @throws IllegalArgumentException when EcoCash/ZiG path lacks a positive rate
     */
    fun build(
        usdMinor: Long,
        payMethod: CheckoutPayMethod,
        zigRatePerUsd: Double? = null,
        fxRateId: String? = null,
    ): CheckoutDisplay {
        val zigWallet = payMethod == CheckoutPayMethod.ECOCASH
        if (zigWallet) {
            val rate = zigRatePerUsd
            require(rate != null && rate.isFinite() && rate > 0.0) {
                "Daily ZiG rate required for EcoCash checkout"
            }
            val zigMinor = round(usdMinor.toDouble() * rate).toLong()
            return CheckoutDisplay(
                browseCurrency = CurrencyCode.USD,
                payCurrency = CurrencyCode.ZIG,
                payable = MoneyMinorDto(
                    amountMinor = zigMinor,
                    currency = CurrencyCode.ZIG,
                    fxRateId = fxRateId,
                ),
                fxRateId = fxRateId,
            )
        }
        val indicative =
            if (zigRatePerUsd != null && zigRatePerUsd.isFinite() && zigRatePerUsd > 0.0) {
                round(usdMinor.toDouble() * zigRatePerUsd).toLong()
            } else {
                null
            }
        return CheckoutDisplay(
            browseCurrency = CurrencyCode.USD,
            payCurrency = CurrencyCode.USD,
            payable = MoneyMinorDto(
                amountMinor = usdMinor,
                currency = CurrencyCode.USD,
                fxRateId = null,
            ),
            fxRateId = null,
            indicativeZigMinor = indicative,
        )
    }

    /**
     * D-57 settle helper: ZiG settlement from USD minor + ops rate.
     * Fail-closed when rate missing/invalid.
     */
    fun buildZigSettlement(
        usdMinor: Long,
        zigRatePerUsd: Double?,
        fxRateId: String?,
    ): CheckoutDisplay = build(
        usdMinor = usdMinor,
        payMethod = CheckoutPayMethod.ECOCASH,
        zigRatePerUsd = zigRatePerUsd,
        fxRateId = fxRateId,
    )
}
