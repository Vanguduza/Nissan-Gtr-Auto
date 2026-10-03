package co.zw.nissangtr.pos.domain.model

/** A card machine (`pos_card_terminals`, adapter `android_intent_v1`). */
data class CardTerminal(
    val id: String,
    val label: String,
    val acquirer: String?,
    val config: Map<String, String?>,
)

/**
 * One card-machine operation (`pos_card_terminal_attempts`). Card data never reaches the POS: only
 * references (transaction id, RRN / auth code, last four digits).
 */
data class TerminalAttempt(
    val attemptId: String,
    /** purchase, refund, reversal. */
    val operation: String,
    /** initiated, approved, declined, cancelled, unknown, failed, settled, reversed — or recovery_required. */
    val status: String,
    val amount: Money,
    val terminalLabel: String?,
    val cardLast4: String?,
    val cardScheme: String?,
    val transactionId: String?,
    val responseMessage: String?,
    val orderId: String?,
    val splitLegId: String?,
    val invoiceId: String?,
    val finalizationError: String?,
) {
    /** The machine charged the card but the sale is not posted yet: finish it or reverse it, never charge again. */
    val approvedUnposted: Boolean get() = operation == "purchase" && (status == "approved" || status == "recovery_required")
    val unresolved: Boolean get() = status in setOf("initiated", "unknown") || approvedUnposted
}

/** `list_pos_card_terminal_recovery` row. */
data class TerminalRecoveryItem(
    val attemptId: String,
    val operation: String,
    val status: String,
    val terminalLabel: String?,
    val orderId: String?,
    val amount: Money,
    val transactionId: String?,
    val cardLast4: String?,
    val message: String?,
    val updatedAtIso: String,
)

/** This tablet's card machine: the one chosen in Settings, and whether this device is paired with it. */
data class TerminalSetup(
    val terminals: List<CardTerminal>,
    val selected: CardTerminal?,
    /** The card machine app is installed on this device. */
    val appInstalled: Boolean,
    /** An admin registered this device's evidence key for [selected]. */
    val paired: Boolean,
)
