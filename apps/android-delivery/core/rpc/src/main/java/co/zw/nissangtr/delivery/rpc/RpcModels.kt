package co.zw.nissangtr.delivery.rpc

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
