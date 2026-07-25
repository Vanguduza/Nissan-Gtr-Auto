package co.zw.nissangtr.management.rpc

/** Mirrors `public.attendance_event_type`. */
enum class AttendanceEventType(val rpcValue: String) {
    CLOCK_IN("clock_in"),
    CLOCK_OUT("clock_out"),
}

/** Mirrors `public.currency_code` — never assume USD silently. */
enum class CurrencyCode(val rpcValue: String) {
    USD("USD"),
    ZIG("ZIG"),
}

/** Mirrors `public.fulfillment_mode`. */
enum class FulfillmentMode(val rpcValue: String) {
    IMMEDIATE("immediate"),
    DISPATCH("dispatch"),
}

/** Mirrors `public.valuation_method`. */
enum class ValuationMethod(val rpcValue: String) {
    FIFO("FIFO"),
    AVG("AVG"),
}

/** Mirrors `public.stock_reconciliation_scope`. */
enum class ReconciliationScope(val rpcValue: String) {
    FULL("full"),
    PARTIAL("partial"),
}

data class DeliveryNoteSummary(
    val id: String,
    val documentNumber: String,
    val salesInvoiceId: String,
    val status: String,
)

data class PickListSummary(
    val id: String,
    val documentNumber: String,
    val salesInvoiceId: String,
    val status: String,
)

data class DnLineInput(
    val salesInvoiceLineId: String,
    val qty: Double,
)

data class ConfirmPickLineInput(
    val pickListLineId: String? = null,
    val salesInvoiceLineId: String? = null,
    val qtyPicked: Double,
)

/** Mirrors `public.delivery_job_status` (RPC cannot revert to pending). */
enum class DeliveryJobStatus(val rpcValue: String) {
    DISPATCHED("dispatched"),
    COMPLETED("completed"),
    FAILED("failed"),
}

/**
 * Result of [RpcNames.UPDATE_DELIVERY_JOB_STATUS] (jsonb).
 * [trackToken] is present only on transition to dispatched — use for share UI;
 * do not remint immediately (revokes SMS token).
 */
data class UpdateDeliveryJobStatusResult(
    val deliveryJobId: String,
    val trackToken: String? = null,
)

/** Row from [RpcNames.SUGGEST_DELIVERY_ASSIGNEES] (nearest + capacity + shift). */
data class DeliveryAssigneeSuggestion(
    val userId: String,
    val status: String,
    val distanceM: Double?,
    val capacity: Int,
    val openJobs: Int,
    val lastLat: Double?,
    val lastLng: Double?,
    val lastSeenAt: String?,
)

/** Row from [RpcNames.OPTIMIZE_DRIVER_STOPS] (nearest-neighbor; writes route_sequence). */
data class OptimizedDriverStop(
    val deliveryJobId: String,
    val routeSequence: Int,
    val distanceM: Double?,
)

/** Staff live last-point + ETA from [RpcNames.GET_DELIVERY_TRACK_POINT]. */
data class DeliveryTrackPoint(
    val deliveryJobId: String,
    val lat: Double,
    val lng: Double,
    val recordedAt: String,
    val etaAt: String?,
    val etaSeconds: Int?,
    val status: String,
)

/** Open row from `panic_events` (staff inbox; ack via PostgREST UPDATE). */
data class PanicEventSummary(
    val id: String,
    val driverUserId: String,
    val deliveryJobId: String?,
    val lat: Double?,
    val lng: Double?,
    val createdAt: String,
    val acknowledgedAt: String?,
    val acknowledgedBy: String?,
)

/** Line for [RpcNames.POST_STOCK_RECEIPT] `p_lines` JSONB. */
data class ReceiptLineInput(
    val stockItemId: String,
    val uomId: String,
    val qty: Double,
    val unitCost: Double,
    val currency: CurrencyCode,
    val valuationMethod: ValuationMethod = ValuationMethod.FIFO,
    val serials: List<String>? = null,
)

/** Line for [RpcNames.CREATE_STOCK_TRANSFER] `p_lines` JSONB. */
data class TransferLineInput(
    val stockItemId: String,
    val uomId: String,
    val qty: Double,
    val valuationMethod: ValuationMethod = ValuationMethod.FIFO,
)

/** Line for [RpcNames.UPSERT_STOCK_RECONCILIATION_LINES] `p_lines` JSONB. */
data class ReconciliationLineInput(
    val stockItemId: String,
    val countedQty: Double,
)

/** Result of [RpcClient.lookupStockItemByOem] after Bridge-First QR parse. */
data class StockItemRef(
    val stockItemId: String,
    val uomId: String,
    val oemPartNumber: String,
)

/** Staff inbox filter — mirrors web `StaffChatFilter`. */
enum class StaffChatFilter {
    OPEN,
    MINE,
    CLOSED,
}

/** Mirrors `public.chat_thread_status`. */
enum class ChatThreadStatus(val rpcValue: String) {
    OPEN("open"),
    ASSIGNED("assigned"),
    CLOSED("closed"),
}

/** Mirrors `public.chat_sender_kind`. */
enum class ChatSenderKind(val rpcValue: String) {
    CUSTOMER("customer"),
    STAFF("staff"),
    SYSTEM("system"),
}

/**
 * Staff roles that may claim / reply / close chat (`_chat_staff_roles`).
 * Nav gate: admin | sales | warehouse.
 */
object ChatStaffRoles {
    val ALL: Set<String> = setOf("admin", "sales", "warehouse")

    fun allows(roles: Collection<String>): Boolean =
        roles.any { it in ALL }
}

/**
 * Role home: sales-only → POS workspace; admin/warehouse keep hub.
 * Admin or warehouse wins over sales when both present.
 */
object ManagementHomeRoles {
    fun prefersPosHome(roles: Collection<String>): Boolean {
        if (roles.any { it == "admin" || it == "warehouse" }) return false
        return roles.any { it == "sales" }
    }
}

/** 4-way catalog search modes — mirrors `search_catalog` p_mode. */
enum class CatalogSearchMode(val rpcValue: String) {
    PART("part"),
    VIN("vin"),
    MODEL("model"),
    PNC("pnc"),
}

/** Part hit from [RpcNames.SEARCH_CATALOG] (type=part or nested fitment). */
data class CatalogPartHit(
    val oemPartNumber: String,
    val pncCode: String? = null,
    val categoryName: String? = null,
    val subcategoryName: String? = null,
    val chassisCode: String? = null,
    val engineCode: String? = null,
)

data class CatalogSearchResult(
    val mode: CatalogSearchMode,
    val query: String,
    val parts: List<CatalogPartHit>,
)

data class WarehouseRef(
    val id: String,
    val code: String,
    val name: String,
)

data class PosCartLineSummary(
    val id: String,
    val stockItemId: String,
    val oemPartNumber: String?,
    val qty: Double,
    val unitPrice: Double,
    val lineTotal: Double,
    val isCoreCharge: Boolean = false,
)

data class PosScanSessionCreated(
    val sessionId: String,
    val pairingCode: String,
    val expiresAt: String,
)

/**
 * Checkout result with receipt-contact bind messaging.
 * [customerId] set ⇒ bound or preselected; null + contacts ⇒ walk-in / no unique match.
 */
data class CheckoutPosResult(
    val invoiceId: String,
    val customerId: String?,
    val receiptEmail: String?,
    val receiptWhatsappE164: String?,
    val hadCustomerBeforeCheckout: Boolean,
) {
    val bindMessage: String
        get() = when {
            hadCustomerBeforeCheckout && !customerId.isNullOrBlank() ->
                "Customer was already on cart"
            !customerId.isNullOrBlank() ->
                "Bound to registered / trade account"
            !receiptEmail.isNullOrBlank() || !receiptWhatsappE164.isNullOrBlank() ->
                "Walk-in — no unique account match (link manually if needed)"
            else ->
                "Walk-in — no receipt contacts"
        }
}

/** Row from `chat_threads` (staff list / detail header). */
data class ChatThreadSummary(
    val id: String,
    val kind: String,
    val status: String,
    val subject: String?,
    val assignedTo: String?,
    val lastMessageAt: String?,
    val createdAt: String,
)

/** Row from `chat_messages`. */
data class ChatMessageSummary(
    val id: String,
    val threadId: String,
    val senderUserId: String,
    val senderKind: String,
    val body: String,
    val createdAt: String,
)
