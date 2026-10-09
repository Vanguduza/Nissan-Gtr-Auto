package co.zw.nissangtr.bridges.terminal

/**
 * Card terminal (ECR) bridge — Bridge-First: the POS never talks to a payment terminal directly.
 *
 * Adapter `android_intent_v1`: the acquirer's terminal app on this device is started with an
 * Android intent and returns the outcome as intent extras. Card data (PAN, PIN, track, EMV) never
 * reaches the POS: only references come back (transaction id, RRN, auth code, last four digits).
 */
enum class TerminalOperation(val wire: String) { Purchase("purchase"), Refund("refund"), Reversal("reversal"), Status("status") }

/** Exactly the outcomes `record_pos_card_terminal_result` accepts. */
enum class TerminalOutcome(val wire: String) { Approved("approved"), Declined("declined"), Cancelled("cancelled"), Unknown("unknown"), Failed("failed") }

/**
 * `pos_card_terminals.adapter_config` for `android_intent_v1`. Extra names default to the common
 * ones; an acquirer app that uses others sets them per terminal.
 */
data class IntentTerminalConfig(
    val packageName: String,
    val purchaseAction: String,
    val refundAction: String? = null,
    val statusAction: String? = null,
    val reversalAction: String? = null,
    val amountMinorKey: String = "amount_minor",
    val currencyKey: String = "currency",
    val referenceKey: String = "reference",
    val operationKey: String = "operation",
    val originalTransactionIdKey: String = "original_transaction_id",
    val resultStatusKey: String = "result_status",
    val resultTransactionIdKey: String = "transaction_id",
    val resultRrnKey: String = "rrn",
    val resultAuthCodeKey: String = "authorization_code",
    val resultLast4Key: String = "card_last4",
    val resultSchemeKey: String = "card_scheme",
    val resultResponseCodeKey: String = "response_code",
    val resultResponseMessageKey: String = "response_message",
) {
    fun actionFor(op: TerminalOperation): String? = when (op) {
        TerminalOperation.Purchase -> purchaseAction
        TerminalOperation.Refund -> refundAction
        TerminalOperation.Reversal -> reversalAction
        TerminalOperation.Status -> statusAction
    }?.takeIf { it.isNotBlank() }

    companion object {
        /** From the server's `adapter_config` object (string values only). */
        fun from(config: Map<String, String?>): IntentTerminalConfig? {
            val pkg = config["package_name"]?.trim().orEmpty()
            val purchase = config["purchase_action"]?.trim().orEmpty()
            if (pkg.isEmpty() || purchase.isEmpty()) return null
            fun key(name: String, default: String) = config[name]?.trim()?.takeIf { it.isNotEmpty() } ?: default
            return IntentTerminalConfig(
                packageName = pkg,
                purchaseAction = purchase,
                refundAction = config["refund_action"]?.trim(),
                statusAction = config["status_action"]?.trim(),
                reversalAction = config["reversal_action"]?.trim(),
                amountMinorKey = key("amount_minor_key", "amount_minor"),
                currencyKey = key("currency_key", "currency"),
                referenceKey = key("reference_key", "reference"),
                operationKey = key("operation_key", "operation"),
                originalTransactionIdKey = key("original_transaction_id_key", "original_transaction_id"),
                resultStatusKey = key("result_status_key", "result_status"),
                resultTransactionIdKey = key("result_transaction_id_key", "transaction_id"),
                resultRrnKey = key("result_rrn_key", "rrn"),
                resultAuthCodeKey = key("result_auth_code_key", "authorization_code"),
                resultLast4Key = key("result_last4_key", "card_last4"),
                resultSchemeKey = key("result_scheme_key", "card_scheme"),
                resultResponseCodeKey = key("result_response_code_key", "response_code"),
                resultResponseMessageKey = key("result_response_message_key", "response_message"),
            )
        }
    }
}

data class TerminalRequest(
    val config: IntentTerminalConfig,
    val operation: TerminalOperation,
    val amountMinor: Long,
    val currency: String,
    /** Our reference (`external_ref`): the terminal echoes it so the bank's record matches ours. */
    val reference: String,
    val originalTransactionId: String? = null,
)

data class TerminalResult(
    val outcome: TerminalOutcome,
    val transactionId: String? = null,
    val rrn: String? = null,
    val authorizationCode: String? = null,
    val cardLast4: String? = null,
    val cardScheme: String? = null,
    val responseCode: String? = null,
    val responseMessage: String? = null,
)

interface CardTerminalBridge {
    /** Is the acquirer's terminal app installed on this device? */
    fun isAvailable(config: IntentTerminalConfig): Boolean

    /** Runs one terminal operation. Never throws for a terminal answer; a missing answer is [TerminalOutcome.Unknown]. */
    suspend fun run(request: TerminalRequest): TerminalResult
}

/**
 * Maps the terminal app's answer to one outcome. A charge is only "approved" with a status that
 * says so; anything we cannot read is Unknown (it blocks a second charge), never "declined".
 */
object TerminalResultMapper {
    private val approved = setOf("approved", "success", "successful", "ok", "00", "accepted")
    private val declined = setOf("declined", "denied", "rejected", "not_approved")
    private val cancelled = setOf("cancelled", "canceled", "aborted", "user_cancelled", "user_canceled")
    private val failed = setOf("failed", "error", "not_started")

    fun map(launched: Boolean, extras: Map<String, String?>, config: IntentTerminalConfig): TerminalResult {
        // The terminal app never opened: no card was presented, so nothing can have been charged.
        if (!launched) return TerminalResult(TerminalOutcome.Failed, responseMessage = "Card machine app not available on this device.")
        fun v(key: String) = extras[key]?.trim()?.takeIf { it.isNotEmpty() }
        val status = v(config.resultStatusKey)?.lowercase()
        val outcome = when (status) {
            in approved -> TerminalOutcome.Approved
            in declined -> TerminalOutcome.Declined
            in cancelled -> TerminalOutcome.Cancelled
            in failed -> TerminalOutcome.Failed
            else -> TerminalOutcome.Unknown
        }
        val last4 = v(config.resultLast4Key)?.filter { it.isDigit() }?.takeLast(4)?.takeIf { it.length == 4 }
        val result = TerminalResult(
            outcome = outcome,
            transactionId = v(config.resultTransactionIdKey),
            rrn = v(config.resultRrnKey),
            authorizationCode = v(config.resultAuthCodeKey),
            cardLast4 = last4,
            cardScheme = v(config.resultSchemeKey),
            responseCode = v(config.resultResponseCodeKey),
            responseMessage = v(config.resultResponseMessageKey)?.take(240),
        )
        // "Approved" without the references the server requires cannot be proven: treat as Unknown.
        return if (outcome == TerminalOutcome.Approved && (result.transactionId == null || (result.rrn == null && result.authorizationCode == null))) {
            result.copy(outcome = TerminalOutcome.Unknown)
        } else {
            result
        }
    }
}
