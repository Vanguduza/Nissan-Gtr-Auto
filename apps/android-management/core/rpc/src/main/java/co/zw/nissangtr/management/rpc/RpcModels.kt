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

/** Named-customer hit from PostgREST `customers` (POS / credit / consignment). */
data class CustomerOption(
    val id: String,
    val displayName: String,
)

data class SupplierRef(
    val id: String,
    val code: String,
    val name: String,
)

/** Staff roles that may set B2B credit (`set_customer_credit`). */
object CreditStaffRoles {
    val ALL: Set<String> = setOf("admin", "sales", "finance")

    fun allows(roles: Collection<String>): Boolean =
        roles.any { it in ALL }
}

/** Staff roles for company fleet CRUD (`fleet_vehicles` RPCs). */
object FleetStaffRoles {
    val ALL: Set<String> = setOf("admin", "warehouse", "dispatcher")

    fun allows(roles: Collection<String>): Boolean =
        roles.any { it in ALL }
}

/** Mirrors `public.fleet_vehicle_status`. */
enum class FleetVehicleStatus(val rpcValue: String) {
    ACTIVE("active"),
    IN_SERVICE("in_service"),
    RETIRED("retired"),
    ;

    companion object {
        fun fromRpc(value: String): FleetVehicleStatus =
            entries.find { it.rpcValue == value } ?: ACTIVE
    }
}

/** Row from [RpcNames.LIST_FLEET_VEHICLES] / `fleet_vehicles`. */
data class FleetVehicleSummary(
    val id: String,
    val plate: String,
    val label: String?,
    val status: FleetVehicleStatus,
    val assignedDriverUserId: String?,
    val notes: String?,
)

/** Mirrors `public.consignment_kind`. */
enum class ConsignmentKind(val rpcValue: String) {
    SUPPLIER_OWNED("supplier_owned"),
    CUSTOMER_HELD("customer_held"),
}

/** Mirrors `public.consignment_entry_purpose`. */
enum class ConsignmentPurpose(val rpcValue: String) {
    RECEIVE("receive"),
    RETURN_TO_SUPPLIER("return_to_supplier"),
    TAKE_OWNERSHIP("take_ownership"),
    PLACE_WITH_CUSTOMER("place_with_customer"),
    RETURN_FROM_CUSTOMER("return_from_customer"),
    RECOGNIZE_SALE("recognize_sale"),
}

data class WarehouseBinSummary(
    val id: String,
    val warehouseId: String,
    val code: String,
    val name: String,
    val pickPathSeq: Int,
    val aisle: String? = null,
    val rack: String? = null,
    val shelf: String? = null,
    val isActive: Boolean = true,
)

data class PickPathHint(
    val stockItemId: String,
    val oemPartNumber: String?,
    val quantity: Double,
    val binId: String?,
    val binCode: String?,
    val binName: String?,
    val pickPathSeq: Int?,
    val aisle: String? = null,
    val rack: String? = null,
    val shelf: String? = null,
)

data class BlanketLineInput(
    val stockItemId: String,
    val uomId: String,
    val qty: Double,
    val unitPrice: Double,
    val currency: CurrencyCode? = null,
)

data class BlanketReleaseLineInput(
    val blanketLineId: String,
    val qty: Double,
)

data class BlanketLineSummary(
    val id: String,
    val lineNo: Int,
    val stockItemId: String,
    val oemPartNumber: String?,
    val qtyOrdered: Double,
    val qtyReleased: Double,
    val unitPrice: Double,
    val currency: CurrencyCode,
) {
    val remainingQty: Double get() = (qtyOrdered - qtyReleased).coerceAtLeast(0.0)
}

data class BlanketSummary(
    val id: String,
    val documentNumber: String,
    val status: String,
    val supplierId: String,
    val supplierName: String?,
    val warehouseId: String,
    val warehouseCode: String?,
    val currency: CurrencyCode,
    val blanketMaxValue: Double,
    val blanketValueReleased: Double,
    val expectedDate: String?,
    val lines: List<BlanketLineSummary>,
) {
    val remainingValue: Double
        get() = (blanketMaxValue - blanketValueReleased).coerceAtLeast(0.0)
}

enum class BlanketAlertKind { EXPIRY, REMAINING_VALUE, REMAINING_QTY }

enum class BlanketAlertSeverity { WARN, CRITICAL }

data class BlanketAlert(
    val kind: BlanketAlertKind,
    val severity: BlanketAlertSeverity,
    val message: String,
)

/**
 * Expiry / remaining alerts — mirrors web `blanketAlerts` thresholds
 * (14d expiry warn, 15% remaining value, qty floor 5).
 */
fun blanketAlerts(summary: BlanketSummary): List<BlanketAlert> {
    val alerts = mutableListOf<BlanketAlert>()
    val expected = summary.expectedDate
    if (!expected.isNullOrBlank()) {
        val due = runCatching { java.time.LocalDate.parse(expected.take(10)) }.getOrNull()
        if (due != null) {
            val days = java.time.temporal.ChronoUnit.DAYS.between(java.time.LocalDate.now(), due)
            when {
                days < 0 -> alerts += BlanketAlert(
                    BlanketAlertKind.EXPIRY,
                    BlanketAlertSeverity.CRITICAL,
                    "Expected date passed (${expected.take(10)})",
                )
                days <= 14 -> alerts += BlanketAlert(
                    BlanketAlertKind.EXPIRY,
                    BlanketAlertSeverity.WARN,
                    "Expires in $days day(s) (${expected.take(10)})",
                )
            }
        }
    }
    if (summary.blanketMaxValue > 0 &&
        summary.remainingValue / summary.blanketMaxValue <= 0.15
    ) {
        val pct = ((summary.remainingValue / summary.blanketMaxValue) * 100).toInt()
        alerts += BlanketAlert(
            BlanketAlertKind.REMAINING_VALUE,
            if (summary.remainingValue <= 0) BlanketAlertSeverity.CRITICAL else BlanketAlertSeverity.WARN,
            "Remaining value ${"%.2f".format(summary.remainingValue)} ${summary.currency.rpcValue} ($pct% of max)",
        )
    }
    val remQty = summary.lines.sumOf { it.remainingQty }
    when {
        remQty > 0 && remQty <= 5 -> alerts += BlanketAlert(
            BlanketAlertKind.REMAINING_QTY,
            BlanketAlertSeverity.WARN,
            "Low remaining qty · $remQty left across lines",
        )
        remQty <= 0 && summary.lines.isNotEmpty() -> alerts += BlanketAlert(
            BlanketAlertKind.REMAINING_QTY,
            BlanketAlertSeverity.CRITICAL,
            "No remaining qty on blanket lines",
        )
    }
    return alerts
}

data class ConsignmentEntrySummary(
    val id: String,
    val documentNumber: String,
    val status: String,
    val kind: String,
    val purpose: String,
    val warehouseId: String,
    val supplierId: String? = null,
    val customerId: String? = null,
    val currency: CurrencyCode = CurrencyCode.USD,
)

data class CustomerCreditSnapshot(
    val customerId: String,
    val creditLimit: Double,
    val creditHold: Boolean,
    val openBalance: Double,
    val currency: CurrencyCode,
)
