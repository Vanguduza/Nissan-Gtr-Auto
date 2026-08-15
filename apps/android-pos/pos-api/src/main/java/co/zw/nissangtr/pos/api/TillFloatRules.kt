package co.zw.nissangtr.pos.api

/**
 * Cash-sales till float uses CoA **1120** (Cash Sales Till).
 * Petty cash imprest is **1110** — finance-only; never open/close from POS counter.
 */
object TillFloatRules {
    const val CASH_SALES_TILL = "1120"
    const val PETTY_CASH = "1110"

    fun validateOpenAccount(accountCode: String): String? {
        val code = accountCode.trim()
        if (code.isEmpty()) return "account code required"
        if (code == PETTY_CASH) {
            return "petty cash $PETTY_CASH is finance-only; till float is $CASH_SALES_TILL"
        }
        if (code != CASH_SALES_TILL) {
            return "POS till float must use account $CASH_SALES_TILL (got $code)"
        }
        return null
    }

    fun requireOpenAccount(accountCode: String) {
        validateOpenAccount(accountCode)?.let { throw IllegalArgumentException(it) }
    }

    fun validateOpeningBalance(major: Double): String? {
        if (major < 0.0) return "opening balance cannot be negative"
        return null
    }

    fun validatePhysicalCount(major: Double): String? {
        if (major < 0.0) return "physical count cannot be negative"
        return null
    }
}

data class OpenTillFloatRequest(
    val accountCode: String = TillFloatRules.CASH_SALES_TILL,
    val currency: String = "USD",
    val periodStart: String,
    val periodEnd: String,
    val openingBalance: Double,
    val notes: String? = null,
) {
    fun validated(): OpenTillFloatRequest {
        TillFloatRules.requireOpenAccount(accountCode)
        TillFloatRules.validateOpeningBalance(openingBalance)?.let {
            throw IllegalArgumentException(it)
        }
        require(periodStart.isNotBlank() && periodEnd.isNotBlank()) { "period dates required" }
        return this
    }
}

data class CloseTillFloatRequest(
    val periodId: String,
    val physicalCount: Double,
    val notes: String? = null,
) {
    fun validated(): CloseTillFloatRequest {
        require(periodId.isNotBlank()) { "period id required" }
        TillFloatRules.validatePhysicalCount(physicalCount)?.let {
            throw IllegalArgumentException(it)
        }
        return this
    }
}

data class TillFloatPeriod(
    val id: String,
    val accountCode: String,
    val currency: String,
    val status: String,
    val openingBalance: Double? = null,
)
