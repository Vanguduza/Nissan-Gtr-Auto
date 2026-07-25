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
