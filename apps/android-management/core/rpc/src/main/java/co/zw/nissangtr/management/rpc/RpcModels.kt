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
