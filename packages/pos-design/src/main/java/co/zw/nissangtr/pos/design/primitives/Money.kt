package co.zw.nissangtr.pos.design.primitives

import androidx.compose.runtime.Immutable
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * Supported transactional currencies in Zimbabwe deployment (Blueprint §2, §5.11, Delta D-006 / SYS-16).
 */
enum class PosCurrency(val code: String, val symbol: String) {
    USD("USD", "$"),
    ZIG("ZiG", "ZiG ");
}

/**
 * Value type for monetary amounts with mandatory explicit currency (Blueprint §5.11 / SYS-16).
 * Represented in micro-units (1/1,000,000) for lossless arithmetic.
 */
@Immutable
data class Money(
    val amountMicros: Long,
    val currency: PosCurrency = PosCurrency.USD,
) : Comparable<Money> {

    val amountMajor: Double
        get() = amountMicros / 1_000_000.0

    operator fun plus(other: Money): Money {
        require(currency == other.currency) {
            "Cannot add mismatched currencies: $currency and ${other.currency}"
        }
        return Money(amountMicros + other.amountMicros, currency)
    }

    operator fun minus(other: Money): Money {
        require(currency == other.currency) {
            "Cannot subtract mismatched currencies: $currency and ${other.currency}"
        }
        return Money(amountMicros - other.amountMicros, currency)
    }

    operator fun times(factor: Int): Money = Money(amountMicros * factor, currency)

    override fun compareTo(other: Money): Int {
        require(currency == other.currency) {
            "Cannot compare mismatched currencies: $currency and ${other.currency}"
        }
        return amountMicros.compareTo(other.amountMicros)
    }

    companion object {
        val ZERO_USD = Money(0L, PosCurrency.USD)
        val ZERO_ZIG = Money(0L, PosCurrency.ZIG)

        fun fromMajor(amount: Double, currency: PosCurrency = PosCurrency.USD): Money {
            val micros = Math.round(amount * 1_000_000.0)
            return Money(micros, currency)
        }

        fun fromCents(cents: Long, currency: PosCurrency = PosCurrency.USD): Money {
            return Money(cents * 10_000L, currency)
        }
    }
}

/**
 * Standard locale formatter for money in POS ensuring tabular alignment (Blueprint §5.11).
 */
object PosMoneyFormatter {
    private val symbols = DecimalFormatSymbols(Locale.US).apply {
        groupingSeparator = ','
        decimalSeparator = '.'
    }
    private val format = DecimalFormat("#,##0.00", symbols)

    fun format(money: Money, includeCode: Boolean = true): String {
        val majorStr = format.format(money.amountMajor)
        return when (money.currency) {
            PosCurrency.USD -> if (includeCode) "USD $majorStr" else "$$majorStr"
            PosCurrency.ZIG -> if (includeCode) "ZiG $majorStr" else "ZiG $majorStr"
        }
    }
}
