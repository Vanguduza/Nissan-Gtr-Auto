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

/** Mirrors `public.fulfillment_mode` — UX: pickup = immediate, delivery = dispatch. */
enum class FulfillmentMode(val rpcValue: String, val label: String) {
    IMMEDIATE("immediate", "Pickup"),
    DISPATCH("dispatch", "Delivery"),
}

/** One split-bill tender line for [RpcNames.CHECKOUT_POS_CART_WITH_TENDERS]. */
data class PosTenderLine(
    val tender: String,
    val amount: Double,
    val currency: String? = null,
    val exchangeRate: Double? = null,
)

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
 * Post-auth landing (tablet kiosk plan §3).
 * [Deny] = missing / empty / unknown roles (fail closed).
 */
enum class ManagementHomeLanding {
    Deny,
    Pos,
    Hub,
}

/**
 * Role landing: sales → POS; warehouse / finance / HR / admin / dispatcher → hub.
 * Dashboard roles win over sales when multi-role. Unknown strings do not grant access.
 */
object ManagementHomeRoles {
    val KNOWN: Set<String> = setOf(
        "admin",
        "sales",
        "warehouse",
        "finance",
        "hr",
        "dispatcher",
    )

    private val DASHBOARD_ROLES: Set<String> =
        setOf("admin", "warehouse", "finance", "hr", "dispatcher")

    fun normalize(roles: Collection<String>): List<String> =
        roles
            .map { it.trim().lowercase() }
            .filter { it in KNOWN }
            .distinct()

    /**
     * Sales-only → POS. Admin | warehouse | finance | hr | dispatcher → hub
     * (hub wins when combined with sales).
     */
    fun prefersPosHome(roles: Collection<String>): Boolean {
        val n = normalize(roles)
        if (n.any { it in DASHBOARD_ROLES }) return false
        return n.any { it == "sales" }
    }

    /** Fail closed when auth has no recognized staff role. */
    fun hasStaffAccess(roles: Collection<String>): Boolean =
        normalize(roles).isNotEmpty()

    /** Alias for [hasStaffAccess] — Fake-friendly routing helper. */
    fun hasActiveStaffRole(roles: Collection<String>): Boolean =
        hasStaffAccess(roles)

    fun resolveLanding(roles: Collection<String>): ManagementHomeLanding {
        val n = normalize(roles)
        if (n.isEmpty()) return ManagementHomeLanding.Deny
        return if (prefersPosHome(n)) ManagementHomeLanding.Pos else ManagementHomeLanding.Hub
    }

    /**
     * Prefer DB [defaultLanding] (`pos`|`hub`) when set; otherwise [resolveLanding] from roles.
     * Empty/unknown roles still Deny regardless of DB landing.
     */
    fun resolveLanding(
        roles: Collection<String>,
        defaultLanding: String?,
    ): ManagementHomeLanding {
        val n = normalize(roles)
        if (n.isEmpty()) return ManagementHomeLanding.Deny
        return when (defaultLanding?.trim()?.lowercase()) {
            "pos" -> ManagementHomeLanding.Pos
            "hub" -> ManagementHomeLanding.Hub
            else -> resolveLanding(n)
        }
    }

    /**
     * Thin module gate from organogram module_access.
     * Admins bypass; empty access → show all (staff_roles already applied by caller).
     */
    fun moduleAllowed(
        moduleKey: String,
        roles: Collection<String>,
        moduleAccess: Collection<String>,
    ): Boolean {
        if (normalize(roles).any { it == "admin" }) return true
        if (moduleAccess.isEmpty()) return true
        return moduleAccess.any { it.equals(moduleKey, ignoreCase = true) }
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
    /** Stock master description; populated by POS description/OEM discovery where available. */
    val description: String? = null,
    val pncCode: String? = null,
    val categoryName: String? = null,
    val subcategoryName: String? = null,
    val chassisCode: String? = null,
    val engineCode: String? = null,
    /** Saleable on-hand across warehouses; null when OEM unknown / lookup skipped. */
    val saleableQty: Double? = null,
)

data class CatalogSearchResult(
    val mode: CatalogSearchMode,
    val query: String,
    val parts: List<CatalogPartHit>,
)

/** Immutable Nissan fitment context attached to a POS cart and snapshotted to invoice. */
data class PosSaleVehicleSelection(
    val modelSlug: String,
    val modelName: String,
    val generation: String,
    val chassisCode: String,
    val engineCode: String,
) {
    val displayLabel: String
        get() = "$modelName · $generation · $engineCode"
}

/** Ranked from real posted POS invoice lines; never a merchandising placeholder. */
data class PopularPosSpare(
    val stockItemId: String,
    val oemPartNumber: String,
    val description: String? = null,
    val unitsSold: Double,
    val saleableQty: Double,
    val unitPrice: Double? = null,
    val currency: CurrencyCode? = null,
)

/** Posted invoice read model used by POS Orders and Returns. */
data class PosInvoiceSummary(
    val id: String,
    val documentNumber: String? = null,
    val customerId: String? = null,
    val customerName: String? = null,
    val total: Double,
    val currency: CurrencyCode,
    val postedAt: String? = null,
    val vehicleModelName: String? = null,
    val vehicleGeneration: String? = null,
    val vehicleChassisCode: String? = null,
    val vehicleEngineCode: String? = null,
) {
    val vehicleLabel: String?
        get() = vehicleModelName?.let { model ->
            listOfNotNull(model, vehicleGeneration ?: vehicleChassisCode, vehicleEngineCode)
                .joinToString(" · ")
        }
}

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

/** Row from [RpcNames.LIST_POS_QUOTATIONS]. */
data class PosQuotationSummary(
    val id: String,
    val documentNumber: String?,
    val customerId: String?,
    val warehouseId: String,
    val currency: CurrencyCode,
    val status: String,
    val validUntil: String?,
    val sentChannel: String?,
    val createdAt: String?,
    val lineCount: Long = 0,
    val total: Double = 0.0,
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

/** Line from [RpcNames.PULL_POS_OFFLINE_SNAPSHOT] items[]. */
data class OfflineCatalogItem(
    val stockItemId: String,
    val oemPartNumber: String,
    val description: String? = null,
    val uomId: String,
    val unitPrice: Double,
    val coreCharge: Double = 0.0,
    val saleableQty: Double = 0.0,
    val currency: CurrencyCode = CurrencyCode.USD,
)

/** Result of [RpcNames.PULL_POS_OFFLINE_SNAPSHOT]. */
data class OfflinePosSnapshot(
    val warehouseId: String,
    val pulledAt: String,
    val priceListId: String? = null,
    val currency: CurrencyCode = CurrencyCode.USD,
    val items: List<OfflineCatalogItem> = emptyList(),
)

/**
 * Payload for [RpcNames.REPLAY_OFFLINE_POS_SALE].
 * Cash tender only; walk-in (no named credit customer).
 */
data class OfflineSaleReplayPayload(
    val warehouseId: String,
    val currency: CurrencyCode,
    val exchangeRate: Double = 1.0,
    val deviceId: String? = null,
    val lines: List<OfflineSaleLine>,
    val tenders: List<PosTenderLine>,
    val receiptEmail: String? = null,
    val receiptWhatsappE164: String? = null,
    val receiptPhoneE164: String? = null,
    val soldAt: String? = null,
    val vehicle: PosSaleVehicleSelection? = null,
)

data class OfflineSaleLine(
    val stockItemId: String,
    val uomId: String,
    val qty: Double,
    val expectedUnitPrice: Double,
)

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

/** HR onboarding + sensitive banking/health — admin|hr only (mirrors web RLS). */
object HrOnboardingStaffRoles {
    fun allows(roles: Collection<String>): Boolean =
        roles.any { it == "admin" || it == "hr" }
}

/** Mirrors `public.hr_onboarding_stage`. */
enum class HrOnboardingStage(val rpcValue: String) {
    PERSONAL("personal"),
    BANKING_HEALTH("banking_health"),
    DOCUMENTS("documents"),
    ROLE_CONTRACT("role_contract"),
    CREDENTIALS("credentials"),
    ;

    companion object {
        fun fromRpc(raw: String?): HrOnboardingStage =
            entries.firstOrNull { it.rpcValue.equals(raw, ignoreCase = true) }
                ?: PERSONAL
    }
}

data class HrGradeOption(
    val id: String,
    val code: String,
    val title: String,
    val sortOrder: Int = 100,
)

data class HrRoleOption(
    val id: String,
    val title: String,
    val department: String? = null,
    val gradeId: String? = null,
)

data class HrOnboardingDraft(
    val id: String,
    val employeeId: String? = null,
    val stage: HrOnboardingStage,
    val payload: Map<String, String?>,
    val bankingJson: Map<String, String?>? = null,
    val healthJson: Map<String, String?>? = null,
    val completedAt: String? = null,
    val updatedAt: String,
)

data class HrOnboardingCompleteResult(
    val draftId: String?,
    val employeeId: String?,
    val employeeCode: String?,
    val email: String?,
    val phoneE164: String?,
    val userId: String?,
    val mustChangePassword: Boolean,
    /** Channel delivery status from Edge when auth was created; empty if skipped. */
    val auth: HrOnboardingAuthResult? = null,
    val authError: String? = null,
    val message: String? = null,
)

data class HrOnboardingAuthChannel(
    val channel: String,
    val status: String,
    val error: String? = null,
)

data class HrOnboardingAuthResult(
    val employeeId: String,
    val userId: String,
    val created: Boolean,
    val mustChangePassword: Boolean,
    val channels: List<HrOnboardingAuthChannel> = emptyList(),
)
