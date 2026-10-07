package co.zw.nissangtr.delivery.rpc

/** Mirrors `public.currency_code` — never assume USD silently. */
enum class CurrencyCode(val rpcValue: String) {
    USD("USD"),
    ZIG("ZIG"),
}

/** Mirrors `public.driver_presence_status`. */
enum class DriverPresenceStatus(val rpcValue: String) {
    AVAILABLE("available"),
    ON_DUTY("on_duty"),
    BREAK("break"),
    OFFLINE("offline"),
}

/** Mirrors `public.delivery_job_status`. */
enum class DeliveryJobStatus(val rpcValue: String) {
    PENDING("pending"),
    DISPATCHED("dispatched"),
    COMPLETED("completed"),
    FAILED("failed"),
}

/** Mirrors `public.delivery_failure_reason`. */
enum class DeliveryFailureReason(val rpcValue: String) {
    CUSTOMER_ABSENT("customer_absent"),
    REFUSED("refused"),
    WRONG_ADDRESS("wrong_address"),
    DAMAGED("damaged"),
    OTHER("other"),
}

/** Staff role gate — delivery app requires `driver` (admin may also use for QA). */
object DriverStaffRoles {
    val ALLOWED: Set<String> = setOf("driver", "admin")

    fun allows(roles: Collection<String>): Boolean =
        roles.any { it in ALLOWED }
}

/**
 * Optional COD / invoice settlement on a delivery job (H4 dual-read).
 *
 * Prefer `*_minor` when present; majors are legacy NUMERIC bridges.
 * Never invent payable amounts — callers supply DB/API values only.
 * Live path: [RpcNames.GET_DELIVERY_JOB_SETTLEMENT] (driver-scoped DEFINER).
 */
data class DeliveryJobSettlement(
    val currency: CurrencyCode,
    val invoiceTotal: Double? = null,
    val invoiceTotalMinor: Long? = null,
    val amountPaid: Double? = null,
    val amountPaidMinor: Long? = null,
    /** Explicit open balance / COD collect when API provides it. */
    val amountDue: Double? = null,
    val amountDueMinor: Long? = null,
)

/**
 * One DN / invoice sell line for receipt copy (driver-scoped DEFINER).
 * Prefer `*_minor` when present; never invent amounts.
 */
data class DeliveryJobLineItem(
    val lineId: String,
    val qty: Double,
    val oemPartNumber: String?,
    val description: String?,
    val currency: CurrencyCode = CurrencyCode.USD,
    val unitPrice: Double? = null,
    val lineTotal: Double? = null,
    val unitPriceMinor: Long? = null,
    val lineTotalMinor: Long? = null,
    val isCoreCharge: Boolean = false,
)

data class DeliveryJobSummary(
    val id: String,
    val deliveryNoteId: String,
    val documentNumber: String?,
    val status: String,
    val dropoffLat: Double?,
    val dropoffLng: Double?,
    val etaAt: String?,
    val etaSeconds: Int?,
    val notes: String?,
    val routeSequence: Int?,
    val reattemptOf: String?,
    val failureReasonCode: String?,
    val podPhotoPath: String?,
    val podSignaturePath: String?,
    val assigneeUserId: String?,
    /** H4 dual-read COD/settlement snapshot when API provides money fields. */
    val settlement: DeliveryJobSettlement? = null,
    /**
     * Human-readable dropoff when the API provides it (Fake seeds; Live may be null
     * until a driver-scoped address RPC exists — UI falls back to lat/lng + notes).
     */
    val dropoffAddressText: String? = null,
    /** DN/invoice lines for receipt banner (Fake seeds; Live via get_delivery_job_lines). */
    val lineItems: List<DeliveryJobLineItem> = emptyList(),
)

data class GeofenceSuggestion(
    val distanceM: Double?,
    val suggestArrive: Boolean,
    val suggestComplete: Boolean,
)

data class OptimizedStop(
    val deliveryJobId: String,
    val routeSequence: Int,
    val distanceM: Double?,
)

data class DriverPresenceSnapshot(
    val userId: String,
    val status: String,
    val capacity: Int,
    val lastLat: Double?,
    val lastLng: Double?,
)

// --- Cash and card on delivery (`get_delivery_job_payment_context`, `collect_delivery_cash`,
// `*_delivery_card_terminal_*`). The invoice balance is always the server's.

data class DeliveryPaymentContext(
    val deliveryJobId: String,
    val invoiceId: String,
    val documentNumber: String?,
    val warehouseId: String?,
    val currency: String,
    val invoiceTotal: Double,
    val amountPaid: Double,
    val amountDue: Double,
    /** prepay | cash_on_delivery | card_on_delivery | cash_or_card_on_delivery */
    val method: String,
    val mayCollectCash: Boolean,
    val mayCollectCard: Boolean,
)

/** Leaving an unpaid balance on account (`delivery_balance_approvals`). */
data class DeliveryBalanceApproval(
    val id: String,
    /** pending | auto_approved | approved | refused | cancelled */
    val status: String,
    /** credit_limit (approved by the customer's trade account) | back_office (dispatch decides) */
    val basis: String,
    val amount: Double,
    val currency: String,
    val reason: String,
    val decidedByName: String?,
    val decisionNote: String?,
) {
    val approved: Boolean get() = status == "auto_approved" || status == "approved"
}

/** Cash collected on delivery that the driver has not handed in yet, in one currency. */
data class DriverCashHolding(
    val currency: String,
    val amount: Double,
    val count: Int,
    val oldestAt: String?,
    val collections: List<DriverCashCollection>,
)

data class DriverCashCollection(val id: String, val amount: Double, val collectedAt: String?, val jobNumber: String?, val invoiceNumber: String?)

data class DriverCashHandin(
    val id: String,
    val documentNumber: String?,
    /** submitted | received | variance_pending | approved */
    val status: String,
    val currency: String,
    val expectedAmount: Double,
    val declaredAmount: Double,
    val receivedAmount: Double?,
    val variance: Double?,
    val collectionCount: Int,
    val submittedAt: String?,
    val receivedByName: String?,
    val reasonCode: String?,
)

data class DriverCash(val holding: List<DriverCashHolding>, val handins: List<DriverCashHandin>) {
    /** A hand-in still waiting for someone to count it, per currency. */
    fun waiting(currency: String): DriverCashHandin? = handins.firstOrNull { it.currency == currency && it.status == "submitted" }
}

data class DeliveryCashReceipt(val collectionId: String, val amount: Double, val currency: String, val balanceDue: Double?)

data class DeliveryCardTerminal(
    val id: String,
    val label: String,
    val acquirer: String?,
    val adapterKey: String?,
    val adapterConfig: Map<String, String?>,
    val deviceId: String?,
)

/** A card-machine attempt as the server sees it (`pos_card_terminal_attempt_payload`). */
data class DeliveryCardAttempt(
    val attemptId: String,
    /** initiated, approved, declined, cancelled, unknown, failed, settled — or recovery_required. */
    val status: String,
    val amount: Double,
    val currency: String,
    val externalRef: String?,
    val terminalLabel: String?,
    val adapterConfig: Map<String, String?>,
    val transactionId: String?,
    val cardLast4: String?,
    val responseMessage: String?,
    val finalizationError: String?,
)
