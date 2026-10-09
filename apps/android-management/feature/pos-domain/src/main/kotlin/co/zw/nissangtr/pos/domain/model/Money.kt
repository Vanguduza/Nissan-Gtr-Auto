package co.zw.nissangtr.pos.domain.model

/** ISO currency code carried on every money value (D-006: explicit currency, never assumed). */
@JvmInline
value class CurrencyCode(val code: String) {
    init {
        require(code.length == 3 && code.all { it.isUpperCase() }) { "ISO 4217 code expected: $code" }
    }

    companion object {
        val USD = CurrencyCode("USD")
        val ZIG = CurrencyCode("ZIG")
    }
}

/**
 * Backend-authoritative money in minor units. The UI renders it; it never derives a price, a tax
 * or a total that the server did not return (Blueprint §14.14).
 */
data class Money(val minor: Long, val currency: CurrencyCode) {
    companion object {
        fun ofMajor(amount: Double, currency: CurrencyCode): Money =
            Money(Math.round(amount * 100.0), currency)

        fun zero(currency: CurrencyCode): Money = Money(0, currency)
    }
}
