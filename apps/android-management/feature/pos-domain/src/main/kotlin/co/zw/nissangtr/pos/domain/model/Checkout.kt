package co.zw.nissangtr.pos.domain.model

/** Digital providers: the customer pays on their phone; the provider's webhook settles the order. */
enum class DigitalProvider(val rpcValue: String) { EcoCash("ecocash"), Paynow("paynow"), ContiPay("contipay") }

/** Paynow (`paynow_method`) and ContiPay (`contipay_method`) payment methods. */
enum class ProviderMethod(val rpcValue: String, val paynow: Boolean, val contipay: Boolean) {
    EcoCash("ecocash", true, true),
    OneMoney("onemoney", true, false),
    InnBucks("innbucks", true, false),
    Visa("visa", true, true),
    ZimSwitch("zimswitch", false, true),
}

/** Blueprint §10.7: every attempt ends as exactly one of these. */
enum class TenderOutcome { Approved, Declined, Cancelled, Error, Unknown }

/** Tenders the counter settles itself (`settle_pos_commerce_tenders`); digital money uses a provider. */
val Tender.manual: Boolean get() = this == Tender.Cash || this == Tender.Bank || this == Tender.StoreCredit

data class PaymentExceptionInfo(val code: String, val detail: String?, val resolvedAtIso: String?, val resolution: String?, val createdAtIso: String)

/** `get_pos_payment_status`: the server's view of a reserved checkout. The total is the server's. */
data class PaymentStatus(
    val orderId: String,
    val state: String,
    val total: Money,
    val reservationExpiresAtIso: String?,
    val activeProvider: String?,
    val providerStatus: String?,
    val providerFailure: String?,
    val settledProvider: String?,
    val reference: String?,
    val salesInvoiceId: String?,
    val paymentException: String?,
    val exceptions: List<PaymentExceptionInfo> = emptyList(),
) {
    val settled: Boolean get() = salesInvoiceId != null && state in setOf("paid", "allocation_pending", "dispatch_ready", "delivered", "account_invoiced")
    /** Money captured but the sale did not finish: never charge again; repair from recovery. */
    val capturedUnfinished: Boolean get() = state == "allocation_pending" && salesInvoiceId == null
    val inFlight: Boolean get() = state == "payment_processing"
}

data class ProviderStart(val intentId: String, val checkoutUrl: String?, val message: String?)

/** A digital attempt in flight, timed from when it was started (clock comes from the store). */
data class ProviderAttempt(val provider: DigitalProvider, val intentId: String, val checkoutUrl: String?, val startedAtMs: Long)

/** `list_pos_payment_recovery` row. */
data class RecoveryItem(
    val orderId: String,
    val state: String,
    val total: Money,
    val activeProvider: String?,
    val salesInvoiceId: String?,
    val updatedAtIso: String,
    val openExceptions: Int,
)

/** `list_pos_pickup_orders` row: paid (or on account) and waiting for the customer. */
data class PickupOrder(
    val orderId: String,
    val documentNumber: String?,
    val customerName: String?,
    val state: String,
    val total: Money,
    val settledProvider: String?,
    val updatedAtIso: String,
)
