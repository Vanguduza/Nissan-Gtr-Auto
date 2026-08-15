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
