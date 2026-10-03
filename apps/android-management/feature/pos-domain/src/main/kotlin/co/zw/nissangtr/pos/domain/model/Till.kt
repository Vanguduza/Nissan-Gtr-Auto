package co.zw.nissangtr.pos.domain.model

/** `pos_till_sessions.status`: a blind count with a variance waits for a manager before closing. */
enum class TillStatus { Open, VariancePending, Closed }

/** A cash drawer session (`pos_till_sessions`). Expected cash and variance are server figures. */
data class TillSession(
    val id: String,
    val warehouseId: String,
    val currency: CurrencyCode,
    val operatorUserId: String,
    val openingFloat: Money,
    val status: TillStatus,
    val expectedCash: Money?,
    val countedCash: Money?,
    val variance: Money?,
    val varianceReasonCode: String?,
    val openedAtIso: String,
    val closedAtIso: String?,
)

/** What the server worked out from a blind count (`close_pos_till_session`). */
data class TillCloseResult(
    val sessionId: String,
    val expected: Money,
    val counted: Money,
    val variance: Money,
    val status: TillStatus,
)

/** `pos_till_cash_movement_kind`. Everything but [CashIn] needs a manager. */
enum class CashMovementKind(val rpcValue: String) {
    CashIn("cash_in"),
    CashOut("cash_out"),
    PettyCash("petty_cash"),
    BankDrop("bank_drop"),
    CashRefund("cash_refund"),
    ;

    val needsManager: Boolean get() = this != CashIn

    companion object {
        /** Cash-out reason codes (`pos_approval_reason_codes`, action `cash_out`) → movement kind. */
        fun forCashOutReason(code: String): CashMovementKind = when (code) {
            "petty_cash" -> PettyCash
            "bank_drop" -> BankDrop
            "customer_refund" -> CashRefund
            else -> CashOut
        }
    }
}

/** A configured reason for a governed action (`list_pos_approval_reasons`). */
data class ReasonCode(val code: String, val label: String, val requiresNotes: Boolean)

/** Governed actions that carry reason codes. */
object ReasonAction {
    const val CASH_OUT = "cash_out"
    const val TILL_VARIANCE = "till_variance"
}

/** Cash-in reasons are not governed (no manager); these are the ones the drawer accepts. */
val CASH_IN_REASONS: List<ReasonCode> = listOf(
    ReasonCode("float_top_up", "Float top-up", false),
    ReasonCode("change_from_safe", "Change from safe", false),
    ReasonCode("other_cash_in", "Other cash in", true),
)

/** Staff the till can be handed to (`list_pos_handover_operators`). */
data class HandoverOperator(val userId: String, val employeeCode: String, val fullName: String)

/** One line of a blind drawer count; [denominationMinor] in minor units of the till currency. */
data class DenominationCount(val denominationMinor: Long, val quantity: Int) {
    val amountMinor: Long get() = denominationMinor * quantity
}

/** Notes and coins the drawer is counted in, largest first (minor units). Same set as the web POS. */
fun denominationsFor(currency: CurrencyCode): List<Long> = when (currency) {
    CurrencyCode.ZIG -> listOf(20000, 10000, 5000, 2000, 1000, 500, 200, 100, 50, 25, 10)
    else -> listOf(10000, 5000, 2000, 1000, 500, 200, 100, 50, 25, 10, 5, 1)
}
