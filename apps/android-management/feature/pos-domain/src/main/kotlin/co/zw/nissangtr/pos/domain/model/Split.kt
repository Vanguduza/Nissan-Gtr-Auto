package co.zw.nissangtr.pos.domain.model

/** Tenders a part payment can take at the counter (`add_pos_split_payment_leg`). */
enum class SplitTender(val rpcValue: String, val receipt: Tender) {
    Cash("cash", Tender.Cash),
    Bank("bank", Tender.Bank),
    StoreCredit("store_credit", Tender.StoreCredit),
}

/** Who carries provider / transfer fees on a refund of a captured part. */
enum class RefundFeePolicy(val rpcValue: String) {
    ManualReview("manual_review"),
    BusinessAbsorbs("business_absorbs"),
    CustomerBears("customer_bears"),
}

data class SplitLeg(
    val id: String,
    val sequenceNo: Int,
    /** `payment_tender` value; cash, bank and store credit at the counter. */
    val tender: String,
    val amount: Money,
    val status: String,
    val reference: String?,
    val statusDetail: String?,
    val applied: Money?,
    val refundRequired: Money?,
)

data class SplitRefund(
    val id: String,
    val legId: String,
    /** review → pending (approved) → settled, or failed. */
    val status: String,
    val gross: Money,
    val feePolicy: String,
    val netToCustomer: Money?,
    val providerRef: String?,
    val failureReason: String?,
    val notes: String?,
) {
    val open: Boolean get() = status != "settled" && status != "cancelled"
}

/** `pos_split_payment_payload`: a part-paid sale. Every figure is the server's. */
data class SplitSession(
    val sessionId: String,
    val orderId: String,
    val status: String,
    val total: Money,
    val received: Money,
    val pending: Money,
    val balanceDue: Money,
    val availableToAllocate: Money,
    val finalInvoiceId: String?,
    val finalizationError: String?,
    val legs: List<SplitLeg>,
    val refunds: List<SplitRefund>,
) {
    /** Money has been received or is in flight: going back to the sale would need a refund. */
    val hasMoney: Boolean get() = received.minor > 0 || pending.minor > 0
    /** Still taking parts. */
    val open: Boolean get() = status in setOf("open", "partially_captured", "leg_pending")
    /** Posted: the receipt can be printed. */
    val posted: Boolean get() = finalInvoiceId != null && status in setOf("settled", "refund_review", "refund_pending")
    val unposted: Boolean get() = status == "finalization_failed"
    val retryable: Boolean get() = status == "finalization_failed" || status == "fully_committed"
    val cancellable: Boolean get() = status in setOf("open", "partially_captured", "finalization_failed")
    val owedBack: Money get() = Money(refunds.filter { it.open }.sumOf { it.gross.minor }, total.currency)
}

/** `list_pos_split_payment_recovery` row. */
data class SplitRecoveryItem(
    val documentNumber: String?,
    val customerName: String?,
    val updatedAtIso: String,
    val session: SplitSession,
)

/** A refund step on a captured part: manager or finance approves, records it paid, or marks it failed. */
sealed interface SplitRefundStep {
    data class Approve(val feePolicy: RefundFeePolicy, val customerFee: Money?, val notes: String?) : SplitRefundStep
    data class Complete(val providerRef: String, val notes: String?) : SplitRefundStep
    data class Fail(val reason: String) : SplitRefundStep
}
